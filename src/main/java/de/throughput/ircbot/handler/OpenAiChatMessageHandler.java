package de.throughput.ircbot.handler;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.openai.client.OpenAIClient;
import com.openai.models.ChatModel;
import com.openai.models.Reasoning;
import com.openai.models.ReasoningEffort;
import com.openai.models.responses.EasyInputMessage;
import com.openai.models.responses.Response;
import com.openai.models.responses.ResponseCreateParams;
import com.openai.models.responses.ResponseInputItem;
import com.openai.models.responses.WebSearchTool;
import de.throughput.ircbot.api.Command;
import de.throughput.ircbot.api.CommandEvent;
import de.throughput.ircbot.api.CommandHandler;
import de.throughput.ircbot.api.MessageHandler;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.pircbotx.hooks.events.MessageEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.apache.commons.lang3.tuple.Pair;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Reponds to messages directed at the bot, using the OpenAI API.
 */
@Component
public class OpenAiChatMessageHandler implements MessageHandler, CommandHandler {

    private static final Logger LOG = LoggerFactory.getLogger(OpenAiChatMessageHandler.class);

    public static final Command CMD_RESET_CONTEXT = new Command("aireset",
            "aireset - deletes the current context for the channel and reloads the system prompt from the file system."
            , true);

    private static final ChatModel MODEL = ChatModel.of("gpt-5.6-luna");
    private static final int MAX_CONTEXT_MESSAGES = 20;
    private static final int MAX_TOKENS = 500;
    private static final int MAX_IRC_MESSAGE_LENGTH = 420;
    private static final String SHORT_ANSWER_HINT = " (Antwort auf 200 Zeichen begrenzen)";

    private final Map<String, LinkedList<TimedChatMessage>> contextMessagesPerChannel = new ConcurrentHashMap<>();

    private final OpenAIClient openAiClient;
    private final Path systemPromptPath;
    private String systemPrompt;

    public OpenAiChatMessageHandler(
            OpenAIClient openAiClient,
            @Value("${openai.systemPrompt.path}") Path systemPromptPath) {
        this.openAiClient = openAiClient;
        this.systemPromptPath = systemPromptPath;
        readSystemPromptFromFile();
    }

    @Override
    public Set<Command> getCommands() {
        return Set.of(CMD_RESET_CONTEXT);
    }

    @Override
    public boolean onMessage(MessageEvent event) {
        String message = event.getMessage().trim();
        String botNick = event.getBot().getNick();
        if (message.startsWith(botNick + ":") || message.startsWith(botNick + ",")) {
            message = message.substring(event.getBot().getNick().length() + 1).trim();

            generateResponse(event, message);
            return true;
        }
        return false;
    }

    @Override
    public boolean onCommand(CommandEvent command) {
        // handles the aireset command
        var contextMessages = contextMessagesPerChannel.get(command.getEvent().getChannel().getName());
        if (contextMessages != null) {
            synchronized (contextMessages) {
                contextMessages.clear();
            }
        }
        readSystemPromptFromFile();
        command.respond("system prompt reloaded. context reset complete.");
        return true;
    }

    /**
     * Generates a response to the given (trimmed) message using the OpenAI API.
     */
    private void generateResponse(MessageEvent event, String message) {
        var contextMessages = contextMessagesPerChannel.computeIfAbsent(event.getChannel().getName(), k -> new LinkedList<>());
        synchronized (contextMessages) {
            try {
                sendResponse(event, contextMessages, event.getChannel().getName(), message);
            } catch (Exception e) {
                LOG.error(e.getMessage(), e);
                event.respond("Tja. (" + ExceptionUtils.getRootCauseMessage(e) + ")");
            }
        }
    }

    private void sendResponse(MessageEvent event, LinkedList<TimedChatMessage> contextMessages,
                              String channel, String message) {
        ResponseCreateParams request = ResponseCreateParams.builder()
                .model(MODEL)
                .maxOutputTokens(MAX_TOKENS)
                .reasoning(Reasoning.builder().effort(ReasoningEffort.NONE).build())
                .addTool(WebSearchTool.builder().type(WebSearchTool.Type.WEB_SEARCH).build())
                .inputOfResponse(createPromptMessages(contextMessages, channel, message))
                .build();

        Response completion = openAiClient.responses().create(request);
        String response = sanitizeResponse(completion.output().stream()
                .flatMap(item -> item.message().stream())
                .flatMap(output -> output.content().stream())
                .flatMap(content -> content.outputText().stream())
                .map(output -> output.text())
                .findFirst()
                .orElse(""));
        if (response.isEmpty()) {
            event.respond("Tja. (no response)");
            return;
        }
        contextMessages.add(new TimedChatMessage(
                createMessage(EasyInputMessage.Role.ASSISTANT, response)));
        event.respond(response);
    }

    /**
     * Converts Markdown responses to IRC-safe plain text and limits their length.
     */
    static String sanitizeResponse(String content) {
        String plainText = content
                .replaceAll("!\\[([^]]*)]\\([^)]*\\)", "$1")
                .replaceAll("\\[([^]]+)]\\(([^)]*)\\)", "$1 ($2)")
                .replaceAll("`([^`]*)`", "$1")
                .replace("**", "")
                .replace("__", "")
                .replace("~~", "")
                .replaceAll("(?m)^\\s{0,3}#{1,6}\\s+", "")
                .replaceAll("(?m)^\\s*>\\s?", "")
                .replaceAll("(?m)^\\s*(?:[-*+]\\s+|\\d+[.)]\\s+)", "");
        String trim = plainText.replaceAll("\\s+", " ").trim();
        return trim.length() > MAX_IRC_MESSAGE_LENGTH ? trim.substring(0, MAX_IRC_MESSAGE_LENGTH) : trim;
    }

    /**
     * Creates the list of prompt messages for the OpenAI API call.
     */
    private List<ResponseInputItem> createPromptMessages(LinkedList<TimedChatMessage> contextMessages,
                                                         String channel, String message) {
        String augmentedMessage = message + SHORT_ANSWER_HINT;

        pruneOldMessages(contextMessages);
        contextMessages.add(new TimedChatMessage(createMessage(EasyInputMessage.Role.USER, augmentedMessage)));

        List<ResponseInputItem> promptMessages = new ArrayList<>();
        promptMessages.add(createMessage(EasyInputMessage.Role.SYSTEM, systemPrompt));
        promptMessages.add(createMessage(EasyInputMessage.Role.SYSTEM, getDatePrompt()));
        for (TimedChatMessage timedMessage : contextMessages) {
            promptMessages.add(timedMessage.message());
        }
        return promptMessages;
    }

    private static ResponseInputItem createMessage(EasyInputMessage.Role role, String content) {
        return ResponseInputItem.ofEasyInputMessage(EasyInputMessage.builder()
                .role(role)
                .content(content)
                .build());
    }

    /**
     * Generates a system prompt containing the current date and time.
     */
    private String getDatePrompt() {
        TimeZone timeZone = TimeZone.getTimeZone("Europe/Berlin");
        SimpleDateFormat dateFormat = new SimpleDateFormat("EEEE, 'der' dd. MMMM yyyy", Locale.GERMAN);
        dateFormat.setTimeZone(timeZone);
        SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm", Locale.GERMAN);
        timeFormat.setTimeZone(timeZone);

        Date now = new Date();
        return "Heute ist " + dateFormat.format(now) + ", und es ist " + timeFormat.format(now) + " Uhr in Deutschland.";
    }

    /**
     * Removes old messages from the context.
     */
    private void pruneOldMessages(LinkedList<TimedChatMessage> contextMessages) {
        LocalDateTime twoHoursAgo = LocalDateTime.now().minusHours(2);
        contextMessages.removeIf(message -> message.getTimestamp().isBefore(twoHoursAgo));
        while (contextMessages.size() >= MAX_CONTEXT_MESSAGES) {
            contextMessages.removeFirst();
        }
    }

    /**
     * Reads the system prompt from the file system.
     */
    private void readSystemPromptFromFile() {
        try {
            if (systemPromptPath != null) {
                systemPrompt = Files.readString(systemPromptPath);
            } else {
                LOG.warn("system prompt path not specified");
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public boolean isOnlyTalkChannels() {
        return true;
    }

    /**
     * Adds a timestamp to chat messages, allowing us to drop old messages from the context.
     */
    private static class TimedChatMessage {

        private final ResponseInputItem message;
        private final LocalDateTime timestamp;

        public TimedChatMessage(ResponseInputItem message) {
            this.message = message;
            this.timestamp = LocalDateTime.now();
        }

        public ResponseInputItem message() {
            return message;
        }

        @JsonIgnore
        public LocalDateTime getTimestamp() {
            return timestamp;
        }
    }
}

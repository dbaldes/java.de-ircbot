package de.throughput.ircbot.handler;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.theokanning.openai.completion.chat.ChatCompletionRequest;
import com.theokanning.openai.completion.chat.ChatCompletionResult;
import com.theokanning.openai.completion.chat.ChatMessage;
import com.theokanning.openai.completion.chat.ChatMessageRole;
import com.theokanning.openai.service.OpenAiService;
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

@Component
public class OpenAiChatMessageHandler implements MessageHandler, CommandHandler {

    private static final Logger LOG = LoggerFactory.getLogger(OpenAiChatMessageHandler.class);

    public static final Command CMD_RESET_CONTEXT = new Command("aireset",
            "aireset - deletes the current context for the channel and reloads the system prompt from the file system.");

    private static final String MODEL_GPT_3_5_TURBO = "gpt-3.5-turbo";
    private static final int MAX_CONTEXT_MESSAGES = 10;
    private static final int MAX_TOKENS = 100;
    private static final int MAX_IRC_MESSAGE_LENGTH = 420;
    private static final String SHORT_ANSWER_HINT = " (Antwort auf 200 Zeichen begrenzen)";

    private final Map<String, LinkedList<TimedChatMessage>> contextMessagesPerChannel = new ConcurrentHashMap<>();

    private final OpenAiService openAiService;
    private final Path systemPromptPath;
    private String systemPrompt;

    public OpenAiChatMessageHandler(OpenAiService openAiService, @Value("${openai.systemPrompt.path}") Path systemPromptPath) {
        this.openAiService = openAiService;
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
                String channel = event.getChannel().getName();
                var request = ChatCompletionRequest.builder()
                        .model(MODEL_GPT_3_5_TURBO)
                        .maxTokens(MAX_TOKENS)
                        .messages(createPromptMessages(contextMessages, channel, event.getUser().getNick(), message))
                        .build();

                ChatCompletionResult completionResult = openAiService.createChatCompletion(request);

                ChatMessage responseMessage = completionResult.getChoices().get(0).getMessage();
                contextMessages.add(new TimedChatMessage(responseMessage));
                event.respond(sanitizeResponse(responseMessage.getContent()));
            } catch (Exception e) {
                LOG.error(e.getMessage(), e);
                event.respond("Tja. (" + ExceptionUtils.getRootCauseMessage(e) + ")");
            }
        }
    }

    /**
     * Sanitizes the response by removing excessive whitespace and limiting the length.
     */
    private static String sanitizeResponse(String content) {
        String trim = content.replaceAll("\\s+", " ").trim();
        return trim.length() > MAX_IRC_MESSAGE_LENGTH ? trim.substring(0, MAX_IRC_MESSAGE_LENGTH) : trim;
    }

    /**
     * Creates the list of prompt messages for the OpenAI API call.
     */
    private List<ChatMessage> createPromptMessages(LinkedList<TimedChatMessage> contextMessages, String channel, String nick, String message) {
        message += SHORT_ANSWER_HINT;

        contextMessages.add(new TimedChatMessage(new ChatMessage(ChatMessageRole.USER.value(), message, nick)));
        pruneOldMessages(contextMessages);

        List<ChatMessage> promptMessages = new ArrayList<>();
        promptMessages.add(new ChatMessage(ChatMessageRole.SYSTEM.value(), systemPrompt));
        promptMessages.add(new ChatMessage(ChatMessageRole.SYSTEM.value(), getDatePrompt()));
        promptMessages.addAll(contextMessages);
        return promptMessages;
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
        while (contextMessages.size() > MAX_CONTEXT_MESSAGES) {
            contextMessages.removeFirst();
        }
    }

    /**
     * Reads the system prompt from the file system.
     */
    private void readSystemPromptFromFile() {
        try {
            systemPrompt = Files.readString(systemPromptPath);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public boolean isOnlyTalkChannels() {
        return true;
    }

    /**
     * Adds a timestamp to ChatMessage, allowing us to drop old messages from the context.
     */
    private static class TimedChatMessage extends ChatMessage {

        private final LocalDateTime timestamp;

        public TimedChatMessage(ChatMessage chatMessage) {
            super(chatMessage.getRole(), chatMessage.getContent(), chatMessage.getName());
            this.timestamp = LocalDateTime.now();
        }

        @JsonIgnore
        public LocalDateTime getTimestamp() {
            return timestamp;
        }
    }
}

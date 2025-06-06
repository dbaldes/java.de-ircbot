package de.throughput.ircbot.handler;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.theokanning.openai.completion.chat.ChatCompletionRequest;
import com.theokanning.openai.completion.chat.ChatCompletionResult;
import com.theokanning.openai.completion.chat.ChatMessage;
import com.theokanning.openai.completion.chat.ChatMessageRole;
import com.theokanning.openai.service.OpenAiService;
import org.apache.commons.lang3.tuple.Pair;
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

/**
 * Reponds to messages directed at the bot, using the OpenAI API.
 */
@Component
public class OpenAiChatMessageHandler implements MessageHandler, CommandHandler {

    private static final Logger LOG = LoggerFactory.getLogger(OpenAiChatMessageHandler.class);

    public static final Command CMD_RESET_CONTEXT = new Command("aireset",
            "aireset - deletes the current context for the channel and reloads the system prompt from the file system."
            , true);

    private static final String MODEL_NAME = "gpt-4o-mini";
    private static final int MAX_CONTEXT_MESSAGES = 20;
    private static final int MAX_TOKENS = 100;
    private static final int MAX_IRC_MESSAGE_LENGTH = 420;
    private static final String SHORT_ANSWER_HINT = " (Antwort auf 200 Zeichen begrenzen)";

    private final Map<String, LinkedList<TimedChatMessage>> contextMessagesPerChannel = new ConcurrentHashMap<>();

    private final OpenAiService openAiService;
    private final Path systemPromptPath;
    private final Map<String, Pair<Command, CommandHandler>> autoCommandHandlers = new ConcurrentHashMap<>();
    private String autoCommandHelp;
    private String systemPrompt;

    public OpenAiChatMessageHandler(
            OpenAiService openAiService,
            @Value("${openai.systemPrompt.path}") Path systemPromptPath,
            List<CommandHandler> commandHandlers) {
        this.openAiService = openAiService;
        this.systemPromptPath = systemPromptPath;

        StringBuilder helpBuilder = new StringBuilder();
        commandHandlers.forEach(handler -> handler.getCommands().forEach(cmd -> {
            if (Set.of("stock", "crypto", "aiimage", "weather").contains(cmd.getCommand())) {
                autoCommandHandlers.put(cmd.getCommand(), Pair.of(cmd, handler));
                helpBuilder.append('!').append(cmd.getUsage()).append('\n');
            }
        }));
        autoCommandHelp = helpBuilder.toString().trim();

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

                String commandLine = detectAutoCommand(contextMessages, event.getUser().getNick(), message);
                if (commandLine != null) {
                    executeCommand(event, commandLine);
                    contextMessages.add(new TimedChatMessage(new ChatMessage(ChatMessageRole.SYSTEM.value(),
                            "Executed command: " + commandLine)));
                }

                var request = ChatCompletionRequest.builder()
                        .model(MODEL_NAME)
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
     * Uses the AI service to determine if the message should trigger a bot command.
     * Returns the command line if a command should be executed or {@code null} otherwise.
     */
    private String detectAutoCommand(LinkedList<TimedChatMessage> contextMessages, String nick, String message) {
        LinkedList<TimedChatMessage> copy = new LinkedList<>(contextMessages);
        copy.add(new TimedChatMessage(new ChatMessage(ChatMessageRole.USER.value(), message, nick)));
        pruneOldMessages(copy);

        List<ChatMessage> promptMessages = new ArrayList<>();
        promptMessages.add(new ChatMessage(ChatMessageRole.SYSTEM.value(), systemPrompt));
        promptMessages.add(new ChatMessage(ChatMessageRole.SYSTEM.value(), getDatePrompt()));
        promptMessages.addAll(copy);
        promptMessages.add(new ChatMessage(ChatMessageRole.SYSTEM.value(),
                "Available commands:\n" + autoCommandHelp +
                        "\nIf the user's last message should trigger one of these commands, respond with the command line starting with '!'. Otherwise respond with NONE."));

        var request = ChatCompletionRequest.builder()
                .model(MODEL_NAME)
                .maxTokens(20)
                .messages(promptMessages)
                .build();

        ChatCompletionResult result = openAiService.createChatCompletion(request);
        String response = result.getChoices().get(0).getMessage().getContent()
                .replace("`", "")
                .replace("\n", "")
                .replace("\r", "")
                .trim();

        if (response.equalsIgnoreCase("none")) {
            return null;
        }

        if (!response.startsWith("!")) {
            response = "!" + response;
        }
        return response;
    }

    /**
     * Executes the given command line using the registered command handlers.
     */
    private void executeCommand(MessageEvent event, String commandLine) {
        String[] parts = commandLine.substring(1).split("\\s+", 2);
        String cmdName = parts[0];
        String argLine = parts.length > 1 ? parts[1] : null;

        Pair<Command, CommandHandler> handlerPair = autoCommandHandlers.get(cmdName);
        if (handlerPair == null) {
            return;
        }

        CommandEvent cmdEvent = new CommandEvent(event, handlerPair.getLeft(), "!", java.util.Optional.ofNullable(argLine));
        handlerPair.getRight().onCommand(cmdEvent);
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

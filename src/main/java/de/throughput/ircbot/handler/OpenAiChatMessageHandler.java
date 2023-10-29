package de.throughput.ircbot.handler;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.theokanning.openai.completion.chat.ChatCompletionRequest;
import com.theokanning.openai.completion.chat.ChatCompletionResult;
import com.theokanning.openai.completion.chat.ChatMessage;
import com.theokanning.openai.completion.chat.ChatMessageRole;
import com.theokanning.openai.service.OpenAiService;
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
import java.util.TimeZone;

@Component
public class OpenAiChatMessageHandler implements MessageHandler {

    private static final Logger LOG = LoggerFactory.getLogger(OpenAiChatMessageHandler.class);

    private static final String MODEL_GPT_3_5_TURBO = "gpt-3.5-turbo";
    private static final int MAX_CONTEXT_MESSAGES = 10;
    private static final int MAX_TOKENS = 100;
    private static final int MAX_IRC_MESSAGE_LENGTH = 420;
    public static final String SHORT_ANSWER_HINT = " (Antwort auf 200 Zeichen begrenzen)";

    private final LinkedList<TimedChatMessage> contextMessages = new LinkedList<>();

    private final OpenAiService openAiService;
    private final Path systemPromptPath;

    public OpenAiChatMessageHandler(OpenAiService openAiService, @Value("${openai.systemPrompt.path}") Path systemPromptPath) {
        this.openAiService = openAiService;
        this.systemPromptPath = systemPromptPath;
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

    /**
     * Generates a response to the given (trimmed) message using the OpenAI API.
     */
    private void generateResponse(MessageEvent event, String message) {
        synchronized (contextMessages) {
            try {
                var request = ChatCompletionRequest.builder()
                        .model(MODEL_GPT_3_5_TURBO)
                        .maxTokens(MAX_TOKENS)
                        .messages(createPromptMessages(event.getUser().getNick(), message))
                        .build();

                ChatCompletionResult completionResult = openAiService.createChatCompletion(request);

                ChatMessage responseMessage = completionResult.getChoices().get(0).getMessage();
                addContextMessage(responseMessage);
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
    private List<ChatMessage> createPromptMessages(String nick, String message) {
        try {
            String systemPrompt = Files.readString(systemPromptPath);

            message += SHORT_ANSWER_HINT;

            addContextMessage(new ChatMessage(ChatMessageRole.USER.value(), message, nick));

            List<ChatMessage> promptMessages = new ArrayList<>();
            promptMessages.add(new ChatMessage(ChatMessageRole.SYSTEM.value(), systemPrompt));
            promptMessages.add(new ChatMessage(ChatMessageRole.SYSTEM.value(), getDatePrompt()));
            promptMessages.addAll(contextMessages);
            return promptMessages;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
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
     * Adds a message to the context.
     */
    private void addContextMessage(ChatMessage chatMessage) {
        contextMessages.add(new TimedChatMessage(chatMessage));
        pruneOldMessages();
    }

    /**
     * Removes old messages from the context.
     */
    private void pruneOldMessages() {
        LocalDateTime twoHoursAgo = LocalDateTime.now().minusHours(2);

        contextMessages.removeIf(message -> message.getTimestamp().isBefore(twoHoursAgo));

        while (contextMessages.size() > MAX_CONTEXT_MESSAGES) {
            contextMessages.removeFirst();
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

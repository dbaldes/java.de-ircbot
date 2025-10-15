package de.throughput.ircbot.handler;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.openai.client.OpenAIClient;
import com.openai.models.ChatModel;
import com.openai.models.chat.completions.ChatCompletion;
import com.openai.models.chat.completions.ChatCompletionCreateParams;
import com.openai.models.chat.completions.ChatCompletionMessage;
import com.openai.models.chat.completions.ChatCompletionMessageParam;
import com.openai.models.chat.completions.ChatCompletionSystemMessageParam;
import com.openai.models.chat.completions.ChatCompletionUserMessageParam;
import de.throughput.ircbot.api.Command;
import de.throughput.ircbot.api.CommandEvent;
import de.throughput.ircbot.api.CommandHandler;
import de.throughput.ircbot.api.MessageHandler;
import org.apache.commons.codec.binary.Hex;
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
import java.security.MessageDigest;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
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

    private static final ChatModel MODEL = ChatModel.GPT_4O_MINI;
    private static final int MAX_CONTEXT_MESSAGES = 20;
    private static final int MAX_TOKENS = 100;
    private static final int MAX_IRC_MESSAGE_LENGTH = 420;
    private static final String SHORT_ANSWER_HINT = " (Antwort auf 200 Zeichen begrenzen)";

    private final Map<String, LinkedList<TimedChatMessage>> contextMessagesPerChannel = new ConcurrentHashMap<>();

    private final OpenAIClient openAiClient;
    private final Path systemPromptPath;
    private String systemPrompt;
    private Random random;
    private byte[] nickObfuscationSalt;

    public OpenAiChatMessageHandler(
            OpenAIClient openAiClient,
            @Value("${openai.systemPrompt.path}") Path systemPromptPath) {
        this.openAiClient = openAiClient;
        this.systemPromptPath = systemPromptPath;
        readSystemPromptFromFile();
        random = new Random(System.currentTimeMillis());
        nickObfuscationSalt = new byte[8];
        updateNickObfuscationSalt();
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
                sendChatCompletion(event, contextMessages, event.getChannel().getName(), event.getUser().getNick(), message);
            } catch (Exception e) {
                LOG.error(e.getMessage(), e);
                event.respond("Tja. (" + ExceptionUtils.getRootCauseMessage(e) + ")");
            }
        }
    }

    private void sendChatCompletion(MessageEvent event, LinkedList<TimedChatMessage> contextMessages,
                                    String channel, String nick, String message) {
        ChatCompletionCreateParams request = ChatCompletionCreateParams.builder()
                .model(MODEL)
                .maxCompletionTokens(MAX_TOKENS)
                .messages(createPromptMessages(contextMessages, channel, nick, message))
                .build();

        ChatCompletion completion = openAiClient.chat().completions().create(request);

        var responseChoice = completion.choices().stream().findFirst();
        if (responseChoice.isEmpty()) {
            event.respond("Tja. (no response)");
            return;
        }

        ChatCompletionMessage responseMessage = responseChoice.get().message();
        contextMessages.add(new TimedChatMessage(
                ChatCompletionMessageParam.ofAssistant(responseMessage.toParam())));
        event.respond(sanitizeResponse(responseMessage.content().orElse("")));
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
    private List<ChatCompletionMessageParam> createPromptMessages(LinkedList<TimedChatMessage> contextMessages,
                                                                  String channel, String nick, String message) {
        String augmentedMessage = message + SHORT_ANSWER_HINT;

        pruneOldMessages(contextMessages);
        contextMessages.add(new TimedChatMessage(createUserMessage(augmentedMessage, nick)));

        List<ChatCompletionMessageParam> promptMessages = new ArrayList<>();
        promptMessages.add(ChatCompletionMessageParam.ofSystem(
                ChatCompletionSystemMessageParam.builder().content(systemPrompt).build()));
        promptMessages.add(ChatCompletionMessageParam.ofSystem(
                ChatCompletionSystemMessageParam.builder().content(getDatePrompt()).build()));
        for (TimedChatMessage timedMessage : contextMessages) {
            promptMessages.add(timedMessage.message());
        }
        return promptMessages;
    }

    private ChatCompletionMessageParam createUserMessage(String message, String nick) {
        return ChatCompletionMessageParam.ofUser(
                ChatCompletionUserMessageParam.builder()
                        .content(message)
                        .name(obfuscateNick(nick))
                        .build());
    }

    private String obfuscateNick(String nick) {
        try {
            MessageDigest instance = MessageDigest.getInstance("SHA-256");
            instance.update(nickObfuscationSalt);
            instance.update(nick.getBytes());

            String hexString = Hex.encodeHexString(instance.digest());
            return "user-" + hexString.substring(0, 8);
        } catch (java.security.NoSuchAlgorithmException e) {
            return nick; // Fallback to original nick if hashing fails
        }
    }

    private void updateNickObfuscationSalt() {
        random.nextBytes(nickObfuscationSalt);
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
        if (contextMessages.isEmpty()) {
            // Reset salt if context is empty
            updateNickObfuscationSalt();
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

        private final ChatCompletionMessageParam message;
        private final LocalDateTime timestamp;

        public TimedChatMessage(ChatCompletionMessageParam message) {
            this.message = message;
            this.timestamp = LocalDateTime.now();
        }

        public ChatCompletionMessageParam message() {
            return message;
        }

        @JsonIgnore
        public LocalDateTime getTimestamp() {
            return timestamp;
        }
    }
}

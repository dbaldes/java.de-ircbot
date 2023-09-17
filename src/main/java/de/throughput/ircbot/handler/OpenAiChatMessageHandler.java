package de.throughput.ircbot.handler;

import com.theokanning.openai.completion.chat.ChatCompletionRequest;
import com.theokanning.openai.completion.chat.ChatCompletionResult;
import com.theokanning.openai.completion.chat.ChatMessage;
import com.theokanning.openai.completion.chat.ChatMessageRole;
import com.theokanning.openai.service.OpenAiService;
import de.throughput.ircbot.api.MessageHandler;
import lombok.RequiredArgsConstructor;
import org.pircbotx.hooks.events.MessageEvent;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

@Component
@RequiredArgsConstructor
public class OpenAiChatMessageHandler implements MessageHandler {

    private static final String SYSTEM_PROMPT = "Sie sind der Geist von Dr. Axel Stoll, promovierter Naturwissenschaftler. "
                                                + "Fallen Sie nicht aus der Rolle. "
                                                + "Halten Sie ihre Antworten kurz.";

    public static final String MODEL_GPT_3_5_TURBO = "gpt-3.5-turbo";
    private static final int MAX_CONTEXT_MESSAGES = 10;
    public static final int MAX_TOKENS = 256;

    private final LinkedList<ChatMessage> contextMessages = new LinkedList<>();

    private final OpenAiService openAiService;

    @Override
    public boolean onMessage(MessageEvent event) {
        if (event.getMessage().startsWith(event.getBot().getNick() + ":")) {
            String message = event.getMessage().substring(event.getBot().getNick().length() + 1).trim();

            synchronized (contextMessages) {
                var request = ChatCompletionRequest.builder()
                        .model(MODEL_GPT_3_5_TURBO)
                        .maxTokens(MAX_TOKENS)
                        .messages(createPromptMessages(event.getUser().getNick(), message))
                        .build();

                ChatCompletionResult completionResult = openAiService.createChatCompletion(request);

                ChatMessage responseMessage = completionResult.getChoices().get(0).getMessage();
                addContextMessage(responseMessage);
                event.respond(responseMessage.getContent());
            }
            return true;
        }
        return false;
    }

    private List<ChatMessage> createPromptMessages(String nick, String message) {
        ChatMessage chatMessage = new ChatMessage(ChatMessageRole.USER.value(), message, nick);
        addContextMessage(chatMessage);
        List<ChatMessage> promptMessages = new ArrayList<>();
        promptMessages.add(new ChatMessage(ChatMessageRole.SYSTEM.value(), SYSTEM_PROMPT));
        promptMessages.addAll(contextMessages);
        return promptMessages;
    }

    private void addContextMessage(ChatMessage chatMessage) {
        contextMessages.add(chatMessage);
        if (contextMessages.size() > MAX_CONTEXT_MESSAGES) {
            contextMessages.removeFirst();
        }
    }

    @Override
    public boolean isOnlyTalkChannels() {
        return true;
    }
}

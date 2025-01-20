package de.throughput.ircbot.handler;

import com.theokanning.openai.completion.chat.ChatCompletionRequest;
import com.theokanning.openai.completion.chat.ChatCompletionResult;
import com.theokanning.openai.completion.chat.ChatMessage;
import com.theokanning.openai.service.OpenAiService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Sends queries to AI and returns the response.
 */
@Service
public class SimpleAiService {

    private static final Logger LOG = LoggerFactory.getLogger(SimpleAiService.class);

    private static final String MODEL_NAME = "gpt-4o-mini";
    private static final int MAX_TOKENS = 300;

    private final OpenAiService openAiService;

    public SimpleAiService(OpenAiService openAiService) {
        this.openAiService = openAiService;
    }

    /**
     * Send the prompt o AI and get the response.
     */
    public String query(String prompt) {
        try {
            // Create a user message with the prompt
            ChatMessage userMessage = new ChatMessage("user", prompt);

            // Build the chat completion request
            ChatCompletionRequest request = ChatCompletionRequest.builder()
                    .model(MODEL_NAME)
                    .maxTokens(MAX_TOKENS)
                    .messages(List.of(userMessage))
                    .build();

            // Send the request to OpenAI and get the result
            ChatCompletionResult result = openAiService.createChatCompletion(request);

            // Extract the assistant's reply from the result
            ChatMessage responseMessage = result.getChoices().get(0).getMessage();

            // Return the content of the assistant's reply
            return responseMessage.getContent();

        } catch (Exception e) {
            LOG.error(e.getMessage(), e);
            return "An error occurred while processing your request: " + e.getClass().getSimpleName();
        }
    }
}

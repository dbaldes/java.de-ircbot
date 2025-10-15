package de.throughput.ircbot.handler;

import com.openai.client.OpenAIClient;
import com.openai.models.ChatModel;
import com.openai.models.chat.completions.ChatCompletion;
import com.openai.models.chat.completions.ChatCompletionCreateParams;
import com.openai.models.chat.completions.ChatCompletionMessageParam;
import com.openai.models.chat.completions.ChatCompletionUserMessageParam;
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

    private static final ChatModel MODEL = ChatModel.GPT_4O_MINI;
    private static final int MAX_TOKENS = 300;

    private final OpenAIClient openAiClient;

    public SimpleAiService(OpenAIClient openAiClient) {
        this.openAiClient = openAiClient;
    }

    /**
     * Send the prompt o AI and get the response.
     */
    public String query(String prompt) {
        try {
            ChatCompletionUserMessageParam userMessage = ChatCompletionUserMessageParam.builder()
                    .content(prompt)
                    .build();

            ChatCompletionCreateParams request = ChatCompletionCreateParams.builder()
                    .model(MODEL)
                    .maxCompletionTokens((long) MAX_TOKENS)
                    .messages(List.of(ChatCompletionMessageParam.ofUser(userMessage)))
                    .build();

            ChatCompletion result = openAiClient.chat().completions().create(request);

            return result.choices()
                    .stream()
                    .findFirst()
                    .flatMap(choice -> choice.message().content())
                    .orElse("");

        } catch (Exception e) {
            LOG.error(e.getMessage(), e);
            return "An error occurred while processing your request: " + e.getClass().getSimpleName();
        }
    }
}

package de.throughput.ircbot.handler;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OpenAiChatMessageHandlerTest {

    @Test
    void rendersMarkdownAsPlainText() {
        String response = """
                ## Results
                - **OpenAI** released [`gpt-5.6`](https://openai.com/?utm_source=openai) today.
                - ![Logo](https://openai.com/logo.png) ~~Old~~ information.
                > Source: [OpenAI](https://openai.com/)
                """;

        assertThat(OpenAiChatMessageHandler.sanitizeResponse(response))
                .isEqualTo("Results OpenAI released gpt-5.6 (https://openai.com/) today. Logo Old information. Source: OpenAI (https://openai.com/)");
    }

    @Test
    void removesOpenAiTrackingParameterFromUrls() {
        String response = "https://example.com/?utm_source=openai&foo=bar https://example.com/?foo=bar&utm_source=openai";

        assertThat(OpenAiChatMessageHandler.sanitizeResponse(response))
                .isEqualTo("https://example.com/?foo=bar https://example.com/?foo=bar");
    }
}

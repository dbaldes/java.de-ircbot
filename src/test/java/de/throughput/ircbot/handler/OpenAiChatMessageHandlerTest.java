package de.throughput.ircbot.handler;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OpenAiChatMessageHandlerTest {

    @Test
    void rendersMarkdownAsPlainText() {
        String response = """
                ## Results
                - **OpenAI** released [`gpt-5.6`](https://openai.com/) today.
                - ![Logo](https://openai.com/logo.png) ~~Old~~ information.
                > Source: [OpenAI](https://openai.com/)
                """;

        assertThat(OpenAiChatMessageHandler.sanitizeResponse(response))
                .isEqualTo("Results OpenAI released gpt-5.6 (https://openai.com/) today. Logo Old information. Source: OpenAI (https://openai.com/)");
    }
}

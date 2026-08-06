package de.throughput.ircbot.handler;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class ImageCommandHandlerTest {

    @Test
    void usesConfiguredQwenModelAndCompatibleParameters() {
        ImageCommandHandler handler = new ImageCommandHandler(
                mock(SimpleAiService.class), "api-key", "/tmp", "https://example.test/images/",
                "Qwen/Qwen-Image", 100);

        Map<String, Object> requestBody = handler.createImageRequestBody("a test image");

        assertThat(requestBody).containsEntry("model", "Qwen/Qwen-Image");
        assertThat(requestBody).containsEntry("response_format", "base64");
        assertThat(requestBody).doesNotContainKey("steps");
    }
}

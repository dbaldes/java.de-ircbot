package de.throughput.ircbot.handler.urls;

import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class FefeUrlProcessorTest {

    private static final Set<String> FEFE_URLS = Set.of(
            "https://blog.fefe.de/?ts=b5258fc3");

    private static final Set<String> NOT_FEFE_URLS = Set.of(
            "https://blog.fefe.de/?mon=201004",
            "https://blog.fefe.de/faq.html");

    @Test
    void testUrlPatterns() {
        FefeUrlProcessor processor = new FefeUrlProcessor();
        Pattern pattern = processor.getUrlPatterns()
                .iterator()
                .next();
        for (String url : FEFE_URLS) {
            Matcher matcher = pattern.matcher(url);

            assertTrue(matcher.matches());
            assertEquals("b5258fc3", matcher.group(1));
        }
    }

    @Test
    void testUrlPatternsNoMatch() {
        FefeUrlProcessor processor = new FefeUrlProcessor();

        Pattern pattern = processor.getUrlPatterns()
                .iterator()
                .next();
        for (String url : NOT_FEFE_URLS) {
            Matcher matcher = pattern.matcher(url);
            assertFalse(matcher.matches());
        }
    }
}

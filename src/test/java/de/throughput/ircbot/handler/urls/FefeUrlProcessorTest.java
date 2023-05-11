package de.throughput.ircbot.handler.urls;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;


public class FefeUrlProcessorTest {


    private static final Set<String> FEFE_URLS = Set.of(
            "https://blog.fefe.de/?ts=b5258fc3");

    private static final Set<String> NOT_FEFE_URLS = Set.of(
            "https://blog.fefe.de/?mon=201004",
            "https://blog.fefe.de/faq.html");

    @Test
    public void testUrlPatterns() {
        FefeUrlProcessor processor = new FefeUrlProcessor();
        Pattern pattern = processor.getUrlPatterns()
                .iterator()
                .next();
        for (String url : FEFE_URLS) {
            Matcher matcher = pattern.matcher(url);

            assertEquals(matcher.matches(), true);
            assertEquals(matcher.group(1), "b5258fc3");
        }
    }

    @Test
    public void testUrlPatternsNoMatch() {
        FefeUrlProcessor processor = new FefeUrlProcessor();

        Pattern pattern = processor.getUrlPatterns()
                .iterator()
                .next();
        for (String url : NOT_FEFE_URLS) {
            Matcher matcher = pattern.matcher(url);
            assertEquals(matcher.matches(), false);
        }
    }
}

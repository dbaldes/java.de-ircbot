package de.throughput.ircbot.handler.urls;


import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

public class TwitterUrlProcessorTest {

    private static final Set<String> TWITTER_URLS = Set.of(
            "https://twitter.com/swear_trek/status/1329590449124864001",
            "https://twitter.com/swear_trek/status/1329590449124864001?s=20"
    );

    @Test
    public void testUrlPatternsMatch() {
        TwitterUrlProcessor target = new TwitterUrlProcessor(null);

        Pattern pattern = target.getUrlPatterns()
                .iterator()
                .next();
        for (String url : TWITTER_URLS) {
            Matcher matcher = pattern.matcher(url);
            assertEquals(matcher.matches(), true);
            assertEquals(matcher.group(1), "1329590449124864001");
        }
    }

}

package de.throughput.ircbot.handler.urls;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.anyOf;
import static org.hamcrest.Matchers.is;

import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class YoutubeUrlProcessorTest {

    private static final Set<String> YOUTUBE_URLS = Set.of(
            "http://www.youtube.com/watch?v=0zM3nApSvMg&feature=feedrec_grec_index",
            "http://www.youtube.com/v/0zM3nApSvMg?fs=1&amp;hl=en_US&amp;rel=0",
            "http://www.youtube.com/watch?v=0zM3nApSvMg#t=0m10s",
            "http://www.youtube.com/embed/0zM3nApSvMg?rel=0",
            "http://www.youtube.com/watch?v=0zM3nApSvMg",
            "http://youtu.be/0zM3nApSvMg",
            "https://youtube.com/watch?v=gTNWm-cKNqI",
            "https://youtube.com/shorts/Q19BoZDiVqw");

    private static final Set<String> NOT_YOUTUBE_URLS = Set.of(
            "http://www.gootube.com/watch?v=0zM3nApSvMg&feature=feedrec_grec_index",
            "http://heise.de/v/0zM3nApSvMg?fs=1&amp;hl=en_US&amp;rel=0",
            "http://www.notyoutube.com/watch?v=0zM3nApSvMg#t=0m10s",
            "http://notyoutu.be/0zM3nApSvMg");

    @Test
    void testUrlPatternsMatch() {
        YoutubeUrlProcessor processor = new YoutubeUrlProcessor(null, null);

        Pattern pattern = processor.getUrlPatterns()
                .iterator()
                .next();
        for (String url : YOUTUBE_URLS) {
            Matcher matcher = pattern.matcher(url);
            assertThat("%s matches".formatted(url), matcher.matches(), is(true));
            assertThat(matcher.group(1), anyOf(is("0zM3nApSvMg"), is("gTNWm-cKNqI"), is("Q19BoZDiVqw")));
        }
    }

    @Test
    void testUrlPatternsNoMatch() {
        YoutubeUrlProcessor processor = new YoutubeUrlProcessor(null, null);

        Pattern pattern = processor.getUrlPatterns()
                .iterator()
                .next();
        for (String url : NOT_YOUTUBE_URLS) {
            Matcher matcher = pattern.matcher(url);
            assertThat(matcher.matches(), is(false));
        }
    }

}

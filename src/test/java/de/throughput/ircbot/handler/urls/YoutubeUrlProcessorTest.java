package de.throughput.ircbot.handler.urls;

import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;

import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.Test;

public class YoutubeUrlProcessorTest {
  
  private static final Set<String> YOUTUBE_URLS = Set.of(
      "http://www.youtube.com/watch?v=0zM3nApSvMg&feature=feedrec_grec_index",
      "http://www.youtube.com/v/0zM3nApSvMg?fs=1&amp;hl=en_US&amp;rel=0",
      "http://www.youtube.com/watch?v=0zM3nApSvMg#t=0m10s",
      "http://www.youtube.com/embed/0zM3nApSvMg?rel=0",
      "http://www.youtube.com/watch?v=0zM3nApSvMg",
      "http://youtu.be/0zM3nApSvMg");
  
  private static final Set<String> NOT_YOUTUBE_URLS = Set.of(
      "http://www.gootube.com/watch?v=0zM3nApSvMg&feature=feedrec_grec_index",
      "http://heise.de/v/0zM3nApSvMg?fs=1&amp;hl=en_US&amp;rel=0",
      "http://www.notyoutube.com/watch?v=0zM3nApSvMg#t=0m10s",
      "http://notyoutu.be/0zM3nApSvMg");

  @Test
  public void testUrlPatternsMatch() {
    YoutubeUrlProcessor processor = new YoutubeUrlProcessor(null);
    
    Pattern pattern = processor.getUrlPatterns().iterator().next();
    for (String url : YOUTUBE_URLS) {
      Matcher matcher = pattern.matcher(url);
      assertThat(matcher.matches(), is(true));
      assertThat(matcher.group(1), is("0zM3nApSvMg"));
    }
  }
  
  @Test
  public void testUrlPatternsNoMatch() {
    YoutubeUrlProcessor processor = new YoutubeUrlProcessor(null);
    
    Pattern pattern = processor.getUrlPatterns().iterator().next();
    for (String url : NOT_YOUTUBE_URLS) {
      Matcher matcher = pattern.matcher(url);
      assertThat(String.format("%s should not match", url), matcher.matches(), is(false));
    }
  }

}

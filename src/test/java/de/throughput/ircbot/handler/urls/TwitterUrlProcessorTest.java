package de.throughput.ircbot.handler.urls;

import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;

import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.Test;

public class TwitterUrlProcessorTest {
  
  private static final Set<String> TWITTER_URLS = Set.of(
      "https://twitter.com/swear_trek/status/1329590449124864001",
      "https://twitter.com/swear_trek/status/1329590449124864001?s=20"
      );

  @Test
  public void testUrlPatternsMatch() {
    TwitterUrlProcessor target = new TwitterUrlProcessor(null);
    
    Pattern pattern = target.getUrlPatterns().iterator().next();
    for (String url : TWITTER_URLS) {
      Matcher matcher = pattern.matcher(url);
      assertThat(matcher.matches(), is(true));
      assertThat(matcher.group(1), is("1329590449124864001"));
    }
  }

}

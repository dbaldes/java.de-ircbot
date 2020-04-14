package de.throughput.ircbot.handler.urls;

import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;

import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.Test;

public class FefeUrlProcessorTest {

  
  private static final Set<String> FEFE_URLS = Set.of(
      "https://blog.fefe.de/?ts=b5258fc3");
  
  private static final Set<String> NOT_FEFE_URLS = Set.of(
      "https://blog.fefe.de/?mon=201004",
      "https://blog.fefe.de/faq.html");
  
  @Test
  public void testUrlPatterns() {
    FefeUrlProcessor processor = new FefeUrlProcessor();
    Pattern pattern = processor.getUrlPatterns().iterator().next();
    for (String url : FEFE_URLS) {
      Matcher matcher = pattern.matcher(url);
      assertThat(matcher.matches(), is(true));
      assertThat(matcher.group(1), is("b5258fc3"));
    }
  }
  
  @Test
  public void testUrlPatternsNoMatch() {
    FefeUrlProcessor processor = new FefeUrlProcessor();
    
    Pattern pattern = processor.getUrlPatterns().iterator().next();
    for (String url : NOT_FEFE_URLS) {
      Matcher matcher = pattern.matcher(url);
      assertThat(String.format("%s should not match", url), matcher.matches(), is(false));
    }
  }
}

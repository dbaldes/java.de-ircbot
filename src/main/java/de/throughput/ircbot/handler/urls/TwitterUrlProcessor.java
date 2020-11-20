package de.throughput.ircbot.handler.urls;

import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.pircbotx.hooks.types.GenericMessageEvent;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import twitter4j.Status;
import twitter4j.Twitter;
import twitter4j.TwitterException;

@Component
@RequiredArgsConstructor
public class TwitterUrlProcessor implements UrlProcessor {

  private static final Pattern TWITTER_URL = Pattern.compile("https?://twitter\\.com/.*\\/status\\/(\\d+).*");
  
  private final Twitter twitter;
  
  @Override
  public Set<Pattern> getUrlPatterns() {
    return Set.of(TWITTER_URL);
  }

  @Override
  public void process(Matcher matcher, GenericMessageEvent event) {
    String tweetId = matcher.group(1);
    try {
      Status status = twitter.showStatus(Long.parseLong(tweetId));

      event.respond(String.format("^ @%s on twitter: %s", status.getUser().getScreenName(), status.getText()));
    } catch (TwitterException e) {
      event.respond("that didn't work: " + e.getMessage());
    }
  }

}

package de.throughput.ircbot.handler.urls;

import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.pircbotx.hooks.types.GenericMessageEvent;
import org.springframework.stereotype.Component;
import com.github.scribejava.core.model.OAuth2AccessToken;
import com.twitter.clientlib.ApiException;
import com.twitter.clientlib.TwitterCredentialsBearer;
import com.twitter.clientlib.api.TwitterApi;
import com.twitter.clientlib.auth.TwitterOAuth20AppOnlyService;
import com.twitter.clientlib.model.Get2TweetsIdResponse;
import com.twitter.clientlib.model.Tweet;

@Component
@RequiredArgsConstructor
public class TwitterUrlProcessor implements UrlProcessor {

    private static final Pattern TWITTER_URL = Pattern.compile("https?://twitter\\.com/.*\\/status\\/(\\d+).*");

    private final TwitterApi twitter;

    @Override
    public Set<Pattern> getUrlPatterns() {
        return Set.of(TWITTER_URL);
    }

    @Override
    public void process(Matcher matcher, GenericMessageEvent event) {
        String tweetId = matcher.group(1);
        try {
            Get2TweetsIdResponse response = twitter.tweets()
                    .findTweetById(tweetId)
                    .tweetFields(Set.of("author_id", "id"))
                    .execute();

            Tweet tweet = response.getData();
            String userName = twitter.users()
                    .findUserById(tweet.getAuthorId())
                    .userFields(Set.of("username"))
                    .execute()
                    .getData()
                    .getUsername();

            event.respond(String.format("^ @%s on twitter: %s", userName, tweet.getText()));
        } catch (ApiException e) {
            //event.respond("that didn't work: " + e.getCode());
        }
    }
}

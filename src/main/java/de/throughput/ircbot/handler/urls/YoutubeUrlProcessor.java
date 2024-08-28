package de.throughput.ircbot.handler.urls;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.pircbotx.hooks.events.MessageEvent;
import org.pircbotx.hooks.types.GenericMessageEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import com.google.api.services.youtube.YouTube;
import com.google.api.services.youtube.model.Video;
import com.google.api.services.youtube.model.VideoListResponse;

import de.throughput.ircbot.handler.TitleEvent;

/**
 * Processes YouTube URLs.
 */
@Component
@RequiredArgsConstructor
public class YoutubeUrlProcessor implements UrlProcessor {

    private static final Pattern YOUTUBE_URL = Pattern.compile(
            "https?://(?:youtu.be/|(?:www.youtube.com|youtube.com)/(?:v/|u/\\w/|embed/|shorts/|watch\\?v=))([^#\\&\\?]*).*");

    private final YouTube youtube;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    public Set<Pattern> getUrlPatterns() {
        return Set.of(YOUTUBE_URL);
    }

    @Override
    public void process(Matcher matcher, MessageEvent event) {
        getYoutubeInfo(matcher.group(1), event);
    }

    private void getYoutubeInfo(String id, MessageEvent event) {
        try {
            YouTube.Videos.List videosListByIdRequest = youtube.videos()
                    .list("snippet");
            videosListByIdRequest.setId(id);

            VideoListResponse response = videosListByIdRequest.execute();

            List<Video> items = response.getItems();

            if (!items.isEmpty()) {
                Video video = response.getItems()
                        .get(0);

                String title = video.getSnippet()
                        .getTitle();

                event.getChannel().send().message(String.format("^ YouTube: '%s'", title));

                eventPublisher.publishEvent(new TitleEvent(this, title));
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}

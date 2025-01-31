package de.throughput.ircbot.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rometools.rome.feed.synd.SyndEntry;
import com.rometools.rome.feed.synd.SyndFeed;
import com.rometools.rome.io.FeedException;
import com.rometools.rome.io.SyndFeedInput;
import com.rometools.rome.io.XmlReader;
import lombok.Getter;
import lombok.Setter;
import org.jsoup.Jsoup;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.PrintStream;
import java.net.URL;
import java.nio.file.Path;
import java.util.List;

@Component
public class NewsService {

    private Path newsCachePath;

    public NewsService(@Value("${news.cache.path}") Path newsCachePath) {
        this.newsCachePath = newsCachePath;
    }

    public synchronized String getNews() {
        try {
            // Check if cached file exists and is recent (under 1 hour old)
            var newsFile = newsCachePath.toFile();
            if (newsFile.exists()) {
                long ageInMillis = System.currentTimeMillis() - newsFile.lastModified();
                // If file is under an hour old, read and return its content
                if (ageInMillis < 3600000) {
                    return java.nio.file.Files.readString(newsCachePath);
                }
            }

            // Otherwise, generate a new version of the news and cache it
            var baos = new java.io.ByteArrayOutputStream();
            try (var ps = new PrintStream(baos)) {
                getNews(ps);
            }

            String newsContent = baos.toString(java.nio.charset.StandardCharsets.UTF_8);
            java.nio.file.Files.writeString(newsCachePath, newsContent, java.nio.charset.StandardCharsets.UTF_8);
            return newsContent;
        } catch (IOException | FeedException e) {
            throw new RuntimeException("Failed to retrieve news.", e);
        }
    }

    private static void getNews(PrintStream out) throws IOException, FeedException {
        FeedsConfig feedsConfig = getFeedsConfig();
        for (FeedEntry feed : feedsConfig.getFeeds()) {
            out.println("================================");
            out.println(feed.getTitle() + " - " + feed.getPolitical_circle() + ":");

            SyndFeedInput input = new SyndFeedInput();
            SyndFeed syndFeed = input.build(new XmlReader(new URL(feed.getFeed_url())));

            for (SyndEntry entry : syndFeed.getEntries()) {
                out.print(stripHtmlTags(entry.getTitle()));
                if (entry.getDescription() != null) {
                    out.printf(": %s", stripHtmlTags(entry.getDescription().getValue()));
                }
                out.println();
            }
        }
    }

    private static String stripHtmlTags(String html) {
        if (html == null || html.isEmpty()) {
            return "";
        }
        return Jsoup.parse(html).text().trim();
    }

    private static FeedsConfig getFeedsConfig() throws IOException {
        try (var configStream = NewsService.class.getResourceAsStream("/config/rss-feeds.json")) {
            return new ObjectMapper().readValue(configStream, FeedsConfig.class);
        }
    }
}

@Getter
@Setter
class FeedsConfig {
    private List<FeedEntry> feeds;
}

@Getter
@Setter
class FeedEntry {
    private String title;
    private String political_circle;
    private String feed_url;
}

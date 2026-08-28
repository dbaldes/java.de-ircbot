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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.PrintStream;
import java.net.URL;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;

@Component
public class NewsService {

    private static final Logger LOG = LoggerFactory.getLogger(NewsService.class);
    private static final long CACHE_DURATION_MILLIS = 3_600_000;
    private static final Pattern NEGATIVE_KEYWORDS = Pattern.compile("\\b(attack|bomb|conflict|crime|death|disaster|earthquake|famine|flood|hurricane|killed|shooting|trade war|war|wildfire)\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern POSITIVE_KEYWORDS = Pattern.compile("\\b(achiev|advance|award|breakthrough|collaborat|complete|conserv|cure|develop|discover|educat|first|improv|innov|launch|milestone|mission|progress|protect|record|recover|reduc|research|restor|success|treat|vaccin)\\w*", Pattern.CASE_INSENSITIVE);

    private final Path newsCachePath;
    private final Path goodNewsCachePath;

    public NewsService(@Value("${news.cache.path}") Path newsCachePath,
                       @Value("${goodnews.cache.path}") Path goodNewsCachePath) {
        this.newsCachePath = newsCachePath;
        this.goodNewsCachePath = goodNewsCachePath;
    }

    public synchronized String getNews() {
        return getCachedNews(newsCachePath, "/config/rss-feeds.json", false);
    }

    public synchronized String getGoodNews() {
        return getCachedNews(goodNewsCachePath, "/config/good-news-rss-feeds.json", true);
    }

    private String getCachedNews(Path cachePath, String configResource, boolean positiveOnly) {
        try {
            // Check if cached file exists and is recent (under 1 hour old)
            var newsFile = cachePath.toFile();
            if (newsFile.exists()) {
                long ageInMillis = System.currentTimeMillis() - newsFile.lastModified();
                // If file is under an hour old, read and return its content
                if (ageInMillis < CACHE_DURATION_MILLIS) {
                    return java.nio.file.Files.readString(cachePath);
                }
            }

            // Otherwise, generate a new version of the news and cache it
            var baos = new java.io.ByteArrayOutputStream();
            try (var ps = new PrintStream(baos)) {
                writeNews(ps, configResource, positiveOnly);
            }

            String newsContent = baos.toString(java.nio.charset.StandardCharsets.UTF_8);
            java.nio.file.Files.writeString(cachePath, newsContent, java.nio.charset.StandardCharsets.UTF_8);
            return newsContent;
        } catch (IOException | FeedException e) {
            throw new RuntimeException("Failed to retrieve news.", e);
        }
    }

    private static void writeNews(PrintStream out, String configResource, boolean positiveOnly) throws IOException, FeedException {
        FeedsConfig feedsConfig = getFeedsConfig(configResource);
        for (FeedEntry feed : feedsConfig.getFeeds()) {
            out.println("================================");
            out.println(feed.getTitle() + " - " + feed.getLabel() + ":");

            try {
                SyndFeedInput input = new SyndFeedInput();
                SyndFeed syndFeed = input.build(new XmlReader(new URL(feed.getFeed_url())));

                int entriesWritten = 0;
                for (SyndEntry entry : syndFeed.getEntries()) {
                    String title = stripHtmlTags(entry.getTitle());
                    String description = entry.getDescription() == null ? "" : stripHtmlTags(entry.getDescription().getValue());
                    if (positiveOnly && !isPositiveCandidate(title + " " + description)) {
                        continue;
                    }
                    out.print(title);
                    if (!description.isEmpty()) {
                        out.printf(": %s", description);
                    }
                    if (entry.getLink() != null && !entry.getLink().isBlank()) {
                        out.printf(" [%s]", entry.getLink());
                    }
                    out.println();
                    entriesWritten++;
                    if (positiveOnly && entriesWritten == 10) {
                        break;
                    }
                }
            } catch (IOException | FeedException e) {
                if (!positiveOnly) {
                    throw e;
                }
                LOG.warn("Could not retrieve good-news feed {}", feed.getTitle(), e);
            }
        }
    }

    private static boolean isPositiveCandidate(String text) {
        return !NEGATIVE_KEYWORDS.matcher(text).find() && POSITIVE_KEYWORDS.matcher(text).find();
    }

    private static String stripHtmlTags(String html) {
        if (html == null || html.isEmpty()) {
            return "";
        }
        return Jsoup.parse(html).text().trim();
    }

    private static FeedsConfig getFeedsConfig(String configResource) throws IOException {
        try (var configStream = NewsService.class.getResourceAsStream(configResource)) {
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
    private String category;
    private String feed_url;

    public String getLabel() {
        return category == null ? political_circle : category;
    }
}

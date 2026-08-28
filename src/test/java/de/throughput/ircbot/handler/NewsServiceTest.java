package de.throughput.ircbot.handler;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

class NewsServiceTest {

    @TempDir
    Path tempDirectory;

    @Test
    void usesSiblingCacheWhenGoodNewsCacheIsNotConfigured() throws IOException {
        Path goodNewsCache = tempDirectory.resolve("goodnews.cache");
        Files.writeString(goodNewsCache, "cached good news");
        NewsService service = new NewsService(tempDirectory.resolve("news.cache"), "");

        assertThat(service.getGoodNews()).isEqualTo("cached good news");
    }
}

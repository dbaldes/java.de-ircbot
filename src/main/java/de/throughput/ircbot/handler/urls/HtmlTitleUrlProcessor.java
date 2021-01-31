package de.throughput.ircbot.handler.urls;

import java.io.IOException;
import java.net.URI;

import org.apache.commons.lang3.StringUtils;
import org.jsoup.HttpStatusException;
import org.jsoup.Jsoup;
import org.jsoup.UnsupportedMimeTypeException;
import org.pircbotx.hooks.types.GenericMessageEvent;
import org.springframework.stereotype.Component;

/**
 * Gets a preview for URLs by looking at the HTML title tag.
 * 
 * Does not implement {@link UrlProcessor} - serves as fallback if no specific processor matched.
 */
@Component
public class HtmlTitleUrlProcessor {
  
  private static final int MAX_BODY_SIZE_512K = 524288;
  private static final int READ_TIMEOUT_MS = 3000;
  private static final String MOZILLA_USER_AGENT = "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/87.0.4280.88 Safari/537.36";

  public void process(String url, GenericMessageEvent event) {
    try {
      URI uri = URI.create(url);
      String title = Jsoup.connect(url)
          .userAgent(MOZILLA_USER_AGENT)
          .header("Accept-Language", "en-US,en;q=0.9")
          .maxBodySize(MAX_BODY_SIZE_512K)
          .timeout(READ_TIMEOUT_MS)
          .get().title();
      
      if (!StringUtils.isEmpty(title)) {
        if (title.length() > 600) {
          title = title.substring(0, 600) + "(...)";
        }
        String message = String.format("^ %s: '%s'", uri.getHost(), title);
        event.respond(message);
      }
    } catch (HttpStatusException e) {
      event.respond(String.format("%d: %s", e.getStatusCode(), e.getMessage()));
    } catch (UnsupportedMimeTypeException e) {
      // ignore
    } catch (IOException e) {
      e.printStackTrace();
    }
  }
  
}

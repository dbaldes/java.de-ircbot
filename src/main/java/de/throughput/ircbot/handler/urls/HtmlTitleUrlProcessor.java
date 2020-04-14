package de.throughput.ircbot.handler.urls;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.pircbotx.hooks.types.GenericMessageEvent;
import org.springframework.stereotype.Component;

/**
 * Gets a preview for various web sites by looking at the HTML title tag.
 */
@Component
public class HtmlTitleUrlProcessor implements UrlProcessor {

  private static final Pattern HTML_TITLE_PATTERN = Pattern.compile(".*<title>([^<]+)</title>.*");
  
  private static final Pattern URL_GITHUB = Pattern.compile("^https://github.com/.*$");
  private static final Pattern URL_AVHERALD = Pattern.compile("^https?://(?:www\\.)?avherald.com/.*$");
  private static final Pattern URL_SPIEGEL = Pattern.compile("^https?://(?:www\\.)?spiegel.de/.*$");
  private static final Pattern URL_HEISE = Pattern.compile("^https?://(?:www\\.)?heise.de/.*$");
  
  @Override
  public Set<Pattern> getUrlPatterns() {
    return Set.of(URL_GITHUB, URL_AVHERALD, URL_SPIEGEL, URL_HEISE);
  }

  @Override
  public void process(Matcher matcher, GenericMessageEvent event) {
    String url = matcher.group(0);
    
    processHtmlTitle(event, url);
  }
  
  private void processHtmlTitle(GenericMessageEvent event, String url) {
    try {
      URI uri = URI.create(url);
      HttpRequest request = HttpRequest.newBuilder(uri)
          .setHeader("User-Agent", "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/79.0.3945.130 Safari/537.36" )
          .setHeader("Accept-Language", "en-US,en;q=0.9") 
          .setHeader("Accept-Encoding", "identity")
          .setHeader("DNT", "1")
          .GET().build();
      
      HttpClient.newHttpClient()
          .sendAsync(request, BodyHandlers.ofString())
          .thenAccept(response -> processHttpResonse(event, uri, response));
      
    } catch (Exception e) {
      e.printStackTrace();
    }
  }
  
  private void processHttpResonse(GenericMessageEvent event, URI uri, HttpResponse<String> response) {
    if (response.statusCode() == 200) {
      Matcher matcher = HTML_TITLE_PATTERN.matcher(response.body());
      if (matcher.find()) {
        String title = matcher.group(1);
        if (title.length() > 600) {
          title = title.substring(0, 600) + "(...)";
        }
        String message = String.format("^ %s: '%s'", uri.getHost(), title);
        event.respond(message);
      }
    } else {
      event.respond("" + response.statusCode());
    }
  }
  
}

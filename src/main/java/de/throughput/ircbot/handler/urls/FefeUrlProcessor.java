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
 * Gets a preview for Fefe's blog URLs.
 */
@Component
public class FefeUrlProcessor implements UrlProcessor {

    private static final Pattern FEFE_URL = Pattern.compile("^https://blog.fefe.de/\\?ts=([0-9a-f]{8})$");

    private static final Pattern FEFE_TITLE_PATTERN = Pattern.compile(
            ".*<li><a href=\"[^\"]+\">\\[l\\]</a> *(([^\"]|\"[^\"]*\")*?([\\.\\!\\?])).*");

    @Override
    public Set<Pattern> getUrlPatterns() {
        return Set.of(FEFE_URL);
    }

    @Override
    public void process(Matcher matcher, GenericMessageEvent event) {
        processFefeUrl(event, matcher.group(0));
    }

    private void processFefeUrl(GenericMessageEvent event, String url) {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .GET()
                .build();
        HttpClient.newHttpClient()
                .sendAsync(request, BodyHandlers.ofString())
                .thenAccept(httpResponse -> {
                    processResponse(event, httpResponse);
                });
    }

    private void processResponse(GenericMessageEvent event, HttpResponse<String> httpResponse) {
        if (httpResponse.statusCode() == 200) {
            Matcher matcher = FEFE_TITLE_PATTERN.matcher(httpResponse.body());
            if (matcher.find()) {
                String title = matcher.group(1);
                title = title.replaceAll("<[^>]+>", "");
                if (title.length() > 600) {
                    title = title.substring(0, 600) + "(...)";
                }
                event.respond(String.format("^ Fefe's Blog: '%s'", title));
            }
        } else {
            event.respond("" + httpResponse.statusCode());
        }
    }
}

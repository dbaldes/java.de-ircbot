package de.throughput.ircbot.handler.urls;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import lombok.RequiredArgsConstructor;
import org.pircbotx.User;
import org.pircbotx.hooks.events.MessageEvent;
import org.springframework.stereotype.Component;

import de.throughput.ircbot.IrcBotConfig;
import de.throughput.ircbot.api.MessageHandler;

/**
 * Handles messages with URLs by passing the URLs to {@link UrlProcessor}s.
 */
@Component
@RequiredArgsConstructor
public class UrlMessageHandler implements MessageHandler {

    private final IrcBotConfig botConfig;
    private final UrlSinkService urlSink;
    private final List<UrlProcessor> urlProcessors;
    private final HtmlTitleUrlProcessor htmlTitleFallback;

    @Override
    public boolean onMessage(MessageEvent event) {
        UrlParser.streamUrls(event.getMessage())
                .forEach(urlString -> {
                    if (botConfig.getTalkChannels()
                            .contains(event.getChannel()
                                    .getName())) {
                        AtomicBoolean processed = new AtomicBoolean(false);
                        for (UrlProcessor urlProcessor : urlProcessors) {
                            urlProcessor.getUrlPatterns()
                                    .stream()
                                    .map(pattern -> pattern.matcher(urlString))
                                    .filter(Matcher::matches)
                                    .findFirst()
                                    .ifPresent(matcher -> {
                                        urlProcessor.process(matcher, event);
                                        processed.set(true);
                                    });
                        }
                        if (!processed.get()) {
                            htmlTitleFallback.process(urlString, event);
                        }
                    }

                    String hostname = event.getBot()
                            .getConfiguration()
                            .getServers()
                            .get(0)
                            .getHostname();
                    User user = event.getUser();
                    this.urlSink.processUrl(hostname, event.getChannel()
                                    .getName(), user.getNick(), user.getLogin(),
                            user.getHostname(), urlString);
                });
        return false;
    }

    @Override
    public boolean isOnlyTalkChannels() {
        return false;
    }
}

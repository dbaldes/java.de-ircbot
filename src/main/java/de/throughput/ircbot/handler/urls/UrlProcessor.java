package de.throughput.ircbot.handler.urls;

import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.pircbotx.hooks.events.MessageEvent;
import org.pircbotx.hooks.types.GenericMessageEvent;

/**
 * Interface for an URL processor.
 */
public interface UrlProcessor {

    /**
     * Return a set of patterns to match against.
     * <p>
     * If any of the patterns matches, a matcher instance is passed to the process method.
     *
     * @return patterns
     */
    Set<Pattern> getUrlPatterns();

    /**
     * Process a match.
     *
     * @param matcher matcher which matched one of the patterns.
     * @param event   matching message event
     */
    void process(Matcher matcher, MessageEvent event);

}

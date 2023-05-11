package de.throughput.ircbot.handler.urls;

import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 * Utility class which provides a stream of valid URLs in a text.
 */
public class UrlParser {

    private static final Pattern URL_PATTERN = Pattern.compile("((?:ht|f)tps?://\\S+)");

    private UrlParser() {
    }

    /**
     * Returns a stream consisting of all valid http(s) and ftp URLs in the given text.
     *
     * @param text text
     * @return stream of URLs
     */
    public static Stream<String> streamUrls(String text) {
        Iterator<String> matchIterator = new MatchIterator(URL_PATTERN.matcher(text));

        return StreamSupport
                .stream(Spliterators.spliteratorUnknownSize(matchIterator, Spliterator.ORDERED | Spliterator.NONNULL), false)
                .filter(UrlParser::validUrl);
    }

    /**
     * Checks if the given string is a valid URL.
     *
     * @param url string
     * @return true if the URL can be parsed
     */
    private static boolean validUrl(String surl) {
        try {
            URL url = new URL(surl);
            url.toURI();
            return true;
        } catch (MalformedURLException | URISyntaxException e) {
            return false;
        }
    }

    /**
     * An iterator which iterates matches.
     */
    private static class MatchIterator implements Iterator<String> {

        private final Matcher matcher;
        private String nextMatch;
        private boolean end;

        public MatchIterator(Matcher matcher) {
            this.matcher = matcher;
        }

        @Override
        public boolean hasNext() {
            if (end) {
                return false;
            }
            if (nextMatch != null) {
                return true;
            }
            if (matcher.find()) {
                nextMatch = matcher.group(1);
            } else {
                end = true;
            }
            return !end;
        }

        @Override
        public String next() {
            if (!hasNext()) {
                throw new NoSuchElementException();
            }
            String match = nextMatch;
            nextMatch = null;
            return match;
        }

    }

}

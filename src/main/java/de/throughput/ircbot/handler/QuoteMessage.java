package de.throughput.ircbot.handler;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * A data structure representing a message from a certain nick in a certain channel at a certain time.
 */
@Getter
@AllArgsConstructor
public class QuoteMessage {

    private final String channel;
    private final String nick;
    private final long timestamp;
    private final String message;

    public String getKey() {
        return channel + ":" + nick;
    }

    public String getAsMessage() {
        return String.format("<%s> %s", nick, message);
    }
}

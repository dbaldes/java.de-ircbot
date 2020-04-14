package de.throughput.ircbot.handler;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * A data structure representing a message from a certain nick in a certain channel at a certain time.
 */
@Getter
@AllArgsConstructor
public class QuoteMessage {

  private String channel;
  private String nick;
  private long timestamp;
  private String message;

  public String getKey() {
    return channel + ":" + nick;
  }
  
  public String getAsMessage() {
    return String.format("<%s> %s", nick, message);
  }
}

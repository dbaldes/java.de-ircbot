package de.throughput.ircbot.handler;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * A data structure representing a slogan.
 */
@Getter
@AllArgsConstructor
public class Slogan {

  private final String channel;
  private final String nick;
  private final long timestamp;
  private final String slogan;

}

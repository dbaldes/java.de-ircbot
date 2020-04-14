package de.throughput.ircbot;

import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import lombok.Getter;

/**
 * Holds configuration values.
 */
@Component
@Getter
public class IrcBotConfig {

  @Autowired
  public IrcBotConfig(@Value("${ircbot.talkchannels}") String talkChannels) {
    this.talkChannels = Set.of(talkChannels.split(","));
  }
  
  /**
   * Channels on which the bot is allowed to talk.
   */
  private Set<String> talkChannels;
    
}

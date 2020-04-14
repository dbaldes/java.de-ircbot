package de.throughput.ircbot.api;

import org.pircbotx.hooks.events.MessageEvent;

/**
 * Interface for message handlers.
 *
 * Message handlers can process any message posted on any channel the bot has joined.
 */
public interface MessageHandler {
  
  /**
   * If {@code true}, the handler is only called for configured talkchannels.
   * 
   * @return {@code true} if only called for talk channels
   */
  boolean isOnlyTalkChannels();
	
  /**
   * Called for every {@link MessageEvent} on relevant channels.
   * 
   * @param event message event
   * @return {@code true} if the message was handled and further processing should cease
   */
  boolean onMessage(MessageEvent event);
  
}

package de.throughput.ircbot.api;

import java.util.Optional;

import org.pircbotx.hooks.events.MessageEvent;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Event that is passed to a {@link CommandHandler} when a command is executed.
 */
@Getter
@AllArgsConstructor
public class CommandEvent {

  private MessageEvent event;
  private Command command;
  private String commandPrefix;
  private Optional<String> argLine;
  
  public void respond(String answer) {
    event.respond(answer);
  }

}

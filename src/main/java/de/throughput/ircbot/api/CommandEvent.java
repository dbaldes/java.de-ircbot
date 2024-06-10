package de.throughput.ircbot.api;

import java.util.Optional;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.pircbotx.hooks.events.MessageEvent;

/**
 * Event that is passed to a {@link CommandHandler} when a command is executed.
 */
@Getter
@AllArgsConstructor
public class CommandEvent {

    private final MessageEvent event;
    private final Command command;
    private final String commandPrefix;
    private final Optional<String> argLine;

    public void respond(String answer) {
        event.respond(answer);
    }

}

package de.throughput.ircbot.api;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;

/**
 * Defines a command that can be executed from talkchannels.
 */
@Getter
@AllArgsConstructor
@EqualsAndHashCode(of = "command")
public class Command {

    private String command;
    private String usage;

}

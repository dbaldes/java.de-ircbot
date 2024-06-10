package de.throughput.ircbot.api;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Defines a command that can be executed from talkchannels.
 */
@Getter
@RequiredArgsConstructor
@EqualsAndHashCode(of = "command")
public class Command {

    private final String command;
    private final String usage;
    private final boolean privileged;

    public Command(String command, String usage) {
        this.command = command;
        this.usage = usage;
        this.privileged = false;
    }
}

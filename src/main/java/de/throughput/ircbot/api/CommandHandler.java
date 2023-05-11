package de.throughput.ircbot.api;

import java.util.Set;

/**
 * Interface for command handlers.
 * <p>
 * Command handlers process commands posted by users on channels.
 */
public interface CommandHandler {

    /**
     * Handles a command event.
     *
     * @param command command event
     * @return true if handled and processing should be stopped
     */
    boolean onCommand(CommandEvent command);

    /**
     * @return commands handled by this handler
     */
    Set<Command> getCommands();

}

package de.throughput.ircbot.handler;

import java.util.Set;
import org.springframework.stereotype.Component;

import de.throughput.ircbot.api.Command;
import de.throughput.ircbot.api.CommandEvent;
import de.throughput.ircbot.api.CommandHandler;

/**
 * Lagerfeld command handler.
 * <p>
 * !lagerfeld <text> - responds with a Lagerfeld quote.
 * @deprecated disabled - replaced with {@link LagerfeldAiCommandHandler}.
 */
//@Component
@Deprecated
public class LagerfeldCommandHandler implements CommandHandler {

    private static final Command CMD_LAGERFELD = new Command("lagerfeld", "lagerfeld <text> - responds with a Lagerfeld quote.");

    @Override
    public Set<Command> getCommands() {
        return Set.of(CMD_LAGERFELD);
    }

    @Override
    public boolean onCommand(CommandEvent command) {
        command.getArgLine()
                .ifPresentOrElse(
                        text -> command.getEvent()
                                .getChannel()
                                .send()
                                .message("\"" + text + ", hat die Kontrolle über sein Leben verloren.\" -- Karl Lagerfeld."),
                        () -> command.respond(CMD_LAGERFELD.getUsage()));
        return true;
    }
}

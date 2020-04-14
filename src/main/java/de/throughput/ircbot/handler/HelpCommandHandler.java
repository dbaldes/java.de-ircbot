package de.throughput.ircbot.handler;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import de.throughput.ircbot.api.Command;
import de.throughput.ircbot.api.CommandEvent;
import de.throughput.ircbot.api.CommandHandler;

/**
 * Handles the !help command.
 */
@Component
public class HelpCommandHandler implements CommandHandler {
  
  private static final Command CMD_HELP = new Command("help", 
      "Usage: !help all commands; !help <command> send usage information for command");

  private Map<String, Command> commandsByCommand;
  
  @Autowired
  public HelpCommandHandler(List<CommandHandler> commandHandlers) {
    commandsByCommand = new LinkedHashMap<>();
    commandHandlers.stream()
      .flatMap(handler -> handler.getCommands().stream())
      .forEach(command -> commandsByCommand.put(command.getCommand(), command));
  }
  
  @Override
  public boolean onCommand(CommandEvent command) {
    if (command.getCommand().equals(CMD_HELP)) {
      command.getArgLine().ifPresentOrElse(
          cmdString -> {
            Command cmd = commandsByCommand.get(cmdString);
            if (cmd != null) {
              command.respond(cmd.getUsage());
            } else {
              command.respond(command.getCommand().getUsage());
            }
          },
          () -> command.respond("Commands: " 
             + String.join(", ", commandsByCommand.keySet())
             + ", help"));
      return true;
    }
    return false;
  }

  @Override
  public Set<Command> getCommands() {
    return Set.of(CMD_HELP);
  }

}

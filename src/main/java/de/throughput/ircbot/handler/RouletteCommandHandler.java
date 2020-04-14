package de.throughput.ircbot.handler;

import java.util.Random;
import java.util.Set;

import org.springframework.stereotype.Component;

import de.throughput.ircbot.api.Command;
import de.throughput.ircbot.api.CommandEvent;
import de.throughput.ircbot.api.CommandHandler;

/**
 * Command handler which handles the roulette game.
 */
@Component
public class RouletteCommandHandler implements CommandHandler {

  private static final Command CMD_ROULETTE = new Command("roulette",
      "Usage: !roulette spin - spin the chambers; "
          + "!roulette - pull the trigger");
  
  private static final int CHAMBERS = 6;
  private Random rand = new Random();
  private int chamber;
  
  public RouletteCommandHandler() {
    spin();
  }

  private synchronized void spin() {
    chamber = rand.nextInt(CHAMBERS);
  }
  
  private synchronized boolean trigger() {
    if (++chamber >= CHAMBERS) {
      chamber = 0;
    }
    return chamber == 0;
  }
  
  @Override
  public boolean onCommand(CommandEvent command) {
    if (command.getCommand().equals(CMD_ROULETTE)) {
      command.getArgLine().ifPresentOrElse(
          argument -> {
            if (argument.equals("spin")) {
              spin();
              command.getEvent().getChannel().send().action(
                  "spins the chambers and hands the revolver back to "
                      +command.getEvent().getUser().getNick());
              command.respond("your turn.");
            } else {
              command.respond(command.getCommand().getUsage());
            }
          },
          () -> {
            if (trigger()) {
              command.respond("BANG!");
              command.respond("you're dead.");
              spin();
              command.getEvent().getChannel().send().action("loads a new bullet and spins the chambers");
              command.getEvent().getChannel().send().message("who's next?");
            } else {
              command.respond("*click*");
            }
          });
      return true;
    }
    return false;
  }

  @Override
  public Set<Command> getCommands() {
    return Set.of(CMD_ROULETTE);
  }
  
}

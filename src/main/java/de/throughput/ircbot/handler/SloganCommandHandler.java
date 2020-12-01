package de.throughput.ircbot.handler;

import static java.util.stream.Collectors.toSet;

import java.util.Optional;
import java.util.Random;
import java.util.Set;

import org.pircbotx.PircBotX;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import de.throughput.ircbot.IrcBotConfig;
import de.throughput.ircbot.api.Command;
import de.throughput.ircbot.api.CommandEvent;
import de.throughput.ircbot.api.CommandHandler;

/**
 * Slogan command handler.
 * 
 * !slogan - shout a random slogan.
 * !addslogan <slogan> - add a slogan.
 * !rmslogan <slogan> - remove a slogan.
 * 
 * Also, the bot will randomly share a slogan every now and then if
 * there is activity in the channel.
 */
@Component
public class SloganCommandHandler implements CommandHandler {

  /**
   * Probability per minute of posting a random slogan if the channel is active.
   * E.g. 0.1 means that on average, every ten minutes a slogan will be posted if
   * the channel is active.
   */
  private static final float P_RANDOM_SLOGAN = 0.1f;
  
  /**
   * After posting a random slogan, the bot will keep quiet on all channels for 
   * at least this many seconds.
   */
  private static final long RANDOM_SLOGAN_COOLDOWN_SECONDS = 600;
  
  private static final Command CMD_SLOGAN = new Command("slogan",
      "!slogan - enhance morale of the plebs by shouting a slogan.");
  
  private static final Command CMD_ADDSLOGAN = new Command("addslogan",
      "!addslogan <slogan> - add a slogan.");
  
  private static final Command CMD_RMSLOGAN = new Command("rmslogan",
      "!rmslogan <slogan> - remove a slogan.");

  private final IrcBotConfig botConfig;
  private final JdbcTemplate jdbc;
  private final PircBotX bot;
  private final Random rnd = new Random();
  
  private long lastSloganTimestampEpochMillis = 0L;
  
  @Autowired
  public SloganCommandHandler(IrcBotConfig botConfig, JdbcTemplate jdbc, @Lazy PircBotX bot) {
    super();
    this.botConfig = botConfig;
    this.jdbc = jdbc;
    this.bot = bot;
  }

  @Override
  public Set<Command> getCommands() {
    return Set.of(CMD_SLOGAN, CMD_ADDSLOGAN, CMD_RMSLOGAN);
  }
  
  @Override
  public boolean onCommand(CommandEvent command) {

    if (CMD_SLOGAN.equals(command.getCommand())) {
      lookupRandomSlogan(command.getEvent().getChannel().getName())
        .ifPresentOrElse(
            slogan -> command.getEvent().getChannel().send().message(slogan.getSlogan()),
            () -> command.respond("No slogan found. Add a slogan with !addslogan <slogan>."));
    } else if (CMD_ADDSLOGAN.equals(command.getCommand())) {
      command.getArgLine().ifPresentOrElse(
          argLine -> {
            storeSlogan(command.getEvent().getChannel().getName(), command.getEvent().getUser().getNick(), argLine);
            command.respond("of course, comrade!");
          },
          () -> command.respond(CMD_ADDSLOGAN.getUsage()));
    } else if (CMD_RMSLOGAN.equals(command.getCommand())) {
      command.getArgLine().ifPresentOrElse(
          argLine -> {
            if (!removeSlogan(command.getEvent().getChannel().getName(), argLine)) {
              command.respond("Slogan not found.");
            } else {
              command.respond("Slogan deleted.");
            }
          },
          () -> command.respond(CMD_RMSLOGAN.getUsage()));
    }
    return false;
  }

  /**
   * Stores a slogan in the database.
   * 
   * @param slogan the slogan
   */
  private void storeSlogan(String channel, String nick, String slogan) {
    jdbc.update("INSERT INTO slogan (channel, nick, slogan) VALUES (?, ?, ?)",
        channel, nick, slogan);
  }
  
  /**
   * Deletes a slogan from the database.
   * 
   * @param channel the channel for which to delete the slogan
   * @param text the slogan to delete
   * @return true if the slogan was found and deleted
   */
  private boolean removeSlogan(String channel, String text) {
    return jdbc.update("DELETE FROM slogan WHERE channel = ? AND slogan = ?", channel, text) > 0;
  }
  
  /**
   * Reads a random slogan from the database.
   * 
   * @param channel channel
   * @return quote or null if none found
   */
  private Optional<Slogan> lookupRandomSlogan(String channel) {
    try {
      return Optional.of(jdbc.queryForObject(
          "SELECT EXTRACT(EPOCH FROM timestamp) * 1000, nick, slogan "
          + "FROM slogan "
          + "WHERE channel = ? ORDER BY RANDOM() LIMIT 1",
          (rs, rowNum) -> new Slogan(channel, rs.getString(2), rs.getLong(1), rs.getString(3)),
          channel));
    } catch (EmptyResultDataAccessException e) {
      return Optional.empty();
    }
  }
  
  /**
   * Possibly sends a random quote every minute to active channels.
   */
  @Scheduled(fixedDelay = 60000)
  public void scheduledRandomSlogan() {
    long currentTimeEpochMillis = System.currentTimeMillis();
    if (currentTimeEpochMillis - lastSloganTimestampEpochMillis > RANDOM_SLOGAN_COOLDOWN_SECONDS * 1000L) {
      readActiveTalkChannels().forEach(channel -> {
        if (rnd.nextFloat() <= P_RANDOM_SLOGAN) {
          lastSloganTimestampEpochMillis = currentTimeEpochMillis;
          lookupRandomSlogan(channel).ifPresent(slogan -> bot.send().message(channel, slogan.getSlogan()));
        }
      });
    }
  }
  
  /**
   * Gets a list of active channels where the bot is allowed to talk.
   * 
   * A channel is considered active if three different users have spoken
   * during the last ten minutes.
   * 
   * @return active channel names
   */
  private Set<String> readActiveTalkChannels() {
    return jdbc.queryForList(
        "SELECT channel FROM seen "
        + "WHERE timestamp > NOW() - INTERVAL '10 minutes' "
        + "GROUP BY channel "
        + "HAVING COUNT(nick) > 2;", String.class).stream()
      .filter(channel -> botConfig.getTalkChannels().contains(channel))
      .collect(toSet());
  }
  
}

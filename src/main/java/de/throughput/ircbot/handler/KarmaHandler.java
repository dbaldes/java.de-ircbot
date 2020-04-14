package de.throughput.ircbot.handler;

import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.pircbotx.hooks.events.MessageEvent;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import de.throughput.ircbot.api.Command;
import de.throughput.ircbot.api.CommandEvent;
import de.throughput.ircbot.api.CommandHandler;
import de.throughput.ircbot.api.MessageHandler;

/**
 * Command- and message handler which updates and retrieves karma.
 * 
 * If a user utters "something++" or "something--", the karma for "something" is increased or decreased, respectively.
 * 
 * The current karma level of something can be queried by uttering "!karma something".
 */
@Component
public class KarmaHandler implements CommandHandler, MessageHandler {

  private static final Command CMD_KARMA = new Command("karma", "Usage: !karma <nick or thing> - shows the recorded karma for <nick or thing>");

  /**
   * Matches any message ending in ++ or --, ignoring pending or trailing white space; 
   * match group 1 is the key, group 2 the operator.
   * Key must be 2 to 255 characters long.
   */
  private static final Pattern PATTERN_KARMA_MESSAGE = Pattern.compile("^\\s*(.*{1,254}\\S)\\s*(\\+\\+|\\-\\-)\\s*$"); 

  private final JdbcTemplate jdbc;
  
  @Autowired
  public KarmaHandler(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }


  @Override
  public boolean onCommand(CommandEvent command) {
    if (CMD_KARMA.equals(command.getCommand())) {
      command.getArgLine()
        .map(String::toLowerCase)
        .ifPresentOrElse(
          key -> command.respond(karma(key)),
          () -> command.respond(command.getCommand().getUsage()));
      return true;
    }
    return false;
  }
  
  private String karma(String key) {
    int karma = lookupKarma(key);
    if (karma == 0) {
      return String.format("%s has neutral karma.", key);
    } else {
      return String.format("%s has a karma of %d", key, karma);
    }
  }
  
  private int lookupKarma(String key) {
    try {
      return jdbc.queryForObject("SELECT karma FROM karma WHERE key = ?", Integer.class, key);
    } catch (EmptyResultDataAccessException e) {
      return 0;
    }
  }
  
  @Override
  @Transactional
  public boolean onMessage(MessageEvent event) {
    Matcher matcher = PATTERN_KARMA_MESSAGE.matcher(event.getMessage());
    if (matcher.matches()) {
      upsert(matcher.group(1).toLowerCase(), "++".equals(matcher.group(2)) ? 1 : -1);
    }
    return false;
  }
  
  /**
   * Updates karma for the given key.
   * 
   * @param key key
   * @param karmaDelta delta
   */
  private void upsert(String key, int karmaDelta) {
    int affectedRows = jdbc.update("UPDATE karma SET karma = karma + ? WHERE key = ?", karmaDelta, key);
    if (affectedRows == 0) {
      jdbc.update("INSERT INTO karma (key, karma) VALUES (?, ?)", key, karmaDelta);
    }
  }

  @Override
  public Set<Command> getCommands() {
    return Set.of(CMD_KARMA);
  }
  
  @Override
  public boolean isOnlyTalkChannels() {
    return true;
  }
}

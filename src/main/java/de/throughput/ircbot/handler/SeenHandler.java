package de.throughput.ircbot.handler;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Optional;
import java.util.Set;
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
 * Command- and message handler which updates and retrieves last seen information.
 * <p>
 * Every user's last message is recorded in the database.
 * <p>
 * If a user says "!seen nick", the bot will inform when nick was last seen, and what they said.
 */
@Component
public class SeenHandler implements CommandHandler, MessageHandler {

    private static final Command CMD_SEEN = new Command("seen", "Usage: !seen <nick>");

    private final JdbcTemplate jdbc;

    @Autowired
    public SeenHandler(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    @Transactional
    public boolean onMessage(MessageEvent event) {
        upsert(event.getChannelSource()
                .toLowerCase(), event.getUser()
                .getNick()
                .toLowerCase(), event.getMessage());
        return false;
    }

    @Override
    @Transactional
    public boolean onCommand(CommandEvent command) {
        if (CMD_SEEN.equals(command.getCommand())) {
            command.getArgLine()
                    .map(String::toLowerCase)
                    .ifPresentOrElse(
                            nick -> command.respond(seen(command.getEvent()
                                    .getChannelSource(), nick)),
                            () -> command.respond(CMD_SEEN.getUsage()));
            return true;
        }
        return false;
    }

    /**
     * Checks whether nick was seen on channel and renders an according message.
     *
     * @param channel channel
     * @param nick    nick
     * @return message string
     */
    private String seen(String channel, String nick) {
        return lookupSeenQuote(channel, nick)
                .map(this::renderLastSeenMessage)
                .orElse(String.format("%s has never been seen on %s.", nick, channel));
    }

    /**
     * Renders a last seen message string from the given message.
     *
     * @param message last seen message
     * @return a string message
     */
    private String renderLastSeenMessage(QuoteMessage message) {
        Duration ago = Duration.ofMillis(System.currentTimeMillis() - message.getTimestamp());
        ZonedDateTime dateTime = ZonedDateTime.ofInstant(Instant.ofEpochMilli(message.getTimestamp()), ZoneOffset.UTC);

        return String.format("%s was last seen on %s %s ago, saying: %s (%s)",
                message.getNick(), message.getChannel(), renderDuration(ago),
                message.getMessage(), dateTime.format(DateTimeFormatter.ISO_INSTANT));
    }

    /**
     * Returns a human readable rendition of the given duration.
     *
     * @param duration duration
     * @return human readable string
     */
    private String renderDuration(Duration duration) {
        StringBuilder sb = new StringBuilder();

        long years = duration.toDaysPart() / 365;
        if (years > 0L) {
            sb.append(years)
                    .append("y")
                    .append(" ");
        }
        if (duration.toDaysPart() % 365 > 0) {
            sb.append(duration.toDaysPart() % 365)
                    .append("d")
                    .append(" ");
        }
        if (duration.toHoursPart() > 0) {
            sb.append(duration.toHoursPart())
                    .append("h")
                    .append(" ");
        }
        if (duration.toMinutesPart() > 0) {
            sb.append(duration.toMinutesPart())
                    .append("m")
                    .append(" ");
        }
        if (duration.toSecondsPart() > 0) {
            sb.append(duration.toSecondsPart())
                    .append("s")
                    .append(" ");
        }
        if (sb.isEmpty()) {
            sb.append("0s");
        } else {
            sb.deleteCharAt(sb.length() - 1);
        }

        return sb.toString();
    }

    /**
     * Reads the last seen message from the database.
     *
     * @param channel channel
     * @param nick    nick
     * @return quote or null if none found
     */
    private Optional<QuoteMessage> lookupSeenQuote(String channel, String nick) {
        try {
            return Optional.of(jdbc.queryForObject(
                    "SELECT EXTRACT(EPOCH FROM timestamp) * 1000, message "
                            + "FROM seen "
                            + "WHERE channel = ? AND nick = ?",
                    (rs, rowNum) -> new QuoteMessage(channel, nick, rs.getLong(1), rs.getString(2)),
                    channel, nick));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    /**
     * Updates or inserts the given last seen message for the given nick and channel.
     *
     * @param channel channel
     * @param nick    nick
     * @param message message
     */
    private void upsert(String channel, String nick, String message) {
        int affectedRows = jdbc.update("UPDATE seen SET message = ?, timestamp = NOW() WHERE channel = ? AND nick = ?",
                message, channel, nick);
        if (affectedRows == 0) {
            jdbc.update("INSERT INTO seen (channel, nick, message) VALUES (?, ?, ?)",
                    channel, nick, message);
        }
    }

    @Override
    public Set<Command> getCommands() {
        return Set.of(CMD_SEEN);
    }

    @Override
    public boolean isOnlyTalkChannels() {
        return true;
    }
}

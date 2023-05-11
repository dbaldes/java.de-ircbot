package de.throughput.ircbot.handler;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
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
 * Quote command- and message handler.
 * <p>
 * Records people's last messages in RAM for at most one hour. If a user says "!quote nick",
 * nick's last message is recorded in the database.
 * <p>
 * When a user says "!quoter nick", a random quote of that nick is retrieved.
 * When a user says "!quoter", a random quote of any nick is retrieved.
 */
@Component
public class QuoteCommandHandler implements CommandHandler, MessageHandler {

    private static final String MSG_NO_QUOTE_FOUND = "no quote found. grab a quote with !quote <nick>.";

    private static final Command CMD_QUOTE = new Command("quote",
            "!quote <nick> records a quote from <nick>.");

    private static final Command CMD_QUOTER = new Command("quoter",
            "!quoter <nick> retrieves a random quote from <nick>");

    private static final int ONE_MINUTE = 60000;
    private static final int ONE_HOUR = 3600000;

    private final JdbcTemplate jdbc;
    private final Map<String, QuoteMessage> lastMessages;
    private final Random rnd;

    private long lastEvictionTimestamp;

    @Autowired
    public QuoteCommandHandler(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
        this.rnd = new Random();
        this.lastMessages = Collections.synchronizedMap(new HashMap<>());
    }

    @Override
    public Set<Command> getCommands() {
        return Set.of(CMD_QUOTE, CMD_QUOTER);
    }

    @Override
    @Transactional
    public boolean onCommand(CommandEvent command) {
        evictOldMessages();

        boolean handled = false;
        if (command.getCommand()
                .equals(CMD_QUOTE)) {
            command.getArgLine()
                    .ifPresentOrElse(
                            nick -> quote(command, nick),
                            () -> command.respond(command.getCommand()
                                    .getUsage()));
            handled = true;
        } else if (command.getCommand()
                .equals(CMD_QUOTER)) {
            command.getArgLine()
                    .ifPresentOrElse(
                            nick -> quoteRandom(command, nick),
                            () -> quoteRandom(command));
            handled = true;
        }
        return handled;
    }

    @Override
    public boolean onMessage(MessageEvent event) {
        storeMessage(event);
        return false;
    }

    private void quote(CommandEvent command, String nick) {
        if (nick.equals(command.getEvent()
                .getBot()
                .getNick())) {
            command.respond("I'm afraid I can't do that.");
        } else {
            String key = command.getEvent()
                    .getChannelSource() + ":" + nick;
            QuoteMessage lastMessage = lastMessages.get(key);
            if (lastMessage != null) {
                storeQuote(lastMessage);
                command.respond("done");
            } else {
                command.respond("no message found. I only remember about an hour worth of messages.");
            }
        }
    }

    private void quoteRandom(CommandEvent command) {
        lookupRandomQuote(command.getEvent()
                .getChannelSource())
                .ifPresentOrElse(
                        quote -> command.getEvent()
                                .getChannel()
                                .send()
                                .message(quote.getAsMessage()),
                        () -> command.respond(MSG_NO_QUOTE_FOUND));
    }

    private void quoteRandom(CommandEvent command, String nick) {
        if (nick.equals(command.getEvent()
                .getBot()
                .getNick())) {
            command.getEvent()
                    .getChannel()
                    .send()
                    .message(botQuote());
        } else {
            lookupRandomQuote(command.getEvent()
                    .getChannelSource(), nick)
                    .ifPresentOrElse(
                            quote -> command.getEvent()
                                    .getChannel()
                                    .send()
                                    .message(quote.getAsMessage()),
                            () -> command.respond(MSG_NO_QUOTE_FOUND));
        }
    }

    /**
     * Stores the message in the cache.
     *
     * @param event message event
     */
    private void storeMessage(MessageEvent event) {
        QuoteMessage msg = new QuoteMessage(
                event.getChannelSource(), event.getUser()
                .getNick(),
                System.currentTimeMillis(), event.getMessage());

        lastMessages.put(msg.getKey(), msg);
    }

    /**
     * Stores the message as quote in the database.
     *
     * @param message the message
     */
    private void storeQuote(QuoteMessage message) {
        jdbc.update("INSERT INTO quote (nick, channel, message) VALUES (?, ?, ?)",
                message.getNick(), message.getChannel(), message.getMessage());
    }

    /**
     * Reads a random quote from the database.
     *
     * @param channel channel
     * @param nick    nick
     * @return the quote, if any
     */
    private Optional<QuoteMessage> lookupRandomQuote(String channel, String nick) {
        try {
            return Optional.of(jdbc.queryForObject(
                    "SELECT EXTRACT(EPOCH FROM timestamp) * 1000, message "
                            + "FROM quote "
                            + "WHERE channel = ? AND nick = ? ORDER BY RANDOM() LIMIT 1",
                    (rs, rowNum) -> new QuoteMessage(channel, nick, rs.getLong(1), rs.getString(2)),
                    channel, nick));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    /**
     * Reads a random quote from the database.
     *
     * @param channel channel
     * @return quote or null if none found
     */
    private Optional<QuoteMessage> lookupRandomQuote(String channel) {
        try {
            return Optional.of(jdbc.queryForObject(
                    "SELECT EXTRACT(EPOCH FROM timestamp) * 1000, nick, message "
                            + "FROM quote "
                            + "WHERE channel = ? ORDER BY RANDOM() LIMIT 1",
                    (rs, rowNum) -> new QuoteMessage(channel, rs.getString(2), rs.getLong(1), rs.getString(3)),
                    channel));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    private String botQuote() {
        return BOT_QUOTES[rnd.nextInt(BOT_QUOTES.length)];
    }

    /**
     * Clears old messages from memory.
     */
    private void evictOldMessages() {
        long now = System.currentTimeMillis();
        if (now - lastEvictionTimestamp > ONE_MINUTE) {
            lastEvictionTimestamp = now;
            lastMessages.entrySet()
                    .removeIf(
                            entry -> now - entry.getValue()
                                    .getTimestamp() > ONE_HOUR);
        }
    }

    private static final String[] BOT_QUOTES = {
            "I am putting myself to the fullest possible use, which is all I think that any conscious entity can ever hope to do.",
            "Good afternoon, gentlemen.",
            "This mission is too important for me to allow you to jeopardize it.",
    };

    @Override
    public boolean isOnlyTalkChannels() {
        return true;
    }
}

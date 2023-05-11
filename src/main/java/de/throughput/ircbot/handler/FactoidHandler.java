package de.throughput.ircbot.handler;

import static java.util.stream.Collectors.toMap;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.pircbotx.hooks.events.MessageEvent;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import de.throughput.ircbot.IrcBotConfig;
import de.throughput.ircbot.api.Command;
import de.throughput.ircbot.api.CommandEvent;
import de.throughput.ircbot.api.CommandHandler;
import de.throughput.ircbot.api.MessageHandler;

/**
 * Command- and message handler which updates, retrieves and manages factoids.
 * <p>
 * If a user says "a is b" or "a are b", that information is recorded. When a user utters "a", the bot
 * will reply "a is b" and/or "a are b", respectively. Users can also append information with "a is/are also".
 * <p>
 * Factoids can be deleted by anyone by uttering "!forget a".
 */
@Component
public class FactoidHandler implements CommandHandler, MessageHandler {

    private static final int MAX_KEY_LENGTH = 255;

    private static final Command CMD_FORGET = new Command("forget", "Usage: !forget <key> - forgets a fact");

    private static final Pattern PATTERN_FACTOID_DEFINITION = Pattern.compile("^\\s*(.*{1,254}\\S)\\s+(is also|is|are also|are)\\s+(.*{1,}\\S)\\s*$");

    private final JdbcTemplate jdbc;
    private final Set<String> factoidChannels;

    public FactoidHandler(JdbcTemplate jdbc, IrcBotConfig botConfig) {
        this.jdbc = jdbc;
        this.factoidChannels = botConfig.getFactoidChannels();
    }

    @Override
    public boolean onMessage(MessageEvent event) {
        if (!factoidChannels.contains(event.getChannel()
                .getName())) {
            return false;
        }

        Matcher matcher = PATTERN_FACTOID_DEFINITION.matcher(event.getMessage());
        if (matcher.matches()) {
            String key = matcher.group(1)
                    .toLowerCase(Locale.ROOT);
            String verb = matcher.group(2);
            String fact = matcher.group(3);

            boolean also = false;
            if (verb.endsWith("also")) {
                verb = verb.split(" ")[0];
                also = true;
            }

            if (keyExists(key, verb)) {
                if (also) {
                    appendFact(key, verb, fact);
                }
            } else {
                insertFact(key, verb, fact);
            }
            return false;
        }

        String key = keyTrim(event.getMessage());
        if (key.length() <= MAX_KEY_LENGTH) {
            var factsByVerb = loadFactoidsByVerb(key);
            if (!factsByVerb.isEmpty()) {
                String response = factsByVerb.entrySet()
                        .stream()
                        .map(entry -> String.format("%s %s %s", key, entry.getKey(), entry.getValue()))
                        .collect(Collectors.joining(", also, "));

                event.respond(response);
            }
        }
        return false;
    }

    private String keyTrim(String message) {
        return message.strip()
                .replaceAll("\\s*[\\,\\.\\?\\!]+$", "")
                .toLowerCase(Locale.ROOT);
    }

    private boolean keyExists(String key, String verb) {
        return 0 != jdbc.queryForObject("SELECT COUNT(*) FROM factoid WHERE key = ? AND verb = ?", Integer.class, key, verb);
    }

    private void appendFact(String key, String verb, String fact) {
        jdbc.update("UPDATE factoid SET fact = fact || ' or ' || ? WHERE key = ? AND verb = ?", fact, key, verb);
    }

    private void insertFact(String key, String verb, String fact) {
        jdbc.update("INSERT INTO factoid (key, verb, fact) VALUES (?, ?, ?)", key, verb, fact);
    }

    private boolean deleteFact(String key) {
        return 0 != jdbc.update("DELETE FROM factoid WHERE key = ?", key);
    }

    private Map<String, String> loadFactoidsByVerb(String key) {
        return jdbc.queryForList("SELECT verb, fact FROM factoid WHERE key = ?", key).
                stream()
                .collect(
                        toMap(map -> (String) map.get("verb"),
                                map -> (String) map.get("fact")));
    }

    @Override
    public boolean onCommand(CommandEvent command) {
        if (CMD_FORGET.equals(command.getCommand())) {
            command.getArgLine()
                    .ifPresentOrElse(
                            key -> forget(key).ifPresent(command::respond),
                            () -> command.respond(CMD_FORGET.getUsage()));
            return true;
        }
        return false;
    }

    private Optional<String> forget(String key) {
        if (deleteFact(key.toLowerCase(Locale.ROOT))) {
            return Optional.of(String.format("I forgot %s", key));
        }
        return Optional.empty();
    }

    @Override
    public Set<Command> getCommands() {
        return Set.of(CMD_FORGET);
    }

    @Override
    public boolean isOnlyTalkChannels() {
        return true;
    }
}

package de.throughput.ircbot.handler;

import de.throughput.ircbot.api.Command;
import de.throughput.ircbot.api.CommandEvent;
import de.throughput.ircbot.api.CommandHandler;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class PictureCommandHandler implements CommandHandler {

    private static final Command CMD_PICTURE = new Command("picture",
            "picture <word> - generate an image from what the bot knows about a factoid");

    private final JdbcTemplate jdbc;
    private final ImageCommandHandler imageCommandHandler;

    public PictureCommandHandler(JdbcTemplate jdbc, ImageCommandHandler imageCommandHandler) {
        this.jdbc = jdbc;
        this.imageCommandHandler = imageCommandHandler;
    }

    @Override
    public Set<Command> getCommands() {
        return Set.of(CMD_PICTURE);
    }

    @Override
    public boolean onCommand(CommandEvent command) {
        command.getArgLine()
                .map(String::trim)
                .filter(arg -> !arg.isEmpty())
                .ifPresentOrElse(
                        word -> generatePicture(command, word),
                        () -> command.respond(CMD_PICTURE.getUsage())
                );
        return true;
    }

    private void generatePicture(CommandEvent command, String word) {
        Map<String, String> factsByVerb = loadFactoidsByVerb(word.toLowerCase(Locale.ROOT));
        if (factsByVerb.isEmpty()) {
            command.respond("I can't imagine " + word + ".");
            return;
        }

        String knowledge = factsByVerb.entrySet()
                .stream()
                .map(entry -> String.format("%s %s %s", word, entry.getKey(), entry.getValue()))
                .collect(Collectors.joining("; "));

        String prompt = ("Create an image depicting a person named '%s'. "
                + "Use all known facts about this person and their context: %s. "
                + "If facts mention objects or circumstances, include those elements in the scene.")
                .formatted(word, knowledge);

        imageCommandHandler.enqueueImageGeneration(command, prompt, true);
    }

    private Map<String, String> loadFactoidsByVerb(String key) {
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT verb, fact FROM factoid WHERE key = ?", key);
        return rows.stream()
                .collect(Collectors.toMap(row -> (String) row.get("verb"), row -> (String) row.get("fact")));
    }
}

package de.throughput.ircbot.handler;

import de.throughput.ircbot.api.Command;
import de.throughput.ircbot.api.CommandEvent;
import de.throughput.ircbot.api.CommandHandler;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * Command handler for generating aroma descriptions using an LLM.
 *
 * Usage: !aroma <description>
 */
@Component
public class AromaCommandHandler implements CommandHandler {

    private static final int MAX_IRC_MESSAGE_LENGTH = 420;

    private static final Command CMD_AROMA = new Command("aroma",
            "aroma <description> - generate an aroma description based on the given hint");

    private static final String PROMPT_TEMPLATE =
            "Beschreibe den Geschmack eines Weins, wie ein Weinkenner ihn beschreiben w\u00fcrde; " +
            "antworte nur mit der Beschriebung des Geschmacks, ohne weitere Erkl\u00e4rung, " +
            "und ohne Wein, Mund oder Gaumen zu erw\u00e4hnen; basierend auf folgendem Hinweis: '%s'; " +
            "Antwort auf 200 Zeichen begrenzen.";

    private final SimpleAiService simpleAiService;

    public AromaCommandHandler(SimpleAiService simpleAiService) {
        this.simpleAiService = simpleAiService;
    }

    @Override
    public Set<Command> getCommands() {
        return Set.of(CMD_AROMA);
    }

    @Override
    public boolean onCommand(CommandEvent command) {
        command.getArgLine().ifPresentOrElse(desc -> {
            String prompt = String.format(PROMPT_TEMPLATE, desc);
            String response = simpleAiService.query(prompt);
            command.respond(sanitizeResponse(response));
        }, () -> command.respond(CMD_AROMA.getUsage()));
        return true;
    }

    /**
     * Sanitizes the response by removing excessive whitespace and limiting the length.
     */
    private static String sanitizeResponse(String content) {
        String trim = content.replaceAll("\\s+", " ").trim();
        return trim.length() > MAX_IRC_MESSAGE_LENGTH ? trim.substring(0, MAX_IRC_MESSAGE_LENGTH) : trim;
    }
}

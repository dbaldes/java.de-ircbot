package de.throughput.ircbot.handler;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.concurrent.ThreadLocalRandom;
import org.pircbotx.hooks.events.MessageEvent;
import org.springframework.stereotype.Component;

import de.throughput.ircbot.api.MessageHandler;

/** Responds to variations of the "Der Angriff ... wird alles in Ordnung bringen" phrase. */
@Component
public class InOrdnungBringenMessageHandler implements MessageHandler {

    private static final Pattern ATTACK_PHRASE = Pattern.compile(
            "\\bder\\s+angri(?:f{1,2})\\s+(.+?)\\s+"
                    + "(?:wird|wird\\s+schon|soll)\\s+"
                    + "(?:(?:das|alles|das\\s+alles)\\s+)?"
                    + "(?:schon\\s+)?(?:wieder\\s+)?"
                    + "(?:in\\s+ordnung\\s+bringen|richten|ins\\s+lot\\s+bringen)\\b[.!?]*",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    private static final List<String> RESPONSES = List.of(
            "Mein Führer ... %1$s ... der Angriff %1$s ist nicht erfolgt.",
            "%1$s ... mein Führer, der Angriff %1$s ist nicht erfolgt.",
            "Mein Führer, der Angriff %s ist leider nicht erfolgt.");

    @Override
    public boolean isOnlyTalkChannels() {
        return true;
    }

    @Override
    public boolean onMessage(MessageEvent event) {
        Matcher matcher = ATTACK_PHRASE.matcher(event.getMessage().trim());
        if (!matcher.find()) {
            return false;
        }

        String attackName = matcher.group(1).trim().replaceAll("[.!?,;:]+$", "");
        if (attackName.isEmpty()) {
            return false;
        }

        String response = RESPONSES.get(ThreadLocalRandom.current().nextInt(RESPONSES.size()));
        event.getChannel().send().message(event.getUser().getNick() + ": " + response.formatted(attackName));
        return false;
    }

}

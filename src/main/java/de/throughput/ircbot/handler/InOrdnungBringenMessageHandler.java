package de.throughput.ircbot.handler;

import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.concurrent.ThreadLocalRandom;
import org.pircbotx.hooks.events.MessageEvent;
import org.springframework.stereotype.Component;

import de.throughput.ircbot.api.MessageHandler;

/** Responds to variations of the "Der Angriff ... wird alles in Ordnung bringen" phrase. */
@Component
public class InOrdnungBringenMessageHandler implements MessageHandler {

    private static final int MESSAGE_DELAY_MILLIS = 2_000;

    private static final Pattern ATTACK_PHRASE = Pattern.compile(
            "\\b(?:der\\s+angri(?:f{1,2})\\s+|mit\\s+dem\\s+angri(?:f{1,2})\\s+)(.+?)\\s+"
                    + "(?:wird|soll)\\s+"
                    + "(?:(?:das|alles|schon|wieder)\\s+){0,4}"
                    + "(?:in\\s+ordnung\\s+(?:bringen|kommen)|richten|ins\\s+lot\\s+bringen)\\b[.!?]*",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    private static final List<String> RESPONSES = List.of(
            "Mein Führer ... %1$s ... der Angriff %1$s ist nicht erfolgt.",
            "%1$s ... mein Führer, der Angriff %1$s ist nicht erfolgt.",
            "Mein Führer, der Angriff %s ist nicht erfolgt.");

    @Override
    public boolean isOnlyTalkChannels() {
        return true;
    }

    @Override
    public boolean onMessage(MessageEvent event) {
        Optional<String> match = findAttackName(event.getMessage());
        if (match.isEmpty()) {
            return false;
        }
        String attackName = match.get();

        String response = RESPONSES.get(ThreadLocalRandom.current().nextInt(RESPONSES.size()));
        try {
            Thread.sleep(MESSAGE_DELAY_MILLIS);
            event.getChannel().send().message(event.getUser().getNick() + ": " + response.formatted(attackName));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return false;
    }

    static Optional<String> findAttackName(String message) {
        Matcher matcher = ATTACK_PHRASE.matcher(message.trim());
        if (!matcher.find()) {
            return Optional.empty();
        }

        String attackName = matcher.group(1).trim().replaceAll("[.!?,;:]+$", "");
        return attackName.isEmpty() ? Optional.empty() : Optional.of(attackName);
    }

}

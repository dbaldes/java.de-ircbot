package de.throughput.ircbot.handler;

import org.pircbotx.hooks.events.MessageEvent;
import org.springframework.stereotype.Component;

import de.throughput.ircbot.api.MessageHandler;

/**
 * Nein-Doch message handler.
 *
 * If someone utters "nein!", it will respond with "doch!" after a short delay.
 */
@Component
public class NeinDochMessageHandler implements MessageHandler {

    private static final int MESSAGE_DELAY = 1600;

    @Override
    public boolean onMessage(MessageEvent event) {
        try {
            String message = event.getMessage()
                    .trim();
            String answer = switch (message) {
                case "nein!" -> "doch!";
                case "NEIN!" -> "DOCH!";
                case "Nein!" -> "Doch!";
                default -> null;
            };
            if (answer != null) {
                Thread.sleep(MESSAGE_DELAY);
                event.getChannel()
                        .send()
                        .message(answer);
            }
        } catch (InterruptedException e) {
            Thread.currentThread()
                    .interrupt();
        }
        return false;
    }

    @Override
    public boolean isOnlyTalkChannels() {
        return true;
    }

}

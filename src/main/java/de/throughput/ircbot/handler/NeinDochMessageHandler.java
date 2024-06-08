package de.throughput.ircbot.handler;

import org.pircbotx.hooks.events.MessageEvent;
import org.springframework.stereotype.Component;

import de.throughput.ircbot.api.MessageHandler;

/**
 * Nein-Doch message handler.
 */
@Component
public class NeinDochMessageHandler implements MessageHandler {

    private static final int MESSAGE_DELAY = 1600;

    @Override
    public boolean onMessage(MessageEvent event) {
        try {
            String message = event.getMessage()
                    .trim();
            switch (message) {
                case "nein!": {
                    Thread.sleep(MESSAGE_DELAY);
                    event.getChannel()
                            .send()
                            .message("doch!");
                    return true;
                }
                case "NEIN!": {
                    Thread.sleep(MESSAGE_DELAY);
                    event.getChannel()
                            .send()
                            .message("DOCH!");
                    return true;
                }
                case "Nein!": {
                    Thread.sleep(MESSAGE_DELAY);
                    event.getChannel()
                            .send()
                            .message("Doch!");
                    return true;
                }
                default: return false;
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

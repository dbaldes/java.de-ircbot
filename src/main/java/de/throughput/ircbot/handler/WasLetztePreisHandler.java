package de.throughput.ircbot.handler;

import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.pircbotx.hooks.events.MessageEvent;
import org.springframework.stereotype.Component;

import de.throughput.ircbot.api.CommandEvent;
import de.throughput.ircbot.api.MessageHandler;

@Component
@RequiredArgsConstructor
public class WasLetztePreisHandler implements MessageHandler {

    private final CryptoCommandHandler cryptoHandler;

    @Override
    public boolean isOnlyTalkChannels() {
        return true;
    }

    @Override
    public boolean onMessage(MessageEvent event) {
        String message = event.getMessage()
                .trim()
                .toLowerCase();
        if (message.equals("was letzte preis?")) {
            return cryptoHandler.onCommand(new CommandEvent(event, CryptoCommandHandler.CMD_CRYPTO, "!", Optional.of("btc")));
        }
        return false;
    }

}

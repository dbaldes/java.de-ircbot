package de.throughput.ircbot;

import lombok.RequiredArgsConstructor;
import org.pircbotx.hooks.ListenerAdapter;
import org.pircbotx.hooks.events.PrivateMessageEvent;
import org.springframework.stereotype.Component;

/**
 * Listens for commands from private messages.
 */
@Component
@RequiredArgsConstructor
public class IrcBotControlListener extends ListenerAdapter {

    private static final String COMMAND_MSG = "MSG";
    private static final String COMMAND_PART = "PART";
    private static final String COMMAND_JOIN = "JOIN";

    private final AdminCommandRunner adminCommandRunner;

    @Override
    public void onPrivateMessage(PrivateMessageEvent event) throws Exception {
        adminCommandRunner.runPrivileged(event, () -> processCommand(event));
    }

    private static void processCommand(PrivateMessageEvent event) {
        String[] messageParts = event.getMessage().split("\\s+", 2);
        if (messageParts.length == 2) {
            String command = messageParts[0];
            String arguments = messageParts[1];

            if (COMMAND_JOIN.equals(command)) {
                event.getBot()
                        .send()
                        .joinChannel(arguments);
                event.respond("joined");
            } else if (COMMAND_PART.equals(command)) {
                event.getBot()
                        .sendRaw()
                        .rawLine("PART " + arguments);
                event.respond("parted");
            } else if (COMMAND_MSG.equals(command)) {
                String[] parts = arguments.split("\\s+", 2);
                if (parts.length == 2) {
                    String target = parts[0];
                    String msg = parts[1];
                    event.getBot()
                            .send()
                            .message(target, msg);
                } else {
                    event.respond("MSG <target> <message>");
                }
            } else {
                event.respond("unknown command");
            }
        }
    }


}

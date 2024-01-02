package de.throughput.ircbot;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import org.pircbotx.User;
import org.pircbotx.hooks.ListenerAdapter;
import org.pircbotx.hooks.events.NoticeEvent;
import org.pircbotx.hooks.events.PrivateMessageEvent;
import org.springframework.stereotype.Component;

/**
 * Listens for commands from private messages.
 */
@Component
@RequiredArgsConstructor
public class IrcBotControlListener extends ListenerAdapter {

    private static final int AUTH_TIMEOUT_MINUTES = 3;
    private static final String COMMAND_MSG = "MSG";
    private static final String COMMAND_PART = "PART";
    private static final String COMMAND_JOIN = "JOIN";
    public static final String NICKSERV = "NickServ";
    public static final String LOGGED_IN_ACCLEVEL = "3";

    private final Map<String, LocalDateTime> authedAdmins = new ConcurrentHashMap<>();
    private final Map<String, PrivateMessageEvent> queuedCommand = new ConcurrentHashMap<>();

    private final IrcBotConfig botConfig;

    @Override
    public void onPrivateMessage(PrivateMessageEvent event) throws Exception {
        User user = event.getUser();
        if (user != null) {
            if (isAuth(user
                    .getNick())) {
                processCommand(event);
            } else if (botConfig.getAdmins()
                    .contains(user
                            .getNick())) {
                // not authed; if this is an admin user, store message and ask NickServ for account info
                queuedCommand.put(user
                        .getNick(), event);
                event.getBot()
                        .send()
                        .message(NICKSERV, "ACC " + user
                                .getNick());
            }
        }
    }

    @Override
    public void onNotice(NoticeEvent event) throws Exception {
        if (event.getUserHostmask()
                .getNick()
                .equals(NICKSERV)) {
            // message should be e.g. "db ACC 3"
            String[] parts = event.getMessage()
                    .split("\\s+", 3);
            if (parts.length == 3 && "ACC".equals(parts[1])) {
                String nick = parts[0];
                String accessLevel = parts[2];
                if (LOGGED_IN_ACCLEVEL.equals(accessLevel) && botConfig.getAdmins().contains(nick)) {
                    putAuth(nick);
                    PrivateMessageEvent originalEvent = queuedCommand.remove(nick);
                    if (originalEvent != null) {
                        processCommand(originalEvent);
                    }
                } else {
                    queuedCommand.remove(nick);
                }
            }
        }
    }

    private static void processCommand(PrivateMessageEvent event) {
        String[] messageParts = event.getMessage()
                .split("\\s+", 2);
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


    /**
     * Tell if the given nick is considered an authorized admin.
     *
     * @param nick the nick
     * @return true if admin
     */
    private boolean isAuth(String nick) {
        LocalDateTime lastseen = this.authedAdmins.get(nick);

        return lastseen != null && lastseen.plusMinutes(AUTH_TIMEOUT_MINUTES)
                .isAfter(LocalDateTime.now());
    }

    /**
     * Consider the given nick an authorized admin.
     *
     * @param nick nick
     */
    private void putAuth(String nick) {
        this.authedAdmins.put(nick, LocalDateTime.now());
    }

}

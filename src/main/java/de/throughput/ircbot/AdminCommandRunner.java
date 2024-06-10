package de.throughput.ircbot;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.pircbotx.User;
import org.pircbotx.hooks.ListenerAdapter;
import org.pircbotx.hooks.events.NoticeEvent;
import org.pircbotx.hooks.types.GenericMessageEvent;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Runs commands that need admin access if the calling user is authorized.
 */
@Component
@RequiredArgsConstructor
public class AdminCommandRunner extends ListenerAdapter {

    private static final int AUTH_TIMEOUT_MINUTES = 3;
    private static final String NICKSERV = "NickServ";
    private static final String LOGGED_IN_ACCLEVEL = "3";

    private final Map<String, LocalDateTime> authedAdmins = new ConcurrentHashMap<>();
    private final Map<String, QueuedCommand> queuedCommand = new ConcurrentHashMap<>();

    private final IrcBotConfig botConfig;

    /**
     * Run an admin command.
     *
     * The user is checked against the configured admin list and must be authorized to NickServ.
     *
     * @param event the event triggering the command
     * @param execution the implementation of the command
     */
    public void runPrivileged(GenericMessageEvent event, Runnable execution) {
        User user = event.getUser();
        if (user != null) {
            if (isBypassAuth(event) || isAuth(user.getNick())) {
                execution.run();
            } else if (botConfig.getAdmins().contains(user.getNick())) {
                // not authed; if this is an admin user, store execution and ask NickServ for account info
                // the response will be processed in onNotice()
                queuedCommand.put(user.getNick(), new QueuedCommand(event, execution));
                event.getBot()
                        .send()
                        .message(NICKSERV, "ACC " + user.getNick());
            } else {
                event.respond("not authorized.");
            }
        }
    }

    @Override
    public void onNotice(NoticeEvent event) throws Exception {
        if (event.getUserHostmask()
                .getNick()
                .equals(NICKSERV)) {
            // message should be e.g. "db ACC 3"
            String[] parts = event.getMessage().split("\\s+", 3);
            if (parts.length == 3 && "ACC".equals(parts[1])) {
                String nick = parts[0];
                String accessLevel = parts[2];
                QueuedCommand command = queuedCommand.remove(nick);
                if (LOGGED_IN_ACCLEVEL.equals(accessLevel) && botConfig.getAdmins().contains(nick)) {
                    putAuth(nick);
                    if (command != null) {
                        command.getExecution().run();
                    }
                } else {
                    command.getEvent().respond("not authorized.");
                }
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
        LocalDateTime lastSeen = this.authedAdmins.get(nick);

        return lastSeen != null && lastSeen.plusMinutes(AUTH_TIMEOUT_MINUTES).isAfter(LocalDateTime.now());
    }

    /**
     * Tells if we're bypassing NickServ authentication for test setups.
     *
     * @return true if on bypassing
     */
    private boolean isBypassAuth(GenericMessageEvent event) {
        return botConfig.isTestMode();
    }

    /**
     * Consider the given nick an authorized admin.
     *
     * @param nick nick
     */
    private void putAuth(String nick) {
        this.authedAdmins.put(nick, LocalDateTime.now());
    }

    @Getter
    @RequiredArgsConstructor
    private static class QueuedCommand {

        private final GenericMessageEvent event;
        private final Runnable execution;
    }
}

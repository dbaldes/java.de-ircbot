package de.throughput.ircbot;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.commons.lang3.tuple.Pair;
import org.pircbotx.hooks.ListenerAdapter;
import org.pircbotx.hooks.events.MessageEvent;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import de.throughput.ircbot.api.Command;
import de.throughput.ircbot.api.CommandEvent;
import de.throughput.ircbot.api.CommandHandler;
import de.throughput.ircbot.api.MessageHandler;

/**
 * Listens to the conversation, executes commands and feeds the message handlers.
 */
@Component
public class IrcBotConversationListener extends ListenerAdapter {

    private static final Set<String> COMMAND_PREFIXES = Set.of("!", "*");

    private final IrcBotConfig botConfig;
    private final Map<String, Pair<Command, CommandHandler>> commandHandlersByCommand;
    private final List<MessageHandler> messageHandlers;
    private final UserRateLimiter rateLimiter;

    @Autowired
    public IrcBotConversationListener(
            IrcBotConfig botConfig,
            UserRateLimiter rateLimiter,
            List<CommandHandler> commandHandlers,
            List<MessageHandler> messageHandlers) {
        this.botConfig = botConfig;
        this.rateLimiter = rateLimiter;
        commandHandlersByCommand = new LinkedHashMap<>();
        commandHandlers.forEach(handler -> {
            handler.getCommands()
                    .forEach(command -> {
                        commandHandlersByCommand.put(command.getCommand(), Pair.of(command, handler));
                    });
        });
        this.messageHandlers = messageHandlers;
    }

    @Override
    public void onMessage(MessageEvent event) throws Exception {
        String nick = event.getUser()
                .getNick();
        if (nick.equals(event.getBot()
                .getNick())) {
            return; // don't listen to ourselves
        }
        if (rateLimiter.ignore(nick)) {
            return;
        }

        boolean rateLimitExceeded = rateLimiter.limit(nick);

        String channel = event.getChannel()
                .getName();
        String message = event.getMessage()
                .trim();
        Optional<String> commandPrefix = commandPrefix(message);
        if (commandPrefix.isPresent() && message.length() > 1 && isTalkChannel(channel)) {

            if (rateLimitExceeded) {
                event.respond("take a break!");
                return;
            }
            String[] parts = message.substring(1)
                    .trim()
                    .split("\\s", 2);
            String command = parts[0];
            String argLine = parts.length > 1 ? parts[1] : null;

            List<Pair<Command, CommandHandler>> matches = commandHandlersByCommand.entrySet()
                    .stream()
                    .filter(entry -> entry.getKey()
                            .startsWith(command))
                    .map(Entry::getValue)
                    .sorted((a, b) -> a.getLeft()
                            .getCommand()
                            .compareTo(b.getLeft()
                                    .getCommand()))
                    .collect(Collectors.toList());

            if (matches.size() == 1) {
                handleCommand(commandPrefix.get(), event, argLine, matches.get(0));
            } else if (matches.size() > 1) {
                // is there an exact match?
                matches.stream()
                        .filter(entry -> entry.getKey()
                                .getCommand()
                                .equals(command))
                        .findFirst()
                        .ifPresentOrElse(
                                match -> handleCommand(commandPrefix.get(), event, argLine, match),
                                () -> event.respond("possible matches: " + possibleMatches(commandPrefix.get(), matches)));
            }
        } else if (rateLimitExceeded) {
            return;
        }

        for (MessageHandler handler : messageHandlers) {
            if (!handler.isOnlyTalkChannels() || isTalkChannel(channel)) {
                if (handler.onMessage(event)) {
                    return;
                }
            }
        }
    }

    private Optional<String> commandPrefix(String message) {
        return COMMAND_PREFIXES.stream()
                .filter(message::startsWith)
                .findFirst();
    }

    private String possibleMatches(String commandPrefix, List<Pair<Command, CommandHandler>> matches) {
        return matches.stream()
                .map(match -> commandPrefix + match.getLeft()
                        .getCommand())
                .collect(Collectors.joining(", "));
    }

    private void handleCommand(String commandPrefix, MessageEvent event, String argLine, Pair<Command, CommandHandler> match) {
        CommandEvent cmdEvent = new CommandEvent(event, match.getLeft(), commandPrefix, Optional.ofNullable(argLine));
        match.getRight()
                .onCommand(cmdEvent);
    }

    private boolean isTalkChannel(String channel) {
        return botConfig.getTalkChannels()
                .contains(channel);
    }

}

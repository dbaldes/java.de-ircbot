package de.throughput.ircbot;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

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
  
  private static final String COMMAND_PREFIX = "!";

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
      handler.getCommands().forEach(command -> {
        commandHandlersByCommand.put(command.getCommand(), Pair.of(command, handler));
      });
    });
    this.messageHandlers = messageHandlers;
  }

  @Override
  public void onMessage(MessageEvent event) throws Exception {
    String nick = event.getUser().getNick();
    if (nick.equals(event.getBot().getNick())) {
      return; // don't listen to ourselves
    }
    
    String channel = event.getChannel().getName();
    String message = event.getMessage().trim();
    if (message.startsWith(COMMAND_PREFIX) && isTalkChannel(channel)) {
      if (rateLimiter.limit(nick)) {
        event.respond("hold it!");
      } else {
        String[] parts = message.substring(1).trim().split("\\s", 2);
        String command = parts[0];
        String argLine = parts.length > 1 ? parts[1] : null;

        Pair<Command, CommandHandler> handler = commandHandlersByCommand.get(command);
        if (handler != null) {
          CommandEvent cmdEvent = new CommandEvent(event, handler.getLeft(), Optional.ofNullable(argLine));
          handler.getRight().onCommand(cmdEvent);
        }
      }
    }
    
    for (MessageHandler handler : messageHandlers) {
      if (!handler.isOnlyTalkChannels() || isTalkChannel(channel)) {
        if (handler.onMessage(event)) {
          return;
        }
      }
    }
  }

  private boolean isTalkChannel(String channel) {
    return botConfig.getTalkChannels().contains(channel);
  }

}

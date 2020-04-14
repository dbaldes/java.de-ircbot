package de.throughput.ircbot;

import java.math.BigInteger;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.pircbotx.hooks.ListenerAdapter;
import org.pircbotx.hooks.events.NoticeEvent;
import org.pircbotx.hooks.events.PrivateMessageEvent;
import org.pircbotx.hooks.types.GenericMessageEvent;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Listens for commands from private messages.
 */
@Component
public class IrcBotControlListener extends ListenerAdapter {

  private static final int AUTH_TIMEOUT_MINUTES = 3;
  private static final String COMMAND_MSG = "MSG";
  private static final String COMMAND_PART = "PART";
  private static final String COMMAND_JOIN = "JOIN";
  private static final String COMMAND_AUTH = "AUTH";

  private Map<String, BigInteger> admins = new HashMap<>();
  private Map<String, LocalDateTime> authedAdmins = new ConcurrentHashMap<String, LocalDateTime>();
  
  @Autowired
  public IrcBotControlListener(@Value("#{'${ircbot.admins}'.split(',')}") List<String> adminList) {
    for (String admin : adminList) {
      String[] parts = admin.split(":");
      if (parts.length == 2) {
        String admnick = parts[0];
        BigInteger pwhash = new BigInteger(parts[1], 16);
        
        this.admins.put(admnick, pwhash);
      }
    }
  }
  
  @Override
  public void onPrivateMessage(PrivateMessageEvent event) throws Exception {
    
    String[] parts = event.getMessage().split("\\s+", 2);
    if (parts.length == 2) {
      String command = parts[0];
      String arguments = parts[1];
      
      if (COMMAND_AUTH.equals(command)) {
        BigInteger pwhash = Util.sha256(arguments.trim());
        System.out.printf("AUTH %s with pw hash %s\n", event.getUser().getNick(), pwhash.toString(16));
        if (pwhash.equals(this.admins.get(event.getUser().getNick()))) {
          System.out.printf("AUTH %s successful\n", event.getUser().getNick());
          putAuth(event.getUser().getNick());
          event.respond("yes");
        } else {
          event.respond("no");
        }
        return;
      } else if (isAuth(event.getUser().getNick())) {

        if (COMMAND_JOIN.equals(command)) {
          event.getBot().send().joinChannel(arguments);
          event.respond("joined");
        } else if (COMMAND_PART.equals(command)) {
          event.getBot().sendRaw().rawLine("PART " + arguments);
          event.respond("parted");
        } else if (COMMAND_MSG.equals(command)) {
          parts = arguments.split("\\s+", 2);
          if (parts.length == 2) {
            String target = parts[0];
            String msg = parts[1];
            event.getBot().send().message(target, msg);
          } else {
            event.respond("MSG <target> <message>");
          }
        }
        return;
      }
    }
    this.onUnrecognizedPrivateMessage(event);
  }
  
  @Override
  public void onNotice(NoticeEvent event) throws Exception {
    if (event.getChannel() == null) {
      onUnrecognizedPrivateMessage(event);
    }
  }

  /**
   * Handle a private message which is not an authorized command and not from an admin.
   * 
   * @param event message event
   */
  private void onUnrecognizedPrivateMessage(GenericMessageEvent event) {
    // forward to all authorized admins
    this.authedAdmins.entrySet().stream()
      .filter(e -> e.getValue().plusMinutes(AUTH_TIMEOUT_MINUTES).isAfter(LocalDateTime.now()))
      .map(e -> e.getKey())
      .filter(admin -> !admin.equals(event.getUser().getNick()))
      .forEach(admin -> {
        event.getBot().send().message(admin, String.format("%s: %s", event.getUser().getNick(), event.getMessage()));
      });
  }
  
  /**
   * Tell if the given nick is considered an authorized admin.
   * 
   * @param nick nick
   * @return true if admin
   */
  private boolean isAuth(String nick) {
    LocalDateTime lastseen = this.authedAdmins.get(nick);
    
    if (lastseen != null && lastseen.plusMinutes(AUTH_TIMEOUT_MINUTES).isAfter(LocalDateTime.now())) {
      putAuth(nick);
      return true;
    }
    return false;
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

package de.throughput.ircbot.handler;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Component;

import de.throughput.ircbot.api.Command;
import de.throughput.ircbot.api.CommandEvent;
import de.throughput.ircbot.api.CommandHandler;

/**
 * Flip command handler.
 * 
 * !flip <text> - flips <text> over in rage.
 */
@Component
public class FlipCommandHandler implements CommandHandler {

  private static String ALPHABET = "abcdefghijklmnorstvwxyz"
      + "ABCDEFGHIJKLMNOPQRSTUVWXYZ"
      + "0123456789"
      + "?!.,(<[{'_\\&";

  private static String ALPHABET_FLIPPED = "\u0250q\u0254p\u01dd\u025f\u0183\u0265\u1d09\u027e\u029e\u05df\u026fuo\u0279s\u0287\u028c\u028dx\u028ez"
      + "\u2200B\u0186D\u018e\u2132\u05e4HI\u017fK\u02e5WNO\u0500Q\u042fS\u2534\u2229\u039bMX\u2144Z"
      + "0\u01962\u01904\u03db9L86"
      + "\u00bf\u00a1\u02d9')>]},\u203e/\u214b";
  
  private final static Map<Character, Character> REPLACEMENTS;
  
  static {
    REPLACEMENTS = new HashMap<>();
    for (int i = 0; i < ALPHABET.length(); ++ i) {
      char c = ALPHABET.charAt(i);
      char cr = ALPHABET_FLIPPED.charAt(i);
      
      REPLACEMENTS.put(c, cr);
      REPLACEMENTS.put(cr, c);
    }
  }
  
  private static final String ASCII_FLIPPER = "(\u256f\u00b0\u25a1\u00b0\uff09\u256f \ufe35 ";

  private static final Command CMD_FLIP = new Command("flip", "flip <text> - flips <text> over in rage.");

  @Override
  public Set<Command> getCommands() {
    return Set.of(CMD_FLIP);
  }
  
  @Override
  public boolean onCommand(CommandEvent command) {
    command.getArgLine().ifPresentOrElse(
        text -> command.getEvent().getChannel().send().message(ASCII_FLIPPER + flip(text)),
        () -> command.respond(CMD_FLIP.getUsage()));
    return false;
  }

  private static String flip(String text) {
    StringBuilder sb = new StringBuilder(text.length());
    for (int i = text.length() - 1; i >= 0; --i) {
      char c = text.charAt(i);
      Character flipped = REPLACEMENTS.get(c);
      sb.append(flipped != null ? flipped : c);
    }
    return sb.toString();
  }

}

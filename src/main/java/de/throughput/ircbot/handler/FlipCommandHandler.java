package de.throughput.ircbot.handler;

import static java.util.Map.entry;

import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Component;

import de.throughput.ircbot.api.Command;
import de.throughput.ircbot.api.CommandEvent;
import de.throughput.ircbot.api.CommandHandler;

/**
 * Flip command handler.
 * 
 * !flip <text> - flip <text> over in rage.
 */
@Component
public class FlipCommandHandler implements CommandHandler {

  private static Map<Character, Character> REPLACEMENTS = Map.ofEntries(
      entry('a', '\u0250'),
      entry('b', 'q'),
      entry('c', '\u0254'),
      entry('d', 'p'),
      entry('e', '\u01dd'),
      entry('f', '\u025f'),
      entry('g', '\u0183'),
      entry('h', '\u0265'),
      entry('i', '\u1d09'),
      entry('j', '\u027e'),
      entry('k', '\u029e'),
      entry('l', '\u05df'),
      entry('m', '\u026f'),
      entry('n', 'u'),
      entry('o', 'o'),
      entry('p', 'd'),
      entry('q', 'b'),
      entry('r', '\u0279'),
      entry('s', 's'),
      entry('t', '\u0287'),
      entry('u', 'n'),
      entry('v', '\u028c'),
      entry('w', '\u028d'),
      entry('x', 'x'),
      entry('y', '\u028e'),
      entry('z', 'z'),
      entry('?', '\u00bf'),
      entry('.', '\u02d9'),
      entry(',', '\''),
      entry('(', ')'),
      entry('<', '>'),
      entry('[', ']'),
      entry('{', '}'),
      entry('\'', ','),
      entry('_', '\u203e'),
      entry('\u0250', 'a'),
      entry('\u0254', 'c'),
      entry('\u01dd', 'e'),
      entry('\u025f', 'f'),
      entry('\u0183', 'g'),
      entry('\u0265', 'h'),
      entry('\u1d09', 'i'),
      entry('\u027e', 'j'),
      entry('\u029e', 'k'),
      entry('\u05df', 'l'),
      entry('\u026f', 'm'),
      entry('\u0279', 'r'),
      entry('\u0287', 't'),
      entry('\u028c', 'v'),
      entry('\u028d', 'w'),
      entry('\u028e', 'y'),
      entry('\u00bf', '?'),
      entry('\u02d9', '.'),
      entry(')', '('),
      entry('>', '<'),
      entry(']', '['),
      entry('}', '{'),
      entry('\u203e', '_'));
  
  private static final String FLIPPER = "(\u256f\u00b0\u25a1\u00b0\uff09\u256f \ufe35 ";


  private static final Command CMD_FLIP = new Command("flip", "flip <text> - flip <text> over in rage.");

  @Override
  public Set<Command> getCommands() {
    return Set.of(CMD_FLIP);
  }
  
  @Override
  public boolean onCommand(CommandEvent command) {
    command.getArgLine().ifPresentOrElse(
        text -> command.respond(FLIPPER + flip(text)),
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

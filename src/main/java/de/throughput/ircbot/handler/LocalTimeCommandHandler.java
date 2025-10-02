package de.throughput.ircbot.handler;

import de.throughput.ircbot.api.Command;
import de.throughput.ircbot.api.CommandEvent;
import de.throughput.ircbot.api.CommandHandler;
import java.text.Normalizer;
import java.text.Normalizer.Form;
import java.time.DateTimeException;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import org.springframework.stereotype.Component;

/**
 * Command that displays the local time for a city or timezone.
 */
@Component
public class LocalTimeCommandHandler implements CommandHandler {

    private static final Command CMD_LOCALTIME = new Command("localtime",
            "localtime <city or timezone> - shows the current time in a given city or time zone.");

    private static final DateTimeFormatter OUTPUT_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm z (XXX)");

    private static final int FUZZY_THRESHOLD = 2;

    private static final Map<String, List<String>> CITY_INDEX = buildCityIndex();

    @Override
    public Set<Command> getCommands() {
        return Set.of(CMD_LOCALTIME);
    }

    @Override
    public boolean onCommand(CommandEvent command) {
        command.getArgLine()
                .map(String::trim)
                .filter(arg -> !arg.isEmpty())
                .ifPresentOrElse(
                        query -> resolveZoneId(query)
                                .map(this::formatTime)
                                .ifPresentOrElse(command::respond, () -> command.respond("timezone could not be determined")),
                        () -> command.respond(CMD_LOCALTIME.getUsage()));
        return true;
    }

    private Optional<ZoneId> resolveZoneId(String input) {
        Optional<ZoneId> direct = parseDirect(input);
        if (direct.isPresent()) {
            return direct;
        }

        Optional<ZoneId> city = lookupCity(input);
        if (city.isPresent()) {
            return city;
        }

        return fuzzyLookup(input);
    }

    private Optional<ZoneId> parseDirect(String input) {
        String trimmed = input.trim();
        if (trimmed.isEmpty()) {
            return Optional.empty();
        }

        try {
            return Optional.of(ZoneId.of(trimmed));
        } catch (DateTimeException ex) {
            // ignored
        }

        String upper = trimmed.toUpperCase(Locale.ROOT);
        String shortId = ZoneId.SHORT_IDS.get(upper);
        if (shortId != null) {
            try {
                return Optional.of(ZoneId.of(shortId));
            } catch (DateTimeException ex) {
                // ignored
            }
        }

        if (upper.startsWith("UTC") || upper.startsWith("GMT")) {
            String offsetPart = trimmed.substring(3);
            if (offsetPart.isEmpty()) {
                return Optional.of(ZoneOffset.UTC);
            }
            try {
                return Optional.of(ZoneOffset.of(offsetPart));
            } catch (DateTimeException ex) {
                // ignored
            }
        }

        return Optional.empty();
    }

    private Optional<ZoneId> lookupCity(String input) {
        String normalized = normalize(input, false);
        if (!normalized.isEmpty()) {
            List<String> zones = CITY_INDEX.get(normalized);
            if (zones != null && !zones.isEmpty()) {
                return Optional.of(ZoneId.of(zones.get(0)));
            }
        }

        String normalizedNoSpace = normalize(input, true);
        if (!normalizedNoSpace.isEmpty()) {
            List<String> zones = CITY_INDEX.get(normalizedNoSpace);
            if (zones != null && !zones.isEmpty()) {
                return Optional.of(ZoneId.of(zones.get(0)));
            }
        }

        return Optional.empty();
    }

    private Optional<ZoneId> fuzzyLookup(String input) {
        String normalized = normalize(input, false);
        if (normalized.isEmpty()) {
            normalized = normalize(input, true);
        }
        if (normalized.isEmpty()) {
            return Optional.empty();
        }

        int bestDistance = Integer.MAX_VALUE;
        String bestZone = null;

        for (Map.Entry<String, List<String>> entry : CITY_INDEX.entrySet()) {
            int distance = levenshteinDistance(normalized, entry.getKey());
            if (distance <= FUZZY_THRESHOLD) {
                String candidate = entry.getValue().get(0);
                if (bestZone == null || distance < bestDistance || (distance == bestDistance && candidate.compareTo(bestZone) < 0)) {
                    bestDistance = distance;
                    bestZone = candidate;
                }
            }
        }

        if (bestZone == null) {
            return Optional.empty();
        }
        return Optional.of(ZoneId.of(bestZone));
    }

    private String formatTime(ZoneId zoneId) {
        ZonedDateTime now = ZonedDateTime.now(zoneId);
        return String.format("Current time in %s: %s", zoneId.getId(), OUTPUT_FORMATTER.format(now));
    }

    private static Map<String, List<String>> buildCityIndex() {
        Map<String, SortedSet<String>> builder = new HashMap<>();
        for (String zoneId : ZoneId.getAvailableZoneIds()) {
            int slash = zoneId.lastIndexOf('/');
            if (slash < 0) {
                continue;
            }

            String segment = zoneId.substring(slash + 1);
            Set<String> variants = new HashSet<>();
            String normalized = normalize(segment, false);
            if (!normalized.isEmpty()) {
                variants.add(normalized);
            }
            String normalizedNoSpace = normalize(segment, true);
            if (!normalizedNoSpace.isEmpty()) {
                variants.add(normalizedNoSpace);
            }

            for (String variant : variants) {
                builder.computeIfAbsent(variant, key -> new TreeSet<>()).add(zoneId);
            }
        }

        Map<String, List<String>> index = new HashMap<>();
        for (Map.Entry<String, SortedSet<String>> entry : builder.entrySet()) {
            index.put(entry.getKey(), List.copyOf(entry.getValue()));
        }
        return Map.copyOf(index);
    }

    private static String normalize(String value, boolean removeSpaces) {
        String decomposed = Normalizer.normalize(value, Form.NFD);
        StringBuilder sb = new StringBuilder(decomposed.length());
        boolean lastWasSpace = false;
        for (int i = 0; i < decomposed.length(); i++) {
            char c = decomposed.charAt(i);
            if (Character.getType(c) == Character.NON_SPACING_MARK) {
                continue;
            }
            if (c == '_' || c == '-' || Character.isWhitespace(c)) {
                if (!removeSpaces && !lastWasSpace) {
                    sb.append(' ');
                    lastWasSpace = true;
                }
                continue;
            }
            if (Character.isLetterOrDigit(c)) {
                sb.append(Character.toLowerCase(c));
                lastWasSpace = false;
            }
        }
        if (removeSpaces) {
            return sb.toString();
        }
        int length = sb.length();
        if (length > 0 && sb.charAt(length - 1) == ' ') {
            sb.setLength(length - 1);
        }
        return sb.toString();
    }

    private static int levenshteinDistance(String a, String b) {
        if (a.equals(b)) {
            return 0;
        }
        if (a.isEmpty()) {
            return b.length();
        }
        if (b.isEmpty()) {
            return a.length();
        }

        int[] prev = new int[b.length() + 1];
        int[] curr = new int[b.length() + 1];

        for (int j = 0; j <= b.length(); j++) {
            prev[j] = j;
        }

        for (int i = 1; i <= a.length(); i++) {
            curr[0] = i;
            char ca = a.charAt(i - 1);
            for (int j = 1; j <= b.length(); j++) {
                char cb = b.charAt(j - 1);
                int cost = ca == cb ? 0 : 1;
                curr[j] = Math.min(Math.min(curr[j - 1] + 1, prev[j] + 1), prev[j - 1] + cost);
            }
            int[] tmp = prev;
            prev = curr;
            curr = tmp;
        }
        return prev[b.length()];
    }
}

package de.throughput.ircbot.handler;

import com.google.gson.Gson;
import de.throughput.ircbot.Util;
import de.throughput.ircbot.api.Command;
import de.throughput.ircbot.api.CommandEvent;
import de.throughput.ircbot.api.CommandHandler;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.DateTimeException;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import net.iakovlev.timeshape.TimeZoneEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class LocalTimeCommandHandler implements CommandHandler {

    private static final Logger LOG = LoggerFactory.getLogger(LocalTimeCommandHandler.class);

    private static final Command CMD_LOCALTIME = new Command("localtime",
            "localtime <city or timezone> - show the current time in the given city or time zone.");

    private static final String GEOCODING_URL = "http://api.openweathermap.org/geo/1.0/direct?q=%s&appid=%s";

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");

    private static final Map<String, ZoneId> NORMALIZED_ZONE_IDS;

    private static final Map<String, ZoneId> ADDITIONAL_ALIASES;

    static {
        NORMALIZED_ZONE_IDS = Map.copyOf(ZoneId.getAvailableZoneIds()
                .stream()
                .collect(Collectors.toMap(id -> id.toLowerCase(Locale.ROOT), ZoneId::of, (existing, replacement) -> existing)));

        ADDITIONAL_ALIASES = Map.of(
                "CEST", ZoneId.of("Europe/Berlin")
        );
    }

    private final String apiKey;

    private final HttpClient httpClient = HttpClient.newHttpClient();

    private final Gson gson = new Gson();

    private final TimeZoneEngine timeZoneEngine;

    public LocalTimeCommandHandler(@Value("${openweathermap.apiKey}") String apiKey) {
        this.apiKey = apiKey;
        this.timeZoneEngine = TimeZoneEngine.initialize();
    }

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
                        query -> determineTimeZone(query)
                                .thenApply(optionalZone -> optionalZone
                                        .map(this::formatResponse)
                                        .orElse("timezone could not be determined"))
                                .exceptionally(ex -> {
                                    LOG.warn("Failed to determine time zone for '{}': {}", query, ex.getMessage());
                                    LOG.debug("Exception while determining time zone", ex);
                                    return "timezone could not be determined";
                                })
                                .thenAccept(command::respond),
                        () -> command.respond(command.getCommand().getUsage())
                );
        return true;
    }

    private CompletableFuture<Optional<ZoneId>> determineTimeZone(String query) {
        Optional<ZoneId> directMatch = findZoneId(query);
        if (directMatch.isPresent()) {
            return CompletableFuture.completedFuture(directMatch);
        }

        return getLocation(query)
                .thenApply(optionalLocation -> optionalLocation.flatMap(this::resolveTimeZone));
    }

    private Optional<ZoneId> findZoneId(String query) {
        String trimmed = query.trim();
        if (trimmed.isEmpty()) {
            return Optional.empty();
        }

        try {
            return Optional.of(ZoneId.of(trimmed));
        } catch (DateTimeException ignored) {
            // fall through
        }

        try {
            return Optional.of(ZoneId.of(trimmed, ZoneId.SHORT_IDS));
        } catch (DateTimeException ignored) {
            // fall through
        }

        ZoneId alias = ADDITIONAL_ALIASES.get(trimmed.toUpperCase(Locale.ROOT));
        if (alias != null) {
            return Optional.of(alias);
        }

        ZoneId normalized = NORMALIZED_ZONE_IDS.get(trimmed.toLowerCase(Locale.ROOT));
        if (normalized != null) {
            return Optional.of(normalized);
        }

        return Optional.empty();
    }

    private CompletableFuture<Optional<LocationResponse>> getLocation(String location) {
        if (apiKey == null || apiKey.isBlank()) {
            LOG.warn("OpenWeatherMap API key is not configured");
            return CompletableFuture.completedFuture(Optional.empty());
        }

        URI uri = URI.create(String.format(GEOCODING_URL, Util.urlEnc(location), apiKey));
        HttpRequest request = HttpRequest.newBuilder(uri)
                .header("Accept", "application/json")
                .GET()
                .build();

        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(this::processGeocodingResponse)
                .exceptionally(ex -> {
                    LOG.warn("Failed to fetch geocoding data for '{}': {}", location, ex.getMessage());
                    LOG.debug("Exception while fetching geocoding data", ex);
                    return Optional.empty();
                });
    }

    private Optional<LocationResponse> processGeocodingResponse(HttpResponse<String> response) {
        if (response.statusCode() != 200) {
            LOG.warn("Geocoding request returned status {}", response.statusCode());
            return Optional.empty();
        }

        LocationResponse[] results = gson.fromJson(response.body(), LocationResponse[].class);
        if (results == null || results.length == 0) {
            return Optional.empty();
        }

        return Optional.of(results[0]);
    }

    private Optional<ZoneId> resolveTimeZone(LocationResponse location) {
        return timeZoneEngine.query(location.lat(), location.lon());
    }

    private String formatResponse(ZoneId zoneId) {
        String time = ZonedDateTime.now(zoneId).format(TIME_FORMATTER);
        return "Current time in " + zoneId.getId() + ": " + time;
    }

    private static class LocationResponse {

        private double lat;
        private double lon;

        public double lat() {
            return lat;
        }

        public double lon() {
            return lon;
        }
    }
}


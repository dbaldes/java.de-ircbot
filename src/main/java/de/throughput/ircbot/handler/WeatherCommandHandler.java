package de.throughput.ircbot.handler;

import com.google.gson.Gson;
import de.throughput.ircbot.Util;
import de.throughput.ircbot.api.Command;
import de.throughput.ircbot.api.CommandEvent;
import de.throughput.ircbot.api.CommandHandler;
import lombok.Getter;
import lombok.Setter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/**
 * Command handler for retrieving weather information from OpenWeatherMap.
 */
@Component
public class WeatherCommandHandler implements CommandHandler {

    private static final Command CMD_WEATHER = new Command("weather",
            "weather <location> - get weather information for a location. example: !weather Zurich, Switzerland");

    private static final String GEOCODING_URL = "http://api.openweathermap.org/geo/1.0/direct?q=%s&appid=%s";
    private static final String WEATHER_URL = "http://api.openweathermap.org/data/2.5/weather?lat=%f&lon=%f&units=imperial&appid=%s";

    private final String apiKey;
    private final JdbcTemplate jdbc;
    private final HttpClient httpClient;

    @Autowired
    public WeatherCommandHandler(@Value("${openweathermap.apiKey}") String apiKey, JdbcTemplate jdbc) {
        this(apiKey, jdbc, HttpClient.newHttpClient());
    }

    WeatherCommandHandler(String apiKey, JdbcTemplate jdbc, HttpClient httpClient) {
        this.apiKey = apiKey;
        this.jdbc = jdbc;
        this.httpClient = httpClient;
    }

    @Override
    public Set<Command> getCommands() {
        return Set.of(CMD_WEATHER);
    }

    @Override
    public boolean onCommand(CommandEvent command) {
        command.getArgLine()
                .map(String::trim)
                .filter(location -> !location.isEmpty())
                .ifPresentOrElse(
                        location -> getWeatherInfo(command, location, true),
                        () -> lookupLastLocation(command)
                                .ifPresentOrElse(
                                        location -> getWeatherInfo(command, location, false),
                                        () -> command.respond("No weather location stored for you in this channel. Use !weather <location> first.")
                                )
                );
        return true;
    }

    private void getWeatherInfo(CommandEvent command, String location, boolean storeLocation) {
        getLocation(location).thenCompose(response -> {
            if (response == null) {
                return CompletableFuture.completedFuture("Location not found");
            }
            if (storeLocation) {
                storeLastLocation(command, location);
            }
            return getWeather(response);
        }).thenAccept(command::respond);
    }

    private Optional<String> lookupLastLocation(CommandEvent command) {
        try {
            return Optional.of(jdbc.queryForObject(
                    "SELECT location FROM weather_location WHERE channel = ? AND nick = ?",
                    String.class,
                    channel(command), nick(command)));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    private void storeLastLocation(CommandEvent command, String location) {
        jdbc.update("INSERT INTO weather_location (channel, nick, location) VALUES (?, ?, ?) "
                        + "ON CONFLICT (channel, nick) DO UPDATE SET location = EXCLUDED.location",
                channel(command), nick(command), location);
    }

    private String channel(CommandEvent command) {
        return command.getEvent().getChannel().getName().toLowerCase(Locale.ROOT);
    }

    private String nick(CommandEvent command) {
        return command.getEvent().getUser().getNick().toLowerCase(Locale.ROOT);
    }

    private CompletableFuture<LocationResponse> getLocation(String location) {
        URI uri = URI.create(String.format(GEOCODING_URL, Util.urlEnc(location), apiKey));
        HttpRequest request = HttpRequest.newBuilder(uri)
                .header("Accept", "application/json")
                .GET()
                .build();

        return httpClient
                .sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(this::processGeocodingResponse);
    }

    private LocationResponse processGeocodingResponse(HttpResponse<String> httpResponse) {
        var jsonArray = new Gson().fromJson(httpResponse.body(), LocationResponse[].class);
        if (jsonArray.length == 0) {
            return null;
        }
        return jsonArray[0];
    }

    private CompletableFuture<String> getWeather(LocationResponse location) {
        URI uri = URI.create(String.format(WEATHER_URL, location.getLat(), location.getLon(), apiKey));
        HttpRequest request = HttpRequest.newBuilder(uri)
                .header("Accept", "application/json")
                .GET()
                .build();

        return httpClient
                .sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> processWeatherResponse(location, response));
    }

    private String processWeatherResponse(LocationResponse location, HttpResponse<String> httpResponse) {
        var json = new Gson().fromJson(httpResponse.body(), Map.class);
        var main = (Map<String, Object>) json.get("main");
        var weatherList = (List<Map<String,Object>>) json.get("weather");
        var weather = weatherList.get(0);

        double tempF = ((Number) main.get("temp")).doubleValue();
        double tempC = (tempF - 32) * 5.0 / 9.0;
        int humidity = ((Number) main.get("humidity")).intValue();
        String description = (String) weather.get("description");

        return String.format("%s, %s: %.1f°C | humidity at %d%% | %s", location.getName(), location.getCountry(), tempC, humidity, description);
    }

    @Getter
    @Setter
    private static class LocationResponse {
        private String name;
        private String country;
        private double lat;
        private double lon;
    }
}

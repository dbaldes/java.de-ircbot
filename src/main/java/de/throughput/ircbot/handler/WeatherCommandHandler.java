package de.throughput.ircbot.handler;

import com.google.gson.Gson;
import de.throughput.ircbot.Util;
import de.throughput.ircbot.api.Command;
import de.throughput.ircbot.api.CommandEvent;
import de.throughput.ircbot.api.CommandHandler;
import lombok.Getter;
import lombok.Setter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;
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

    public WeatherCommandHandler(@Value("${openweathermap.apiKey}") String apiKey) {
        this.apiKey = apiKey;
    }

    @Override
    public Set<Command> getCommands() {
        return Set.of(CMD_WEATHER);
    }

    @Override
    public boolean onCommand(CommandEvent command) {
        command.getArgLine().ifPresentOrElse(
                location -> getWeatherInfo(command, location),
                () -> command.respond(command.getCommand().getUsage())
        );
        return true;
    }

    private void getWeatherInfo(CommandEvent command, String location) {
        getLocation(location).thenCompose(response -> {
            if (response == null) {
                return CompletableFuture.completedFuture("Location not found");
            }
            return getWeather(response);
        }).thenAccept(command::respond);
    }

    private CompletableFuture<LocationResponse> getLocation(String location) {
        URI uri = URI.create(String.format(GEOCODING_URL, Util.urlEnc(location), apiKey));
        HttpRequest request = HttpRequest.newBuilder(uri)
                .header("Accept", "application/json")
                .GET()
                .build();

        return HttpClient.newHttpClient()
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

        return HttpClient.newHttpClient()
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

        return String.format("%s, %s: %.1f°C | humidity at %d | %s", location.getName(), location.getCountry(), tempC, humidity, description);
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

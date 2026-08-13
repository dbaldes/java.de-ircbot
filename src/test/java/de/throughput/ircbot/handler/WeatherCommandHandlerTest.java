package de.throughput.ircbot.handler;

import de.throughput.ircbot.api.Command;
import de.throughput.ircbot.api.CommandEvent;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.pircbotx.Channel;
import org.pircbotx.User;
import org.pircbotx.hooks.events.MessageEvent;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WeatherCommandHandlerTest {

    private static final String LOCATION_UPSERT = "INSERT INTO weather_location (channel, nick, location) VALUES (?, ?, ?) "
            + "ON CONFLICT (channel, nick) DO UPDATE SET location = EXCLUDED.location";

    private JdbcTemplate jdbc;
    private HttpClient httpClient;
    private WeatherCommandHandler handler;

    @BeforeEach
    void setUp() {
        jdbc = mock(JdbcTemplate.class);
        httpClient = mock(HttpClient.class);
        handler = new WeatherCommandHandler("api-key", jdbc, httpClient);
        when(httpClient.sendAsync(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenAnswer(invocation -> CompletableFuture.completedFuture(responseFor(invocation.getArgument(0))));
    }

    @Test
    void storesLocationAfterSuccessfulLookup() {
        MessageEvent event = commandEvent("#Zurich", "Alice");

        handler.onCommand(new CommandEvent(event, new Command("weather", "usage"), "!", Optional.of(" Zurich ")));

        verify(jdbc).update(LOCATION_UPSERT, "#zurich", "alice", "Zurich");
        verify(event).respond("Zurich, CH: 20.0°C | humidity at 50% | clear sky");
    }

    @Test
    void usesStoredLocationForSameUserAndChannel() {
        MessageEvent event = commandEvent("#Zurich", "Alice");
        when(jdbc.queryForObject("SELECT location FROM weather_location WHERE channel = ? AND nick = ?",
                String.class, "#zurich", "alice")).thenReturn("Zurich");

        handler.onCommand(new CommandEvent(event, new Command("weather", "usage"), "!", Optional.empty()));

        verify(jdbc).queryForObject("SELECT location FROM weather_location WHERE channel = ? AND nick = ?",
                String.class, "#zurich", "alice");
        verify(event).respond("Zurich, CH: 20.0°C | humidity at 50% | clear sky");
        verify(jdbc, never()).update(eq(LOCATION_UPSERT), any(), any(), any());
    }

    @Test
    void reportsErrorWhenNoLocationIsStored() {
        MessageEvent event = commandEvent("#Zurich", "Alice");
        when(jdbc.queryForObject("SELECT location FROM weather_location WHERE channel = ? AND nick = ?",
                String.class, "#zurich", "alice")).thenThrow(new EmptyResultDataAccessException(1));

        handler.onCommand(new CommandEvent(event, new Command("weather", "usage"), "!", Optional.empty()));

        verify(event).respond("No weather location stored for you in this channel. Use !weather <location> first.");
    }

    @Test
    void doesNotStoreUnknownLocation() {
        MessageEvent event = commandEvent("#Zurich", "Alice");
        HttpResponse<String> unknownLocationResponse = response("[]");
        when(httpClient.sendAsync(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(CompletableFuture.completedFuture(unknownLocationResponse));

        handler.onCommand(new CommandEvent(event, new Command("weather", "usage"), "!", Optional.of("Unknown")));

        verify(event).respond("Location not found");
        verify(jdbc, never()).update(any(String.class), any(), any(), any());
    }

    private static MessageEvent commandEvent(String channelName, String nick) {
        MessageEvent event = mock(MessageEvent.class);
        Channel channel = mock(Channel.class);
        User user = mock(User.class);
        when(event.getChannel()).thenReturn(channel);
        when(channel.getName()).thenReturn(channelName);
        when(event.getUser()).thenReturn(user);
        when(user.getNick()).thenReturn(nick);
        return event;
    }

    private static HttpResponse<String> responseFor(HttpRequest request) {
        return request.uri().getPath().contains("/geo/")
                ? response("[{\"name\":\"Zurich\",\"country\":\"CH\",\"lat\":47.3769,\"lon\":8.5417}]")
                : response("{\"main\":{\"temp\":68.0,\"humidity\":50},\"weather\":[{\"description\":\"clear sky\"}]}");
    }

    private static HttpResponse<String> response(String body) {
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.body()).thenReturn(body);
        return response;
    }
}

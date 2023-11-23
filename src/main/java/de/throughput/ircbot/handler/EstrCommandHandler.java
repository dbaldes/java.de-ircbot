package de.throughput.ircbot.handler;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Set;

import lombok.Getter;
import lombok.Setter;
import org.springframework.stereotype.Component;

import com.google.gson.Gson;
import de.throughput.ircbot.api.Command;
import de.throughput.ircbot.api.CommandEvent;
import de.throughput.ircbot.api.CommandHandler;

/**
 * Command handler for retrieving the Euro Short Term Rate (ESTR).
 */
@Component
public class EstrCommandHandler implements CommandHandler {

    private static final String API_URL_LATEST = "https://api.estr.dev/latest";

    private static final Command CMD_ESTR = new Command("estr", "estr - get the current Euro short term rate");

    @Override
    public Set<Command> getCommands() {
        return Set.of(CMD_ESTR);
    }

    @Override
    public boolean onCommand(CommandEvent command) {
        HttpRequest request = HttpRequest.newBuilder(URI.create(API_URL_LATEST))
                .GET()
                .build();

        HttpClient.newHttpClient()
                .sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenAccept(httpResponse -> processResponse(command, httpResponse));

        return false;
    }

    private void processResponse(CommandEvent command, HttpResponse<String> httpResponse) {
        var response = new Gson().fromJson(httpResponse.body(), EstrResponse.class);

        command.respond(String.format("%s%% (%s)", response.getValue(), response.getDate()));
    }

    @Getter
    @Setter
    private static class EstrResponse {
        private String date;
        private String value;
    }
}

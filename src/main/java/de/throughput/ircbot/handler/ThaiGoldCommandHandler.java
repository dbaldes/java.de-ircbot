package de.throughput.ircbot.handler;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.util.Set;
import lombok.Getter;
import lombok.Setter;
import org.springframework.stereotype.Component;
import com.google.gson.Gson;

import de.throughput.ircbot.api.Command;
import de.throughput.ircbot.api.CommandEvent;
import de.throughput.ircbot.api.CommandHandler;

/**
 * Command handler for retrieving Thai gold price information.
 */
@Component
public class ThaiGoldCommandHandler implements CommandHandler {

    private static final String API_URL_LATEST = "https://thai-gold-api.herokuapp.com/latest";


    private static final Command CMD_THAIGOLD = new Command("thaigold", "thaigold - get current thai gold price information");

    @Override
    public Set<Command> getCommands() {
        return Set.of(CMD_THAIGOLD);
    }

    @Override
    public boolean onCommand(CommandEvent command) {
        HttpRequest request = HttpRequest.newBuilder(URI.create(API_URL_LATEST))
                .GET()
                .build();

        HttpClient.newHttpClient()
                .sendAsync(request, BodyHandlers.ofString())
                .thenAccept(httpResponse -> processResponse(command, httpResponse));

        return false;
    }

    private void processResponse(CommandEvent command, HttpResponse<String> httpResponse) {
        ThaiGoldResponse response = new Gson().fromJson(httpResponse.body(), ThaiGoldResponse.class);

        ResponseData data = response.getResponse();
        PriceData goldBar = data.getPrice()
                .getGold_bar();
        // note: the API returns the gold sale price as "buy" and buy back price as "sell"
        String message = String.format("Thai gold price: bar sell \u0e3f%s buy \u0e3f%s (%s %s)",
                goldBar.getBuy(), goldBar.getSell(), data.getUpdate_time(), data.getDate());

        command.respond(message);
    }

    @Getter
    @Setter
    private static class ThaiGoldResponse {
        private String status;
        private ResponseData response;
    }

    @Getter
    @Setter
    private static class ResponseData {
        private String date;
        private String update_time;
        private GoldPrice price;
    }

    @Getter
    @Setter
    private static class GoldPrice {
        private PriceData gold;
        private PriceData gold_bar;
    }

    @Getter
    @Setter
    private static class PriceData {
        private String buy;
        private String sell;
    }


}

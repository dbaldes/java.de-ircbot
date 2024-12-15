package de.throughput.ircbot.handler;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import de.throughput.ircbot.api.Command;
import de.throughput.ircbot.api.CommandEvent;
import de.throughput.ircbot.api.CommandHandler;
import org.springframework.stereotype.Component;

import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Component
public class ForexCommandHandler implements CommandHandler {

    private static final Command CMD_FX = new Command("fx", "fx <CURRENCYPAIR> [YYYY-MM-DD]  - get currency exchange rates. example: !fx USDEUR 2024-12-15");

    @Override
    public Set<Command> getCommands() {
        return Set.of(CMD_FX);
    }

    @Override
    public boolean onCommand(CommandEvent command) {
        String[] args = command.getArgLine().map(s -> s.split("\\s+")).orElse(new String[0]);
        if (args.length < 1) {
            command.respond(CMD_FX.getUsage());
            return true;
        }

        String pair = args[0].toUpperCase();
        if (pair.length() != 6) {
            command.respond(CMD_FX.getUsage());
            return true;
        }
        String base = pair.substring(0, 3);
        String symbols = pair.substring(3);

        Optional<String> date = args.length > 1 ? Optional.of(args[1]) : Optional.empty();
        String url = date.isPresent()
                ? String.format("https://api.frankfurter.dev/v1/%s?base=%s&symbols=%s", date.get(), base, symbols)
                : String.format("https://api.frankfurter.dev/v1/latest?base=%s&symbols=%s", base, symbols);

        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .header("Accept", "application/json")
                .GET()
                .build();

        HttpClient.newHttpClient().sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(HttpResponse::body)
                .thenAccept(body -> {
                    Map<String, Object> map = parseJson(body);
                    Map<String, Double> rates = (Map<String, Double>) map.get("rates");
                    if (rates == null || rates.isEmpty()) {
                        command.respond("No rate found.");
                    } else {
                        BigDecimal rate = BigDecimal.valueOf(rates.values().iterator().next());
                        if (date.isPresent()) {
                            command.respond(String.format("%s/%s: %.4f on %s", base, symbols, rate, date.get()));
                        } else {
                            command.respond(String.format("%s/%s: %.4f", base, symbols, rate));
                        }
                    }
                }).exceptionally(ex -> {
                    command.respond("Error retrieving rate.");
                    return null;
                });

        return true;
    }

    private Map<String, Object> parseJson(String json) {
        Type mapType = new TypeToken<Map<String, Object>>() {}.getType();
        return new Gson().fromJson(json, mapType);
    }
}

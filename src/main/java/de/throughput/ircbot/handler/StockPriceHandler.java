package de.throughput.ircbot.handler;

import com.google.common.reflect.TypeToken;
import com.google.gson.Gson;
import de.throughput.ircbot.api.Command;
import de.throughput.ircbot.api.CommandEvent;
import de.throughput.ircbot.api.CommandHandler;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class StockPriceHandler implements CommandHandler {

    private static final Command CMD_PRICE = new Command("price", "price <symbols> - get real time price information on stock symbols. example: !price NVDA AAPL");
    private static final String BATCH_API_URL = "https://api.twelvedata.com/batch";

    private final String apiKey;

    public StockPriceHandler(@Value("${twelvedata.apiKey}") String apiKey) {
        this.apiKey = apiKey;
    }

    @Override
    public Set<Command> getCommands() {
        return Set.of(CMD_PRICE);
    }

    @Override
    public boolean onCommand(CommandEvent command) {
        command.getArgLine()
                .map(this::toSymbols)
                .ifPresentOrElse(
                        symbols -> getPriceInfo(command, symbols),
                        () -> command.respond(command.getCommand().getUsage())
                );
        return true;
    }

    private String[] toSymbols(String input) {
        return input.toUpperCase(Locale.ROOT).split("[\\s,;|]+");
    }

    private void getPriceInfo(CommandEvent command, String[] symbols) {
        LinkedHashMap<String, Map<String, String>> requests = new LinkedHashMap<>();
        LinkedHashMap<String, String> reqToSymbol = new LinkedHashMap<>();
        for (int i = 0; i < symbols.length; i++) {
            String symbol = symbols[i];
            String reqKey = "req_" + (i + 1);
            String urlPath = "/price?symbol=" + symbol + "&apikey=" + apiKey;
            requests.put(reqKey, Map.of("url", urlPath));
            reqToSymbol.put(reqKey, symbol);
        }
        String payload = new Gson().toJson(requests);

        HttpRequest httpRequest = HttpRequest.newBuilder(URI.create(BATCH_API_URL))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(payload))
                .build();

        HttpClient.newHttpClient()
                .sendAsync(httpRequest, HttpResponse.BodyHandlers.ofString())
                .thenApply(httpResponse -> processBatchResponse(httpResponse, reqToSymbol))
                .thenAccept(command::respond)
                .exceptionally(e -> {
                    command.respond("Error: " + e.getMessage());
                    return null;
                })
                .join();
    }

    private String processBatchResponse(HttpResponse<String> httpResponse, Map<String, String> reqToSymbol) {
        Gson gson = new Gson();
        Type mapType = new TypeToken<Map<String, Object>>() {}.getType();
        Map<String, Object> responseMap = gson.fromJson(httpResponse.body(), mapType);
        Map<String, Object> data = (Map<String, Object>) responseMap.get("data");

        return reqToSymbol.entrySet().stream()
                .map(entry -> {
                    String reqKey = entry.getKey();
                    String symbol = entry.getValue();
                    Map<String, Object> reqResult = (Map<String, Object>) data.get(reqKey);
                    String status = (String) reqResult.get("status");
                    if ("success".equalsIgnoreCase(status)) {
                        Map<String, Object> response = (Map<String, Object>) reqResult.get("response");
                        Object price = response.get("price");
                        if (price != null) {
                            BigDecimal priceDecimal = new BigDecimal(price.toString());
                            String roundedPrice = priceDecimal.setScale(2, RoundingMode.HALF_UP).toPlainString();
                            return symbol + ": " + roundedPrice;
                        }
                    }
                    return symbol + ": error";
                })
                .collect(Collectors.joining(" "));
    }
}

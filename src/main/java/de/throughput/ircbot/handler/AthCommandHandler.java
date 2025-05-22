package de.throughput.ircbot.handler;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.pircbotx.PircBotX;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import de.throughput.ircbot.api.Command;
import de.throughput.ircbot.api.CommandEvent;
import de.throughput.ircbot.api.CommandHandler;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Locale;
import java.util.Set;

@Component
public class AthCommandHandler implements CommandHandler {

    private static final Command CMD_ATH = new Command("ath", "ath [<id>] - get all-time-high of a cryptocurrency by CoinGecko ID (default: bitcoin) in USD and EUR with dates");
    private static final String API_URL = "https://api.coingecko.com/api/v3/coins/";
    private static final String DEFAULT_ID = "bitcoin";

    @Value("${coingecko.apiKey}")
    private String apiKey;

    private final PircBotX bot;
    private final HttpClient httpClient;

    public AthCommandHandler(@Lazy PircBotX bot) {
        this.bot = bot;
        this.httpClient = HttpClient.newHttpClient();
    }

    @Override
    public Set<Command> getCommands() {
        return Set.of(CMD_ATH);
    }

    @Override
    public boolean onCommand(CommandEvent command) {
        if (!command.getCommand().equals(CMD_ATH)) {
            return false;
        }
        String id = command.getArgLine().map(arg -> arg.trim().toLowerCase(Locale.ROOT))
                .filter(s -> !s.isEmpty())
                .orElse(DEFAULT_ID);
        fetchAth(command, id);
        return true;
    }

    private void fetchAth(CommandEvent command, String id) {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(API_URL + id))
                .header("Accept", "application/json")
                .header("x-cg-pro-api-key", apiKey)
                .GET()
                .build();

        httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenAccept(response -> {
                    if (response.statusCode() != 200) {
                        command.respond("Coin not found or API error");
                        return;
                    }
                    try {
                        JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
                        JsonObject marketData = json.getAsJsonObject("market_data");
                        JsonObject ath = marketData.getAsJsonObject("ath");
                        JsonObject athDate = marketData.getAsJsonObject("ath_date");

                        BigDecimal usdValue = ath.get("usd").getAsBigDecimal();
                        BigDecimal eurValue = ath.get("eur").getAsBigDecimal();
                        String usdRawDate = athDate.get("usd").getAsString();
                        String eurRawDate = athDate.get("eur").getAsString();
                        String usdDate = usdRawDate.contains("T") ? usdRawDate.substring(0, usdRawDate.indexOf('T')) : usdRawDate;
                        String eurDate = eurRawDate.contains("T") ? eurRawDate.substring(0, eurRawDate.indexOf('T')) : eurRawDate;

                        command.respond(String.format(
                                "ATH of %s: %s USD (%s), %s EUR (%s) - data provided by https://www.coingecko.com/",
                                id,
                                usdValue.toPlainString(),
                                usdDate,
                                eurValue.toPlainString(),
                                eurDate));
                    } catch (Exception e) {
                        command.respond("Could not parse ATH data");
                    }
                });
    }
}

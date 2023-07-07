package de.throughput.ircbot.handler;

import de.throughput.ircbot.api.Command;
import de.throughput.ircbot.api.CommandEvent;
import de.throughput.ircbot.api.CommandHandler;
import lombok.RequiredArgsConstructor;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class YahooScrapeCommandHandler implements CommandHandler {

    private static final String FINANCE_YAHOO_URI = "https://finance.yahoo.com";
    private static final String MOZILLA_USER_AGENT = "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/87.0.4280.88 Safari/537.36";

    private static final Command CMD_STOCK = new Command("stock", "<symbols> - get price information on stock symbols");

    @Override
    public Set<Command> getCommands() {
        return Set.of(CMD_STOCK);
    }

    @Override
    public boolean onCommand(CommandEvent command) {
        command.getArgLine()
                .map(this::toSymbols)
                .ifPresentOrElse(
                        symbols -> symbols.forEach(symbol -> this.checkStock(symbol, command)),
                        () -> command.respond(command.getCommand().getUsage()));
        return true;
    }

    private Set<String> toSymbols(String input) {
        return Set.of(input.toUpperCase(Locale.ROOT)
                .split("[\\s,;|]+"));
    }

    private void checkStock(String stockId, CommandEvent command) {
        HttpRequest request = HttpRequest.newBuilder(URI.create(FINANCE_YAHOO_URI + "/quote/" + stockId + "?p=" + stockId))
                .header("User-Agent", MOZILLA_USER_AGENT)
                .GET()
                .build();

        HttpClient.newHttpClient().sendAsync(request, BodyHandlers.ofString()).thenAccept(httpResponse -> processResponse(command, httpResponse));
    }

    private void processResponse(CommandEvent command, HttpResponse<String> httpResponse) {
        Document doc = Jsoup.parse(httpResponse.body());
        Elements moneycode = doc.select("span:contains(Currency in)");
        String currencyCode = moneycode.text();
        currencyCode = currencyCode.substring(currencyCode.indexOf("Currency in ")).replace("Currency in ", "");
        Elements prices = doc.getElementsByAttributeValue("data-test", "qsp-price");
        Elements priceChanges = doc.getElementsByAttributeValue("data-test", "qsp-price-change");
        StringBuilder allPrices = new StringBuilder();
        String symbol = doc.title().substring(doc.title().indexOf("("));
        symbol = symbol.substring(1, symbol.indexOf(")"));
        allPrices.append(symbol);
        allPrices.append(" : ");
        int i = 0;
        for (Element price : prices) {
            String priceChangeText = "";
            if (priceChanges.size() > i) {
                Element priceChange = priceChanges.get(i);
                priceChangeText = priceChange.text();
            }
            if (price != null && i == 0) {
                String result = price.text() + " " + currencyCode + " (" + priceChangeText + ")";
                allPrices.append(result);

            }
            i++;
        }
        command.respond(allPrices.toString());
    }

}

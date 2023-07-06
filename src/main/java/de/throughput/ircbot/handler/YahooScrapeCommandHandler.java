package de.throughput.ircbot.handler;

import de.throughput.ircbot.api.Command;
import de.throughput.ircbot.api.CommandEvent;
import de.throughput.ircbot.api.CommandHandler;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class YahooScrapeCommandHandler implements CommandHandler {

    private static final String baseUri = "https://finance.yahoo.com";
    private static final String MOZILLA_USER_AGENT = "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/87.0.4280.88 Safari/537.36";

    private static final Command CMD_NEWYAHOO = new Command("stock", "<symbols> - get price information on stock symbols");
    public void checkStock(String stockId, CommandEvent command) throws IOException {
        HttpRequest request = HttpRequest.newBuilder(
                        URI.create(baseUri.concat("/quote/" + stockId + "?p=" + stockId)))
                .header("User-Agent", MOZILLA_USER_AGENT)
                .GET()
                .build();

        HttpClient.newHttpClient()
            .sendAsync(request, BodyHandlers.ofString())
            .thenAccept(httpResponse -> processResponse(command, httpResponse));

    }
    private void processResponse(CommandEvent command, HttpResponse<String> httpResponse) {
        Document doc = Jsoup.parse(httpResponse.body());
        Elements moneycode = doc.select("span:contains(Currency in)");
        String currencyCode = moneycode.text();
        currencyCode = currencyCode.substring(currencyCode.indexOf("Currency in ")).replace("Currency in ","");
        Elements prices = doc.getElementsByAttributeValue("data-test", "qsp-price");
        Elements priceChanges = doc.getElementsByAttributeValue("data-test", "qsp-price-change");
        int i = 0;
        StringBuilder allPrices = new StringBuilder();
        String symbol = doc.title().substring(doc.title().indexOf("("));
        symbol = symbol.substring(1, symbol.indexOf(")"));
        allPrices.append(symbol);
        allPrices.append(" : ");
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

    @Override
    public boolean onCommand(CommandEvent command) {
        try {
            Optional<String> parameter = command.getArgLine();
            if (parameter.isPresent()) {

                String allOfThem = parameter.get();
                String[] allSymbols = allOfThem.split(" ");
                for (String oneSymbol : allSymbols) {
                    this.checkStock(oneSymbol, command);
                }
                return true;
            }
            return false;
        } catch (IOException e) {
            return false;
        }
    }

    @Override
    public Set<Command> getCommands() {
        return Set.of(CMD_NEWYAHOO);
    }
}

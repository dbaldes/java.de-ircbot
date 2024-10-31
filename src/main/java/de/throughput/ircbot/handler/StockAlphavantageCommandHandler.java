package de.throughput.ircbot.handler;

import com.google.common.reflect.TypeToken;
import com.google.gson.Gson;
import de.throughput.ircbot.api.Command;
import de.throughput.ircbot.api.CommandEvent;
import de.throughput.ircbot.api.CommandHandler;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.pircbotx.Colors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Currency;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.stream.Collectors;

import static java.util.Map.Entry.*;

/**
 * Command handler for retrieving stock and FX quotes from yahoo finance.
 */
@Component
public class StockAlphavantageCommandHandler implements CommandHandler {

    private static final BigDecimal ONE_HUNDREDTH = new BigDecimal("0.01");
    private static final BigDecimal ONE_TENHOUSANDTH = new BigDecimal("0.0001");
    private static final String DEFAULT_CURRENCY = "USD";

    private static final Command CMD_STOCK = new Command("stock", "stock <symbols> - get price information on stock symbols. example: !stock AMD");
    private static final Command CMD_FX = new Command("fx", "fx <symbols> - get currency exchange rates. example: !fx USDEUR");

    private static final String API_URL_STOCK_QUOTE = "https://www.alphavantage.co/query?function=GLOBAL_QUOTE&symbol=%s&apikey=%s";
    private static final String API_URL_FOREX = "https://www.alphavantage.co/query?function=CURRENCY_EXCHANGE_RATE&from_currency=%s&to_currency=%s&apikey=%s";

    private final String apiKey;

    public StockAlphavantageCommandHandler(@Value("alphavantage.apiKey") String apiKey) {
        this.apiKey = apiKey;
    }

    @Override
    public Set<Command> getCommands() {
        return Set.of(CMD_STOCK, CMD_FX);
    }

    @Override
    public boolean onCommand(CommandEvent command) {
        command.getArgLine()
                .map(this::toSymbols)
                .ifPresentOrElse(
                        symbols -> getPriceInfo(command, symbols),
                        () -> command.respond(command.getCommand()
                                .getUsage()));
        return true;
    }

    private String[] toSymbols(String input) {
        return input.toUpperCase(Locale.ROOT)
                .split("[\\s,;|]+");
    }

    private void getPriceInfo(CommandEvent command, String[] symbols) {
        if (CMD_STOCK.equals(command.getCommand())) {
            command.respond(toStockMessage(getStockQuotes(symbols)));
        } else if (CMD_FX.equals(command.getCommand())) {
            command.respond(toFxMessage(getFxQuotes(symbols)));
        }
    }

    private String toFxMessage(Map<String, FxQuote> result) {
        return result.entrySet()
                .stream()
                .sorted(comparingByKey())
                .map(entry -> {
                    FxQuote fxQuote = entry.getValue();

                    return String.format("%s/%s: %.4f", fxQuote.fromCurrency, fxQuote.toCurrency, fxQuote.exchangeRate);
                })
                .collect(Collectors.joining(" "));
    }

    private String toStockMessage(Map<String, StockQuote> result) {
        return result.entrySet()
                       .stream()
                       .sorted(comparingByKey())
                       .map(entry -> {
                           StockQuote quote = entry.getValue();

                           String priceColor = quote.change.signum() >= 0 ? Colors.GREEN : Colors.RED;
                           return String.format("%s: %s%s (%+.2f%%)%s", quote.symbol, priceColor, renderPrice(quote.price, DEFAULT_CURRENCY),
                                   quote.changePercent, Colors.NORMAL);
                       })
                       .collect(Collectors.joining(" "))
               + " (\u0394 previous close)";
    }

    private static String renderPrice(BigDecimal price, String currencyCode) {
        int precision = 2;
        if (price.compareTo(ONE_TENHOUSANDTH) < 0) {
            precision = 8;
        } else if (price.compareTo(ONE_HUNDREDTH) < 0) {
            precision = 6;
        } else if (price.compareTo(BigDecimal.ONE) < 0) {
            precision = 4;
        }

        String currencySymbol = currencyCode;
        try {
            Currency currency = Currency.getInstance(currencyCode);
            currencySymbol = currency.getSymbol(Locale.US);
        } catch (IllegalArgumentException e) {
            // ignore; it might be a crypto currency
        }
        if (currencySymbol.endsWith("$")) {
            return String.format("%s%." + precision + "f", currencySymbol, price);
        }
        return String.format("%." + precision + "f%s", price, currencySymbol);
    }

    private record StockQuote(String symbol, BigDecimal price, BigDecimal change, BigDecimal changePercent) {}

    private record FxQuote(String fromCurrency, String fromCurrencyName, String toCurrency, String toCurrencyName, BigDecimal exchangeRate) {}

    private Map<String, StockQuote> getStockQuotes(String[] symbols) {
        return Arrays.stream(symbols)
                .map(this::getStockQuote)
                .map(CompletableFuture::join)
                .collect(Collectors.toMap(StockQuote::symbol, Function.identity()));
    }

    private CompletableFuture<StockQuote> getStockQuote(String symbol) {
        URI uri = URI.create(String.format(API_URL_STOCK_QUOTE, symbol, apiKey));

        HttpRequest request = HttpRequest.newBuilder(uri)
                .header("Accept", "application/json")
                .GET()
                .build();

        return HttpClient.newHttpClient()
                .sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(this::processStockQuoteResponse);
    }

    private StockQuote processStockQuoteResponse(HttpResponse<String> httpResponse) {
        Map<String, String> globalQuote = (Map<String, String>) parseAsMap(httpResponse).get("Global Quote");
        return new StockQuote(
                globalQuote.get("01. symbol"),
                new BigDecimal(globalQuote.get("05. price")),
                new BigDecimal(globalQuote.get("09. change")),
                new BigDecimal(globalQuote.get("10. change percent").replace("%", "")));
    }

    private Map<String, FxQuote> getFxQuotes(String[] symbols) {
        return Arrays.stream(symbols)
                .map(this::getFxQuoteKey)
                .map(currencyPair -> getFxQuote(currencyPair.getLeft(), currencyPair.getRight()))
                .map(CompletableFuture::join)
                .collect(Collectors.toMap(fxQuote -> fxQuote.fromCurrency + "/" + fxQuote.toCurrency, Function.identity()));
    }

    private Pair<String, String> getFxQuoteKey(String symbol) {
        return ImmutablePair.of(symbol.substring(0, 3), symbol.substring(3));
    }

    private CompletableFuture<FxQuote> getFxQuote(String fromCurrency, String toCurrency) {
        URI uri = URI.create(String.format(API_URL_FOREX, fromCurrency, toCurrency, apiKey));

        HttpRequest request = HttpRequest.newBuilder(uri)
                .header("Accept", "application/json")
                .GET()
                .build();

        return HttpClient.newHttpClient()
                .sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(this::processFxQuoteResponse);
    }

    private FxQuote processFxQuoteResponse(HttpResponse<String> httpResponse) {
        Map<String, String> fxQuote = (Map<String, String>) parseAsMap(httpResponse).get("Realtime Currency Exchange Rate");
        return new FxQuote(
                fxQuote.get("1. From_Currency Code"),
                fxQuote.get("2. From_Currency Name"),
                fxQuote.get("3. To_Currency Code"),
                fxQuote.get("4. To_Currency Name"),
                new BigDecimal(fxQuote.get("5. Exchange Rate")));
    }

    private static Map<String, Object> parseAsMap(HttpResponse<String> httpResponse) {
        Type mapType = new TypeToken<Map<String, Object>>() {}.getType();
        return new Gson().fromJson(httpResponse.body(), mapType);
    }
}

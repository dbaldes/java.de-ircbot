package de.throughput.ircbot.handler;

import static de.throughput.ircbot.Util.urlEnc;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.util.Collection;
import java.util.Currency;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.pircbotx.Colors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.google.gson.annotations.SerializedName;

import de.throughput.ircbot.api.Command;
import de.throughput.ircbot.api.CommandEvent;
import de.throughput.ircbot.api.CommandHandler;
import lombok.Getter;
import lombok.Setter;

/**
 * Command handler for retrieving crypto currency information from coinmarketcap.com.
 */
@Component
public class CryptoCommandHandler implements CommandHandler {
  
  private static final Pattern PATTERN_QUOTE_CURRENCY = Pattern.compile("^(.*)\\s+in\\s+(\\w+)\\s*$");
  private static final Pattern PATTERN_AMOUNT = Pattern.compile("^(\\d*(\\.\\d+)?)\\s+(.*)$");
  private static final BigDecimal ONE_HUNDREDTH = new BigDecimal("0.01");
  private static final BigDecimal ONE_TENHOUSANDTH = new BigDecimal("0.0001");
  
  private static final String USD = "USD";
  
  private static final String API_URL_QUOTES_LATEST = "https://pro-api.coinmarketcap.com/v1/cryptocurrency/quotes/latest";
  private static final String API_URL_LISTINGS_LATEST = "https://pro-api.coinmarketcap.com/v1/cryptocurrency/listings/latest";
  private final String cmcApiKey;

  private static final Command CMD_CRYPTO = new Command("crypto", "crypto [<amount>] <symbols> [in <currency>] - "
      + "get price information on crypto currencies - currency defaults to USD, amount to 1");
  
  public CryptoCommandHandler(@Value("${coinmarketcap.api.key}") String cmcApiKey) {
    this.cmcApiKey = cmcApiKey;
  }
  
  @Override
  public Set<Command> getCommands() {
    return Set.of(CMD_CRYPTO);
  }
  
  @Override
  public boolean onCommand(CommandEvent command) {
    command.getArgLine()
      .map(this::toCmcQuery)
      .ifPresentOrElse(
          query -> getPriceInfo(command, query),
          () -> getPriceInfo(command, new CmcTopCurrenciesQuery()));
    
    return true;
  }

  private CmcLatestQuoteQuery toCmcQuery(String input) {
    var query = new CmcLatestQuoteQuery();
    Matcher matcher = PATTERN_QUOTE_CURRENCY.matcher(input);
    String symbols = input;
    if (matcher.matches()) {
      query.setConvert(matcher.group(2).toUpperCase());
      symbols = matcher.group(1);
    }
    matcher = PATTERN_AMOUNT.matcher(symbols);
    if (matcher.matches()) {
      query.setAmount(new BigDecimal(matcher.group(1)));
      symbols = matcher.group(3);
    }
    query.setSymbol(String.join(",", symbols.toUpperCase(Locale.ROOT).split("[\\s,;|]+")));
    return query;
  }
  

  private void getPriceInfo(CommandEvent command, CmcQuery query) {
    URI uri =  URI.create(query.toUrl());
    
    HttpRequest request = HttpRequest.newBuilder(uri)
        .header("X-CMC_PRO_API_KEY", cmcApiKey)
        .header("Accept", "application/json")
        .GET().build();

    HttpClient.newHttpClient()
        .sendAsync(request, BodyHandlers.ofString())
        .thenAccept(httpResponse -> processResponse(command, httpResponse, query));
  }
  
  private void processResponse(CommandEvent command, HttpResponse<String> httpResponse, CmcQuery query) {
    try {
      CmcResponse response = new Gson().fromJson(httpResponse.body(), query.getResponseClass());
      if (httpResponse.statusCode() == 200) {
        if (response != null) {
          command.respond(toMessage(response, query));
        } else {
          command.respond("that didn't work");
        }
      } else {
        command.respond(String.format("%d: %s", response.getStatus().getErrorCode(), response.getStatus().getErrorMessage()));
      }
    } catch (JsonSyntaxException e) {
      command.respond(String.format("could not parse response, status: %d", httpResponse.statusCode()));
    }
  }

  private static String toMessage(CmcResponse response, CmcQuery query) {
    return response.getQuotes().stream()
        .map(c -> {
          Entry<String, CmcQuote> currencyQuote = c.getQuoteByFiatSymbol().entrySet().iterator().next();
          String quoteCurrencyCode = currencyQuote.getKey();
          CmcQuote quote = currencyQuote.getValue();
          String priceColor = quote.getPercentChange24h().compareTo(BigDecimal.ZERO) >= 0 ? Colors.GREEN : Colors.RED;
          return String.format("%s: %s%s (%+.1f%%)%s", renderSymbol(query.getAmount(), c.getName()), priceColor,  renderPrice(query.getAmount(), quote.getPrice(), quoteCurrencyCode), quote.getPercentChange24h(), Colors.NORMAL);
        })
        .collect(Collectors.joining(" "))
        + " (\u039424h)";
  }
  
  private static String renderSymbol(BigDecimal amount, String symbol) {
    return amount.compareTo(BigDecimal.ONE) != 0 ? amount.toPlainString() + " " + symbol : symbol;
  }

  private static String renderPrice(BigDecimal amount, BigDecimal price, String currencyCode) {
    BigDecimal total = price.multiply(amount);
    
    int precision = 2;
    if (total.compareTo(ONE_TENHOUSANDTH) < 0) {
      precision = 8;
    } else if (total.compareTo(ONE_HUNDREDTH) < 0) {
      precision = 6;
    } else if (total.compareTo(BigDecimal.ONE) < 0) {
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
      return String.format("%s%." + precision + "f", currencySymbol, total);
    }
    return String.format("%." + precision + "f%s", total, currencySymbol);
  }
  
  @Getter
  @Setter
  private static abstract class CmcQuery {
    private String convert = USD;
    private BigDecimal amount = BigDecimal.ONE;
    
    abstract String toUrl();
    abstract Class<? extends CmcResponse> getResponseClass();
  }
  
  @Getter
  @Setter
  private static class CmcLatestQuoteQuery extends CmcQuery {
    private String symbol;
    
    @Override
    String toUrl() {
      return API_URL_QUOTES_LATEST + "?symbol=" + urlEnc(getSymbol()) + "&convert=" + urlEnc(getConvert());
    }

    @Override
    Class<? extends CmcResponse> getResponseClass() {
      return CmcQuoteResponse.class;
    }
  }

  @Getter
  @Setter
  private static class CmcTopCurrenciesQuery extends CmcQuery {
    private int limit = 10;
    
    @Override
    String toUrl() {
      return API_URL_LISTINGS_LATEST + "?limit=" + limit + "&sort=market_cap&sort_dir=desc&convert=" + urlEnc(getConvert());
    }

    @Override
    Class<? extends CmcResponse> getResponseClass() {
      return CmcListingsResponse.class;
    }
  }
  
  @Getter
  @Setter
  private abstract static class CmcResponse {
    private CmcStatus status;
    
    abstract Collection<CmcCryptoCurrency> getQuotes();
  }
  
  @Getter
  @Setter
  private static class CmcQuoteResponse extends CmcResponse {
    @SerializedName("data")
    private Map<String, CmcCryptoCurrency> dataByCryptoSymbol;
    
    @Override
    Collection<CmcCryptoCurrency> getQuotes() {
      return dataByCryptoSymbol.values();
    }
  }
  
  @Getter
  @Setter
  private static class CmcListingsResponse extends CmcResponse {
    private List<CmcCryptoCurrency> data;
    
    @Override
    Collection<CmcCryptoCurrency> getQuotes() {
      return data;
    }
  }
  
  @Getter
  @Setter
  private static class CmcStatus {
    @SerializedName("error_code")
    private int errorCode;
    @SerializedName("error_message")
    private String errorMessage;
  }
  
  @Getter
  @Setter
  private static class CmcCryptoCurrency {
    private int id;
    private String name;
    private String symbol;
    @SerializedName("quote")
    private Map<String, CmcQuote> quoteByFiatSymbol;
  }

  @Getter
  @Setter
  private static class CmcQuote {
    private BigDecimal price;
    @SerializedName("percent_change_24h")
    private BigDecimal percentChange24h;
    @SerializedName("percent_change_7d")
    private BigDecimal percentChange7d;
  }
  
}

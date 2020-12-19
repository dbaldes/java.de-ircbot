package de.throughput.ircbot.handler;

import static de.throughput.ircbot.Util.urlEnc;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.util.Currency;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

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
  private static final BigDecimal ONE_HUNDREDTH = new BigDecimal("0.01");
  private static final BigDecimal ONE_TENHOUSANDTH = new BigDecimal("0.0001");
  
  private static final String USD = "USD";
  
  private static final String API_URL_QUOTES_LATEST = "https://pro-api.coinmarketcap.com/v1/cryptocurrency/quotes/latest";
  private final String cmcApiKey;

  private static final Command CMD_CRYPTO = new Command("crypto", "crypto <symbols> [in <symbol>] - get price information on crypto currencies");
  
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
          () -> command.respond(command.getCommand().getUsage()));
    
    return true;
  }

  private CmcLatestQuoteQuery toCmcQuery(String input) {
    var query = new CmcLatestQuoteQuery();
    Matcher matcher = PATTERN_QUOTE_CURRENCY.matcher(input);
    String symbols = input;
    if (matcher.matches()) {
      query.setConvert(matcher.group(2).toUpperCase());
      symbols = matcher.group(1);
    } else {
      query.setConvert(USD);
    }
    query.setSymbol(String.join(",", symbols.toUpperCase(Locale.ROOT).split("[\\s,;|]+")));
    return query;
  }

  private void getPriceInfo(CommandEvent command, CmcLatestQuoteQuery query) {
    URI uri =  URI.create(API_URL_QUOTES_LATEST + "?" + query.toQueryString());
    
    HttpRequest request = HttpRequest.newBuilder(uri)
        .header("X-CMC_PRO_API_KEY", cmcApiKey)
        .header("Accept", "application/json")
        .GET().build();

    HttpClient.newHttpClient()
        .sendAsync(request, BodyHandlers.ofString())
        .thenAccept(httpResponse -> processResponse(command, httpResponse));
  }
  
  private void processResponse(CommandEvent command, HttpResponse<String> httpResponse) {
    try {
      CmcResponse response = new Gson().fromJson(httpResponse.body(), CmcResponse.class);
      if (httpResponse.statusCode() == 200) {
        if (response != null) {
          command.respond(toMessage(response));
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

  private static String toMessage(CmcResponse response) {
    return response.getDataByCryptoSymbol().values().stream()
        .sorted((a, b) -> a.getSymbol().compareTo(b.getSymbol()))
        .map(c -> {
          Entry<String, CmcQuote> currencyQuote = c.getQuoteByFiatSymbol().entrySet().iterator().next();
          String quoteCurrencyCode = currencyQuote.getKey();
          CmcQuote quote = currencyQuote.getValue();
          return String.format("%s: %s (%.1f%%)", c.getSymbol(), renderPrice(quote.getPrice(), quoteCurrencyCode), quote.getPercentChange24h());
        })
        .collect(Collectors.joining(" "));
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
  
  @Getter
  @Setter
  private class CmcLatestQuoteQuery {
    private String symbol;
    private String convert;
    
    public String toQueryString() {
      return "symbol=" + urlEnc(getSymbol()) + "&convert=" + urlEnc(getConvert());
    }
  }
  
  @Getter
  @Setter
  private class CmcResponse {
    private CmcStatus status;
    @SerializedName("data")
    private Map<String, CmcCryptoCurrency> dataByCryptoSymbol;
  }
  
  @Getter
  @Setter
  private class CmcStatus {
    @SerializedName("error_code")
    private int errorCode;
    @SerializedName("error_message")
    private String errorMessage;
  }
  
  @Getter
  @Setter
  private class CmcCryptoCurrency {
    private int id;
    private String name;
    private String symbol;
    @SerializedName("quote")
    private Map<String, CmcQuote> quoteByFiatSymbol;
  }

  @Getter
  @Setter
  private class CmcQuote {
    private BigDecimal price;
    @SerializedName("percent_change_24h")
    private BigDecimal percentChange24h;
    @SerializedName("percent_change_7d")
    private BigDecimal percentChange7d;
  }
  
}

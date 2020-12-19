package de.throughput.ircbot.handler;

import static de.throughput.ircbot.Util.urlEnc;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
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
  
  private static final String USD = "USD";

  private static final BigDecimal ONE_HUNDREDTH = new BigDecimal("0.01");

  private static final Command CMD_CRYPTO = new Command("crypto", "crypto <symbols> - get price information on crypto currencies");
  
  private static final String API_URL_QUOTES_LATEST = "https://pro-api.coinmarketcap.com/v1/cryptocurrency/quotes/latest";
  private final String cmcApiKey;
  
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
      .map(this::sanitizeSymbolList)
      .ifPresentOrElse(
          symbols -> getPriceInfo(command, symbols),
          () -> command.respond(command.getCommand().getUsage()));
    
    return true;
  }

  private String sanitizeSymbolList(String input) {
    return String.join(",", input.toUpperCase(Locale.ROOT).split("[\\s,;|]+"));
  }

  private void getPriceInfo(CommandEvent command, String symbols) {
    URI uri =  URI.create(API_URL_QUOTES_LATEST + "?symbol=" + urlEnc(symbols) + "&convert=" + USD);
    
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
          CmcQuote usdQuote = c.getQuoteByFiatSymbol().get(USD);
          int precision = 2;
          if (usdQuote.getPrice().compareTo(ONE_HUNDREDTH) < 0) {
            precision = 6;
          } else if (usdQuote.getPrice().compareTo(BigDecimal.ONE) < 0) {
            precision = 4;
          }
          return String.format("%s: $%." + precision + "f (%.1f%%)", c.getSymbol(), usdQuote.getPrice(), usdQuote.getPercentChange24h());
        })
        .collect(Collectors.joining(" "));
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

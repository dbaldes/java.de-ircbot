package de.throughput.ircbot.handler;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.Currency;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.pircbotx.Colors;
import org.springframework.stereotype.Component;

import de.throughput.ircbot.api.Command;
import de.throughput.ircbot.api.CommandEvent;
import de.throughput.ircbot.api.CommandHandler;
import yahoofinance.Stock;
import yahoofinance.YahooFinance;
import yahoofinance.quotes.fx.FxQuote;
import yahoofinance.quotes.stock.StockQuote;

/**
 * Command handler for retrieving stock and FX quotes from yahoo finance.
 */
@Component
public class StockCommandHandler implements CommandHandler {
  
  private static final BigDecimal ONE_HUNDREDTH = new BigDecimal("0.01");
  private static final BigDecimal ONE_TENHOUSANDTH = new BigDecimal("0.0001");

  private static final Command CMD_STOCK = new Command("stock", "stock <symbols> - get price information on stock symbols. example: !fx AMD");
  private static final Command CMD_FX = new Command("fx", "fx <symbols> - get price information on stock symbols. example: !fx USDEUR=x");
  
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
          () -> command.respond(command.getCommand().getUsage()));
    
    return true;
  }

  private String[] toSymbols(String input) {
    return input.toUpperCase(Locale.ROOT).split("[\\s,;|]+");
  }

  private void getPriceInfo(CommandEvent command, String[] symbols) {
    try {
      if (CMD_STOCK.equals(command.getCommand())) {
        command.respond(toStockMessage(YahooFinance.get(symbols)));
      } else if (CMD_FX.equals(command.getCommand())) {
        command.respond(toFxMessage(YahooFinance.getFx(symbols)));
      }
    } catch (IOException e) {
      command.respond("error: " + e.getMessage());
    }
  }
  
  private String toFxMessage(Map<String, FxQuote> result) {
    return result.entrySet().stream().sorted((a, b) -> a.getKey().compareTo(b.getKey()))
    .map(entry -> {
      FxQuote fxQuote = entry.getValue();

      return String.format("%s: %.4f", fxQuote.getSymbol(), fxQuote.getPrice());
    })
    .collect(Collectors.joining(" "));
  }
  
  private String toStockMessage(Map<String, Stock> result) {
    return result.entrySet().stream().sorted((a, b) -> a.getKey().compareTo(b.getKey()))
    .map(entry -> {
      Stock stock = entry.getValue();

      StockQuote quote = stock.getQuote();
      String priceColor = quote.getChange().compareTo(BigDecimal.ZERO) >= 0 ? Colors.GREEN : Colors.RED;
      return String.format("%s: %s%s (%+.2f)%s", stock.getSymbol(), priceColor, renderPrice(quote.getPrice(), stock.getCurrency()), quote.getChange(), Colors.NORMAL);
    })
    .collect(Collectors.joining(" "))
      + " (\u0394 last close)";
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
  
}

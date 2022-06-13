package de.throughput.ircbot.handler;

import java.util.List;
import java.util.Random;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import org.pircbotx.PircBotX;
import org.pircbotx.hooks.events.MessageEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import de.throughput.ircbot.api.MessageHandler;

/**
 * Deals with mrfinance, a bot.
 */
@Component
public class MrFinanceHandler implements MessageHandler, ApplicationListener<TitleEvent> {

  private static final String MRFINANCE = "mrfinance";

  private Lock lock = new ReentrantLock();
  private final PircBotX bot;
  private TitleEvent lastTitleEvent;
  private QuoteMessage lastMrFinanceMessage;
  
  private static final List<String> MRFINANCE_TAUNTS = List.of(
      "shut up, tootsie!",
      "Schnauze, Lutscher!",
      "nice try, sweetheart :3",
      "better leave that to the pros!",
      "you suck.");
  
  public MrFinanceHandler(@Lazy PircBotX bot) {
    this.bot = bot;
  }
  
  @Override
  public boolean isOnlyTalkChannels() {
    return true;
  }

  @Override
  public boolean onMessage(MessageEvent event) {
    if (event.getUser().getNick().equals(MRFINANCE)) {
      lock.lock();
      try {
       lastMrFinanceMessage = new QuoteMessage(event.getChannelSource(), MRFINANCE, System.currentTimeMillis(), event.getMessage());
       checkTitleAndTaunt();
      } finally {
        lock.unlock();
      }
    }
    return false;
  }

  @Override
  public void onApplicationEvent(TitleEvent event) {
    lock.lock();
    try {
      this.lastTitleEvent = event;
      checkTitleAndTaunt();
    } finally {
      lock.unlock();
    }
  }

  private void checkTitleAndTaunt() {
    // if the the title messages were less than 3 seconds apart and mrfinance's title was contained in our title but shorter (i.e. incomplete)
    if (lastMrFinanceMessage != null && lastTitleEvent != null
        && Math.abs(lastMrFinanceMessage.getTimestamp() - lastTitleEvent.getTimestamp()) < 3000L) {
      String lastTitle = lastTitleEvent.getTitle().trim();
      String mrFinanceTitle = lastMrFinanceMessage.getMessage().trim();
      if (lastTitle.contains(mrFinanceTitle) && lastTitle.length() > mrFinanceTitle.length()) {
        tauntMrFinance(lastMrFinanceMessage.getChannel());
      }
    }
    if (lastMrFinanceMessage != null && System.currentTimeMillis() - lastMrFinanceMessage.getTimestamp() > 5000L) {
      lastMrFinanceMessage = null;
    }
    if (lastTitleEvent != null && System.currentTimeMillis() - lastTitleEvent.getTimestamp() > 5000L) {
      lastTitleEvent = null;
    }
  }
  
  private void tauntMrFinance(String channel) {
    bot.send().message(channel, MRFINANCE + ": " + MRFINANCE_TAUNTS.get(new Random().nextInt(MRFINANCE_TAUNTS.size())));
  }
  
}

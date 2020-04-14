package de.throughput.ircbot;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Implements per-user rate limiting for commands.
 */
@Component
public class UserRateLimiter {

  private final int maxInteractions;
  private final long checkPeriodMillis;
  private final Map<String, Set<Long>> botInteractionTimesByNick = new HashMap<>();
  
  @Autowired
  public UserRateLimiter(@Value("${ircbot.ratelimit.maxInteractions}") int maxInteractions, @Value("${ircbot.ratelimit.checkPeriodMillis}") long checkPeriodMillis) {
    this.maxInteractions = maxInteractions;
    this.checkPeriodMillis = checkPeriodMillis;
  }
  
  /**
   * Updates and checks the amount of recent interactions with {@code nick}.
   * 
   * @param nick nick
   * @return if {@code true}, the user has exceeded the limit
   */
  public boolean limit(String nick) {
    return checkInteractions(nick) > maxInteractions;
  }
  
  /**
   * Checks and updates interactions with {@code nick}.
   * 
   * @param nick nick
   * @return number of interactions with {@code nick} within the last {@link this#checkPeriodMillis}
   */
  private int checkInteractions(String nick) {
    long currentTimeMillis = System.currentTimeMillis();
    long cutOff = currentTimeMillis - checkPeriodMillis;
    synchronized (botInteractionTimesByNick) {
      Set<Long> interactionTimes = botInteractionTimesByNick.computeIfAbsent(nick, key -> new HashSet<>());
      interactionTimes.removeIf(time -> time < cutOff);
      interactionTimes.add(currentTimeMillis);
      return interactionTimes.size();
    }
  }
  
  /**
   * Removes old entries.
   */
  @Scheduled(fixedRate = 60000)
  public void evictAll() {
    long cutOff = System.currentTimeMillis() - checkPeriodMillis;
    synchronized (botInteractionTimesByNick) {
      botInteractionTimesByNick.values().stream()
          .forEach(interactionTimes -> interactionTimes.removeIf(time -> time < cutOff));
    }
  }
  
}

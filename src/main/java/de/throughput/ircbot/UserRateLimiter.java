package de.throughput.ircbot;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import de.throughput.ircbot.handler.LagerfeldAiCommandHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Implements per-user rate limiting for commands.
 */
@Component
public class UserRateLimiter {

    private static final Logger LOG = LoggerFactory.getLogger(UserRateLimiter.class);

    private final int maxInteractions;
    private final long checkPeriodMillis;
    private final long penaltyMillis;
    private final Map<String, Set<Long>> botInteractionTimesByNick = new HashMap<>();
    private final Map<String, Long> penaltyBench = new ConcurrentHashMap<>();

    private final IrcBotConfig config;

    @Autowired
    public UserRateLimiter(
            @Value("${ircbot.ratelimit.maxInteractions}") int maxInteractions,
            @Value("${ircbot.ratelimit.checkPeriodMillis}") long checkPeriodMillis,
            @Value("${ircbot.ratelimit.penaltyMillis}") long penaltyMillis,
            IrcBotConfig config) {
        this.maxInteractions = maxInteractions;
        this.checkPeriodMillis = checkPeriodMillis;
        this.penaltyMillis = penaltyMillis;
        this.config = config;
    }

    /**
     * Updates and checks the amount of recent interactions with {@code nick}.
     *
     * @param nick nick
     * @return if {@code true}, the user has exceeded the limit
     */
    public boolean limit(String nick) {
        if (config.isTestMode()) {
            return false;
        }
        if (checkInteractions(nick) > maxInteractions) {
            penaltyBench.put(nick, System.currentTimeMillis());
            LOG.info("user '{}' is rate limited", nick);
            return true;
        }
        return false;
    }

    /**
     * Tells if a user should be ignored because they exceeded the limit.
     *
     * @param nick nick
     * @return {@code true} if the user has exceeded the limit within the last {@code ircbot.ratelimit.penaltyMillis} milliseconds
     */
    public boolean ignore(String nick) {
        return System.currentTimeMillis() - penaltyBench.getOrDefault(nick, 0L) < penaltyMillis;
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
        synchronized (botInteractionTimesByNick) {
            long cutOff = System.currentTimeMillis() - checkPeriodMillis;
            botInteractionTimesByNick.values()
                    .forEach(interactionTimes -> interactionTimes.removeIf(time -> time < cutOff));
        }
    }

}

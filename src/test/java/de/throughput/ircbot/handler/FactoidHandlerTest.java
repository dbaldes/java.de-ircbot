package de.throughput.ircbot.handler;

import de.throughput.ircbot.IrcBotConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.pircbotx.Channel;
import org.pircbotx.hooks.events.MessageEvent;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.validation.constraints.NotNull;
import java.util.Set;

import static org.mockito.Mockito.*;

class FactoidHandlerTest {

    private static final String TEST_CHANNEL = "#test";

    private JdbcTemplate jdbcTemplate;
    private IrcBotConfig botConfig;

    private FactoidHandler handler;

    @BeforeEach
    void setup() {
        jdbcTemplate = Mockito.mock(JdbcTemplate.class);
        botConfig = Mockito.mock(IrcBotConfig.class);
        when(botConfig.getFactoidChannels()).thenReturn(Set.of(TEST_CHANNEL));
        handler = new FactoidHandler(jdbcTemplate, botConfig);
    }

    @Test
    void matchIsFactoid() {
        String message = "DrMockIt is so smart";
        String key = "drmockit";
        String verb = "is";
        String fact = "so smart";

        assertFactoid(message, key, verb, fact);
    }

    @Test
    void matchAreFactoid() {
        String message = "Trees are beautiful";
        String key = "trees";
        String verb = "are";
        String fact = "beautiful";

        assertFactoid(message, key, verb, fact);
    }

    @Test
    void noFactoid() {
        assertNoFactoid("yo mama is so fat");
    }

    private void assertFactoid(String message, String key, String verb, String fact) {
        // Mock keyExists to return false, indicating the factoid does not yet exist
        when(jdbcTemplate.queryForObject(
                eq("SELECT COUNT(*) FROM factoid WHERE key = ? AND verb = ?"),
                eq(Integer.class),
                eq(key), eq(verb)
        )).thenReturn(0);

        handler.onMessage(mockMessageEvent(message));

        // Assert
        verify(jdbcTemplate).update(
                eq("INSERT INTO factoid (key, verb, fact) VALUES (?, ?, ?)"),
                eq(key), eq(verb), eq(fact)
        );
    }

    private void assertNoFactoid(String message) {
        handler.onMessage(mockMessageEvent(message));

        // Assert that insertFact was not called
        verify(jdbcTemplate, never()).update(
                eq("INSERT INTO factoid (key, verb, fact) VALUES (?, ?, ?)"),
                anyString(), anyString(), anyString()
        );
    }

    @NotNull
    private static MessageEvent mockMessageEvent(String message) {
        MessageEvent event = Mockito.mock(MessageEvent.class);
        Channel channel = Mockito.mock(Channel.class);
        when(event.getChannel()).thenReturn(channel);
        when(channel.getName()).thenReturn(TEST_CHANNEL);
        when(event.getMessage()).thenReturn(message);
        return event;
    }
}

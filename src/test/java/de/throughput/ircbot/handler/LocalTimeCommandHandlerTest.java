package de.throughput.ircbot.handler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.time.ZoneId;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class LocalTimeCommandHandlerTest {

    private LocalTimeCommandHandler handler;
    private Method resolver;

    @BeforeEach
    void setUp() throws NoSuchMethodException {
        handler = new LocalTimeCommandHandler();
        resolver = LocalTimeCommandHandler.class.getDeclaredMethod("resolveZoneId", String.class);
        resolver.setAccessible(true);
    }

    @Test
    void resolvesFullIanaId() {
        Optional<ZoneId> zone = invokeResolver("Europe/Zurich");
        assertTrue(zone.isPresent());
        assertEquals(ZoneId.of("Europe/Zurich"), zone.get());
    }

    @Test
    void resolvesShortId() {
        Optional<ZoneId> zone = invokeResolver("pst");
        assertTrue(zone.isPresent());
        assertEquals(ZoneId.of("America/Los_Angeles"), zone.get());
    }

    @Test
    void resolvesUtcOffset() {
        Optional<ZoneId> zone = invokeResolver("GMT-7");
        assertTrue(zone.isPresent());
        assertEquals(ZoneId.of("-07:00"), zone.get());
    }

    @Test
    void resolvesCityName() {
        Optional<ZoneId> zone = invokeResolver("New York");
        assertTrue(zone.isPresent());
        assertEquals(ZoneId.of("America/New_York"), zone.get());
    }

    @Test
    void resolvesFuzzyCityName() {
        Optional<ZoneId> zone = invokeResolver("Zuric");
        assertTrue(zone.isPresent());
        assertEquals(ZoneId.of("Europe/Zurich"), zone.get());
    }

    @SuppressWarnings("unchecked")
    private Optional<ZoneId> invokeResolver(String input) {
        try {
            return (Optional<ZoneId>) resolver.invoke(handler, input);
        } catch (IllegalAccessException | InvocationTargetException e) {
            throw new RuntimeException(e);
        }
    }
}

package de.throughput.ircbot.integrationtest;

import lombok.Getter;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.pircbotx.Configuration;
import org.pircbotx.PircBotX;
import org.pircbotx.hooks.ListenerAdapter;
import org.pircbotx.hooks.events.ActionEvent;
import org.pircbotx.hooks.events.MessageEvent;

import java.util.Set;

import static org.assertj.core.api.Assertions.*;

class IrcBotIT {

    public static final String TESTBOT_NICK = "TestBot";
    public static final String CHANNEL = "#java.de";

    private static final TestListener listener = new TestListener();

    private static PircBotX bot;

    @BeforeAll
    static void setUp() throws Exception {
        Configuration configuration = new Configuration.Builder()
                .setName(TESTBOT_NICK)
                .addServer("localhost", 6667)
                .addAutoJoinChannel(CHANNEL)
                .addListener(listener)
                .buildConfiguration();

        bot = new PircBotX(configuration);
        new Thread(() -> {
            try {
                bot.startBot();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();

        // Give the bot some time to connect
        Thread.sleep(5000);

        // Tell the bot to join #java.de
        bot.sendIRC().message("DrMockIt", "JOIN " + CHANNEL);
        // Give it some time to clear its head
        Thread.sleep(3000);
    }

    @Test
    void testHelp() throws Exception {
        synchronized (listener) {
            listener.expectMessage(TESTBOT_NICK + ": Commands:");
            bot.sendIRC().message(CHANNEL, "!help");
            assertThat(listener.waitForMessage()).isTrue();

            assertThat(listener.getLastMessage()).contains("seen", "karma", "stock", "crypto");
        }
    }

    @Test
    void testAircraft() throws Exception {
        synchronized (listener) {
            listener.expectMessage(TESTBOT_NICK + ": https://www.flightradar24.com/data/aircraft/HB-JVN");
            bot.sendIRC().message(CHANNEL, "!aircraft HB-JVN");
            assertThat(listener.waitForMessage()).isTrue();
        }
    }

    @Test
    void testAirport() throws Exception {
        synchronized (listener) {
            listener.expectMessage(TESTBOT_NICK + ": https://flightaware.com/live/airport/CNX - https://www.flightradar24.com/airport/CNX");
            bot.sendIRC().message(CHANNEL, "!airport CNX");
            assertThat(listener.waitForMessage()).isTrue();

            listener.expectMessage(TESTBOT_NICK + ": https://flightaware.com/live/airport/LSZH");
            bot.sendIRC().message(CHANNEL, "!airport LSZH");
            assertThat(listener.waitForMessage()).isTrue();
        }
    }

    @Test
    void testAvherald() throws Exception {
        synchronized (listener) {
            listener.expectMessage(TESTBOT_NICK + ": https://avherald.com/h?");
            bot.sendIRC().message(CHANNEL, "!avherald searchterm");
            assertThat(listener.waitForMessage()).isTrue();

            assertThat(listener.getLastMessage()).contains("search_term=searchterm");
        }
    }

    @Test
    void testFlight() throws Exception {
        synchronized (listener) {
            listener.expectMessage(TESTBOT_NICK + ": https://flightaware.com/live/flight/TG970 - https://www.flightradar24.com/data/flights/TG970");
            bot.sendIRC().message(CHANNEL, "!flight TG970");
            assertThat(listener.waitForMessage()).isTrue();
        }
    }

    @Test
    void testKarma() throws Exception {
        synchronized (listener) {
            listener.expectMessage(TESTBOT_NICK + ": Vader has neutral karma.");
            bot.sendIRC().message(CHANNEL, "!karma Vader");
            assertThat(listener.waitForMessage()).isTrue();

            bot.sendIRC().message(CHANNEL, "++Vader");
            bot.sendIRC().message(CHANNEL, "Vader++");
            listener.expectMessage(TESTBOT_NICK + ": Vader has a karma of 2.");
            assertThat(listener.waitForMessage()).isTrue();

            bot.sendIRC().message(CHANNEL, "--Vader");
            bot.sendIRC().message(CHANNEL, "Vader--");
            listener.expectMessage(TESTBOT_NICK + ": Vader has neutral karma.");
            assertThat(listener.waitForMessage()).isTrue();

            bot.sendIRC().message(CHANNEL, "--Vader");
            listener.expectMessage(TESTBOT_NICK + ": Vader has a karma of -1.");
            assertThat(listener.waitForMessage()).isTrue();
        }
    }

    @Test
    void testFlip() throws Exception {
        synchronized (listener) {
            listener.expectMessage("(╯°□°）╯ ︵ ˙ƃop ʎzɐן ǝɥʇ ɹǝʌo sdɯnɾ xoɟ uʍoɹq ʞɔᴉnb ǝɥ┴");
            bot.sendIRC().message(CHANNEL, "!flip The quick brown fox jumps over the lazy dog.");
            assertThat(listener.waitForMessage()).isTrue();
        }
    }

    @Test
    void testFactoid() throws Exception {
        synchronized (listener) {
            bot.sendIRC().message(CHANNEL, "time is of the essence.");

            listener.expectMessage(TESTBOT_NICK + ": time is of the essence.");
            bot.sendIRC().message(CHANNEL, "time.");
            assertThat(listener.waitForMessage()).isTrue();

            bot.sendIRC().message(CHANNEL, "time is also money.");
            listener.expectMessage(TESTBOT_NICK + ": time is of the essence. or money.");
            bot.sendIRC().message(CHANNEL, "time.");
            assertThat(listener.waitForMessage()).isTrue();

            bot.sendIRC().message(CHANNEL, "!forget time");
            listener.expectMessage(TESTBOT_NICK + ": I forgot time.");
            assertThat(listener.waitForMessage()).isTrue();
        }
    }

    @Test
    void testSeen() throws Exception {
        synchronized (listener) {
            bot.sendIRC().message(CHANNEL, "I said something.");
            listener.expectMessage(TESTBOT_NICK + ": " + TESTBOT_NICK.toLowerCase() + " was last seen on " + CHANNEL);
            bot.sendIRC().message(CHANNEL, "!seen " + TESTBOT_NICK);
            assertThat(listener.waitForMessage()).isTrue();
            assertThat(listener.getLastMessage()).contains("saying: I said something.");
        }
    }

    @Test
    void testRoulette() throws Exception {
        synchronized (listener) {
            listener.expectMessage("spins the chambers and hands the revolver back to " + TESTBOT_NICK);
            bot.sendIRC().message(CHANNEL, "!roulette spin");
            assertThat(listener.waitForMessage()).isTrue();

            String clickMessage = TESTBOT_NICK + ": *click*";
            String bangMessage = TESTBOT_NICK + ": BANG!";
            listener.expectMessage(clickMessage, bangMessage);
            bot.sendIRC().message(CHANNEL, "!roulette");
            assertThat(listener.waitForMessage()).isTrue();

            while (clickMessage.equals(listener.getLastMessage())) {
                bot.sendIRC().message(CHANNEL, "!roulette");
                listener.expectMessage(clickMessage, bangMessage);
                assertThat(listener.waitForMessage()).isTrue();
            }
        }
        // roulette will post additional timed messages after the player lost, let's wait for them to pass.
        Thread.sleep(3000);
    }

    @Test
    void testSlogan() throws Exception {
        synchronized (listener) {
            listener.expectMessage(TESTBOT_NICK + ": Of course, comrade!");
            bot.sendIRC().message(CHANNEL, "!addslogan code and conquer!");
            assertThat(listener.waitForMessage()).isTrue();

            listener.expectMessage(TESTBOT_NICK + ": I know, comrade, I know!");
            bot.sendIRC().message(CHANNEL, "!addslogan code and conquer!");
            assertThat(listener.waitForMessage()).isTrue();

            listener.expectMessage("code and conquer!");
            bot.sendIRC().message(CHANNEL, "!slogan");
            assertThat(listener.waitForMessage()).isTrue();

            listener.expectMessage(TESTBOT_NICK + ": Forget those lies!");
            bot.sendIRC().message(CHANNEL, "!rmslogan code and conquer!");
            assertThat(listener.waitForMessage()).isTrue();

            listener.expectMessage(TESTBOT_NICK + ": Slogan not found.");
            bot.sendIRC().message(CHANNEL, "!rmslogan code and conquer!");
            assertThat(listener.waitForMessage()).isTrue();
        }
    }

    @Getter
    private static class TestListener extends ListenerAdapter {

        private static final int WAIT_EXPECTED_MESSAGE_MS = 2000;

        private String lastMessage;
        private Set<String> expectedMessages;
        private volatile boolean messageReceived = false;

        @Override
        public synchronized void onMessage(MessageEvent event) throws Exception {
            testMessage(event.getMessage());
        }

        @Override
        public synchronized void onAction(ActionEvent event) throws Exception {
            testMessage(event.getMessage());
        }

        private void testMessage(String message) {
            if (message != null) {
                lastMessage = message;
                for (var expected : expectedMessages) {
                    if (message.startsWith(expected)) {
                        messageReceived = true;
                        notifyAll();
                        return;
                    }
                }
            }
        }

        public synchronized void expectMessage(String ... message) {
            expectedMessages = Set.of(message);
            messageReceived = false;
        }

        public synchronized boolean waitForMessage() throws InterruptedException {
            wait(WAIT_EXPECTED_MESSAGE_MS);
            return messageReceived;
        }
    }
}

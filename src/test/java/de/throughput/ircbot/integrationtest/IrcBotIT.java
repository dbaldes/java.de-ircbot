package de.throughput.ircbot.integrationtest;

import lombok.Getter;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.pircbotx.Configuration;
import org.pircbotx.PircBotX;
import org.pircbotx.hooks.ListenerAdapter;
import org.pircbotx.hooks.events.MessageEvent;

import static org.assertj.core.api.Assertions.*;

class IrcBotIT {

    public static final String TESTBOT_NICK = "TestBot";
    public static final String CHANNEL = "#java.de";
    private static PircBotX bot;
    private static TestListener listener;

    @BeforeAll
    static void setUp() throws Exception {
        listener = new TestListener();

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
        bot.sendIRC().message(CHANNEL, "!help");
        Thread.sleep(1000);

        assertThat(listener.getLastMessage()).startsWith(TESTBOT_NICK + ": Commands:");
        // just check for some
        assertThat(listener.getLastMessage()).contains("seen","karma","stock","crypto");
    }

    @Test
    void testAvherald() throws Exception {
        bot.sendIRC().message(CHANNEL, "!avherald searchterm");
        Thread.sleep(1000);

        assertThat(listener.getLastMessage()).startsWith(TESTBOT_NICK + ": https://avherald.com/h?");
        assertThat(listener.getLastMessage()).contains("search_term=searchterm");
    }

    @Test
    void testKarma() throws Exception {
        bot.sendIRC().message(CHANNEL, "!karma Vader");
        Thread.sleep(500);

        assertThat(listener.getLastMessage()).isEqualTo(TESTBOT_NICK + ": Vader has neutral karma.");

        bot.sendIRC().message(CHANNEL, "++Vader");
        bot.sendIRC().message(CHANNEL, "Vader++");
        Thread.sleep(500);

        assertThat(listener.getLastMessage()).isEqualTo(TESTBOT_NICK + ": Vader has a karma of 2.");

        bot.sendIRC().message(CHANNEL, "--Vader");
        bot.sendIRC().message(CHANNEL, "Vader--");
        Thread.sleep(500);

        assertThat(listener.getLastMessage()).isEqualTo(TESTBOT_NICK + ": Vader has neutral karma.");

        bot.sendIRC().message(CHANNEL, "--Vader");
        Thread.sleep(500);

        assertThat(listener.getLastMessage()).isEqualTo(TESTBOT_NICK + ": Vader has a karma of -1.");
    }

    @Test
    void testFlip() throws Exception {
        bot.sendIRC().message(CHANNEL, "!flip The quick brown fox jumps over the lazy dog.");
        Thread.sleep(500);
        assertThat(listener.getLastMessage()).isEqualTo("(╯°□°）╯ ︵ ˙ƃop ʎzɐן ǝɥʇ ɹǝʌo sdɯnɾ xoɟ uʍoɹq ʞɔᴉnb ǝɥ┴");
    }

    @Test
    void testFactoid() throws Exception {
        bot.sendIRC().message(CHANNEL, "time is of the essence.");
        Thread.sleep(500);

        bot.sendIRC().message(CHANNEL, "time.");
        Thread.sleep(500);

        assertThat(listener.getLastMessage()).isEqualTo(TESTBOT_NICK + ": time is of the essence.");

        bot.sendIRC().message(CHANNEL, "time is also money.");
        Thread.sleep(500);

        bot.sendIRC().message(CHANNEL, "time.");
        Thread.sleep(500);

        assertThat(listener.getLastMessage()).isEqualTo(TESTBOT_NICK + ": time is of the essence. or money.");

        bot.sendIRC().message(CHANNEL, "!forget time");
        Thread.sleep(500);

        assertThat(listener.getLastMessage()).isEqualTo(TESTBOT_NICK + ": I forgot time.");
    }


    @Test
    void testSeen() throws Exception {
        bot.sendIRC().message(CHANNEL, "I said something.");
        Thread.sleep(500);
        bot.sendIRC().message(CHANNEL, "!seen " + TESTBOT_NICK);
        Thread.sleep(500);

        assertThat(listener.getLastMessage())
                .startsWith(TESTBOT_NICK + ": " + TESTBOT_NICK.toLowerCase() + " was last seen on " + CHANNEL)
                .contains("saying: I said something.");
    }

    @Getter
    private static class TestListener extends ListenerAdapter {

        private String lastMessage;

        @Override
        public void onMessage(MessageEvent event) throws Exception {
            lastMessage = event.getMessage();
        }

    }
}

package de.throughput.ircbot;

import com.google.api.services.youtube.YouTube;
import com.google.api.services.youtube.YouTubeRequestInitializer;
import com.theokanning.openai.service.OpenAiService;
import org.apache.commons.lang3.StringUtils;
import org.pircbotx.Configuration;
import org.pircbotx.PircBotX;
import org.pircbotx.UtilSSLSocketFactory;
import org.pircbotx.cap.TLSCapHandler;
import org.pircbotx.delay.StaticDelay;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;

import javax.net.ssl.SSLSocketFactory;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.time.Duration;

@SpringBootApplication
@EnableScheduling
public class IrcBotMain {

    private static final String REALNAME = "Dr. Ashoka Mockit";
    private static final String VERSION = "0.9.0";
    private static final int DELAY_RECONNECT_MS = 5000;

    public static void main(String[] args) {
        SpringApplication.run(IrcBotMain.class, args);
    }

    @Bean
    public CommandLineRunner configure(PircBotX bot) {
        return (String... arguments) -> {
            try (bot) {
                bot.startBot();
            }
        };
    }

    @Bean
    PircBotX bot(Configuration configuration) {
        return new PircBotX(configuration);
    }

    /**
     * Create the pircbotx configuration.
     */
    @Bean
    Configuration botConfig(IrcBotConfig botConfig, IrcBotControlListener cmdListener, IrcBotConversationListener convListener,
            AdminCommandRunner adminCommandRunner) {
        validate(botConfig);

        Configuration.Builder config = new Configuration.Builder().setAutoNickChange(true)
                .setAutoReconnect(true)
                .setAutoReconnectDelay(new StaticDelay(DELAY_RECONNECT_MS))
                .setVersion(VERSION)
                .setRealName(REALNAME)
                .addListener(cmdListener)
                .addListener(convListener)
                .addListener(adminCommandRunner)
                .addServer(botConfig.getServer(), botConfig.getPort())
                .setName(botConfig.getNick())
                .setLogin(botConfig.getNick())
                .setNickservPassword(botConfig.getNickservPassword())
                .addAutoJoinChannels(botConfig.getChannels())
                .setNickservDelayJoin(true);

        if (botConfig.isSsl() || botConfig.isTls()) {
            SSLSocketFactory socketFactory = null;
            if (botConfig.isTrustAll()) {
                socketFactory = new UtilSSLSocketFactory().trustAllCertificates();
            } else {
                socketFactory = (SSLSocketFactory) SSLSocketFactory.getDefault();
            }

            if (botConfig.isSsl()) {
                config.setSocketFactory(socketFactory);
            } else {
                config.addCapHandler(new TLSCapHandler(socketFactory, false));
            }
        }
        return config.buildConfiguration();
    }

    /**
     * Checks mandatory configuration values.
     *
     * @param botConfig config
     */
    private void validate(IrcBotConfig botConfig) {
        if (StringUtils.isEmpty(botConfig.getServer())) {
            throw new IllegalArgumentException("must give a server");
        }
        if (StringUtils.isEmpty(botConfig.getNick())) {
            throw new IllegalArgumentException("must give a nick");
        }
        if (botConfig.getChannels() == null || botConfig.getChannels().isEmpty()) {
            throw new IllegalArgumentException("must give one or more channels");
        }
        if (botConfig.isSsl() && botConfig.isTls()) {
            throw new IllegalArgumentException("choose one of SSL or TLS");
        }
    }

    @Bean
    public YouTube youtube(@Value("${youtube.api.key}") String apiKey) throws GeneralSecurityException, IOException {
        return new YouTube.Builder(com.google.api.client.googleapis.javanet.GoogleNetHttpTransport.newTrustedTransport(),
                com.google.api.client.json.jackson2.JacksonFactory.getDefaultInstance(), null).setApplicationName("ircbot")
                .setYouTubeRequestInitializer(new YouTubeRequestInitializer(apiKey))
                .build();
    }

    @Bean
    public OpenAiService openAiService(@Value("${openai.apiKey}") String apiKey) {
        return new OpenAiService(apiKey, Duration.ofSeconds(20));
    }

}

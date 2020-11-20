package de.throughput.ircbot;

import java.io.IOException;
import java.security.GeneralSecurityException;

import javax.net.ssl.SSLSocketFactory;

import org.pircbotx.Configuration;
import org.pircbotx.PircBotX;
import org.pircbotx.UtilSSLSocketFactory;
import org.pircbotx.cap.TLSCapHandler;
import org.springframework.amqp.rabbit.annotation.EnableRabbit;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;

import com.google.api.services.youtube.YouTube;
import com.google.api.services.youtube.YouTubeRequestInitializer;

import twitter4j.Twitter;
import twitter4j.TwitterFactory;
import twitter4j.conf.ConfigurationBuilder;

@SpringBootApplication
@EnableRabbit
@EnableScheduling
public class IrcBotMain {

  private static final int IRC_DEFAULT_PORT = 6667;
  private static final String VERSION = "break it fix it";
  private static final int DELAY_RECONNECT_MS = 5000;

  public static void main(String[] args) {
    SpringApplication.run(IrcBotMain.class, args);
  }

  @Bean
  public CommandLineRunner configure(Configuration configuration) {
    
    return (String ... arguments) -> {
      try (PircBotX bot = new PircBotX(configuration)) {
        bot.startBot();
      }
    };
  }

  /**
   * Create the pircbotx configuration.
   * 
   * @param args command line arguments
   * @param cmdListener
   * @param urlProcessor
   * @return bot config
   */
  @Bean
  Configuration botConfig(ApplicationArguments args,
      IrcBotControlListener cmdListener,
      IrcBotConversationListener convListener) {
    validate(args);
    
    String nick = args.getOptionValues("nick").get(0);
    String server = args.getOptionValues("server").get(0);
    int port = args.containsOption("port") ? Integer.parseInt(args.getOptionValues("port").get(0)) : IRC_DEFAULT_PORT;
    
    Configuration.Builder config = new Configuration.Builder()
        .setAutoNickChange(true)
        .setAutoReconnect(true)
        .setAutoReconnectDelay(DELAY_RECONNECT_MS)
        .setVersion(VERSION)
        .setRealName(VERSION)
        .addListener(cmdListener)
        .addListener(convListener)
        .addServer(server, port)
        .setName(nick)
        .setLogin(nick)
        .addAutoJoinChannels(args.getOptionValues("channel"));

    if (args.containsOption("identify")) {
      String[] id = args.getOptionValues("identify").get(0).split(":", 2);
      if (id.length != 2) {
        throw new IllegalArgumentException("identify must be <account:password>");
      }
      config.setNickservPassword(id[1]);
    } else if (args.containsOption("nickservPassword")) {
      config.setNickservPassword(args.getOptionValues("nickservPassword").get(0));
    }
    
    SSLSocketFactory socketFactory = null;
    if (args.containsOption("trustAll")) {
      socketFactory = new UtilSSLSocketFactory().trustAllCertificates();
    } else {
      socketFactory = (SSLSocketFactory) SSLSocketFactory.getDefault();
    }
    
    if (args.containsOption("ssl")) {
      config.setSocketFactory(socketFactory);
    } else if (args.containsOption("tls")) {
      config.addCapHandler(new TLSCapHandler(socketFactory, true));
    }
    Configuration configuration = config.buildConfiguration();
    return configuration;
  }

  /**
   * Checks mandatory arguments.
   * 
   * @param args arguments
   */
  private void validate(ApplicationArguments args) {
    if (!args.containsOption("server") || args.getOptionValues("server").size() != 1) {
      throw new IllegalArgumentException("must give exactly one server");
    }
    if (!args.containsOption("nick") || args.getOptionValues("nick").size() != 1) {
      throw new IllegalArgumentException("must give exactly one nick");
    }
    if (!args.containsOption("channel") || args.getOptionValues("channel").size() < 1) {
      throw new IllegalArgumentException("must give one or more channels");
    }
  }

  @Bean
  public MessageConverter jsonMessageConverter(){
    return new Jackson2JsonMessageConverter();
  }

  @Bean
  public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory) {
    RabbitTemplate template = new RabbitTemplate(connectionFactory);
    template.setMessageConverter(jsonMessageConverter());
    return template;
  }
  
  @Bean
  public YouTube youtube(@Value("${youtube.api.key}") String apiKey) throws GeneralSecurityException, IOException {
    YouTube youTube = new YouTube.Builder(com.google.api.client.googleapis.javanet.GoogleNetHttpTransport.newTrustedTransport(),
        com.google.api.client.json.jackson2.JacksonFactory.getDefaultInstance(), null)
        .setApplicationName("ircbot")
        .setYouTubeRequestInitializer(new YouTubeRequestInitializer(apiKey))
        .build();
    
    return youTube;
  }

  @Bean
  public Twitter twitter(@Value("${twitter.apiKey}") String apiKey, @Value("${twitter.apiSecretKey}") String apiSecretKey, @Value("${twitter.accessToken}") String accessToken, @Value("${twitter.accessTokenSecret}") String accessTokenSecret) {
    ConfigurationBuilder cb = new ConfigurationBuilder();
    cb.setOAuthConsumerKey(apiKey)
      .setOAuthConsumerSecret(apiSecretKey)
      .setOAuthAccessToken(accessToken)
      .setOAuthAccessTokenSecret(accessTokenSecret);
    
    TwitterFactory tf = new TwitterFactory(cb.build());
    return tf.getInstance();
  }
  
}

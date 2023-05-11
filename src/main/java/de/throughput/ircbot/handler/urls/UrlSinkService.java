package de.throughput.ircbot.handler.urls;

import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import de.throughput.urlcollector.api.UrlMessage;

/**
 * Sends URLs to the message queue for storage.
 */
@Component
public class UrlSinkService {

    private RabbitTemplate rabbit;

    @Autowired
    public UrlSinkService(RabbitTemplate rabbit) {
        this.rabbit = rabbit;
    }

    public void processUrl(String server, String channel, String sender, String login, String hostname, String url) {
        UrlMessage message = new UrlMessage(url, "irc", String.format("%s:%s:%s:%s:%s", server, channel, sender, login, hostname));
        this.rabbit.convertAndSend(UrlMessage.TOPIC_EXCHANGE_NAME, UrlMessage.ROUTING_KEY, message);
    }

}

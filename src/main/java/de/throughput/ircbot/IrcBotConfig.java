package de.throughput.ircbot;

import java.util.List;
import java.util.Set;
import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Holds configuration values.
 */
@Component
@Getter
public class IrcBotConfig {

    private static final int IRC_DEFAULT_PORT = 6667;

    /**
     * Server address.
     */
    @Value("${ircbot.irc.server}")
    private String server;

    /**
     * Server port.
     */
    @Value("${ircbot.irc.port}")
    private int port = IRC_DEFAULT_PORT;

    /**
     * Use SSL.
     */
    @Value("${ircbot.irc.ssl}")
    private boolean ssl;

    /**
     * Use TLS.
     */
    @Value("${ircbot.irc.tls}")
    private boolean tls;

    /**
     * Trust invalid certificates.
     */
    @Value("${ircbot.irc.ssl.trustAll}")
    private boolean trustAll;

    /**
     * Bot nick.
     */
    @Value("${ircbot.irc.nick}")
    private String nick;

    /**
     * Nickserv password.
     */
    @Value("#{'${ircbot.irc.nickserv.password}' == '' ? null : '${ircbot.irc.nickserv.password}'}")
    private String nickservPassword;

    /**
     * Channels to join.
     */
    @Value("#{'${ircbot.irc.channels}'.split(',')}")
    private List<String> channels;

    /**
     * Channels on which the bot is allowed to talk.
     */
    @Value("#{'${ircbot.talkchannels}'.split(',')}")
    private Set<String> talkChannels;

    /**
     * Channels on which the factoid system is active.
     */
    @Value("#{'${ircbot.factoid.channels}'.split(',')}")
    private Set<String> factoidChannels;

    /**
     * Admin nicks.
     */
    @Value("#{'${ircbot.admins}'.split(',')}")
    private Set<String> admins;

    /**
     * If true, authentication and rate limiting is disabled for integration testing.
     */
    @Value("${ircbot.testmode}")
    private boolean testMode;
}

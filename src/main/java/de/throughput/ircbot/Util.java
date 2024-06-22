package de.throughput.ircbot;

import java.math.BigInteger;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Utility methods.
 */
public final class Util {

    private Util() {
    }

    /**
     * URL-encodes the argument.
     *
     * @param input input
     * @return encoded input
     */
    public static String urlEnc(String input) {
        return URLEncoder.encode(input, StandardCharsets.UTF_8);
    }
}

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

  private Util() { }
  
  /**
   * Hashes the given string's bytes and returns the hash as BigInteger.
   * 
   * @param input string
   * @return hash
   */
  public static BigInteger sha256(String input) {
    return new BigInteger(getDigest("SHA-256").digest(input.getBytes()));
  }
  
  
  /**
   * Get MessageDigest for the given algorithm with unchecked exception.
   * 
   * @param algorithm algorithm
   * @return digest
   */
  private static MessageDigest getDigest(String algorithm) {
    try {
      return MessageDigest.getInstance(algorithm);
    } catch (NoSuchAlgorithmException e) {
      throw new RuntimeException(e);
    }
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

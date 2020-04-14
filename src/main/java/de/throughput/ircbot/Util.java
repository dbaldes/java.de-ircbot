package de.throughput.ircbot;

import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class Util {

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
}

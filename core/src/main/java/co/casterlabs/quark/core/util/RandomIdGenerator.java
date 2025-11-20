package co.casterlabs.quark.core.util;

import java.security.SecureRandom;

public class RandomIdGenerator {
    public static final SecureRandom RANDOM = new SecureRandom();
    public static final char[] ALPHABET = "_-0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ".toCharArray();

    public static String generate(int length) {
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(ALPHABET[RANDOM.nextInt(ALPHABET.length)]);
        }
        return sb.toString();
    }

}

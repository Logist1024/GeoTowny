package com.geotowny.web;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;

public final class WebSecurity {

    private static final int ITERATIONS = 10_000;
    private static final int SALT_BYTES = 16;
    private static final SecureRandom RANDOM = new SecureRandom();

    private WebSecurity() {
    }

    public static String hashPassword(String password) {
        byte[] salt = new byte[SALT_BYTES];
        RANDOM.nextBytes(salt);
        String hash = hash(password, salt);
        return "sha256$" + ITERATIONS + "$" + hex(salt) + "$" + hash;
    }

    public static boolean verifyPassword(String password, String stored) {
        if (password == null || stored == null) {
            return false;
        }
        String[] parts = stored.split("\\$");
        if (parts.length != 4 || !"sha256".equals(parts[0])) {
            return false;
        }
        try {
            int iterations = Integer.parseInt(parts[1]);
            byte[] salt = unhex(parts[2]);
            String actual = hash(password, salt, iterations);
            return MessageDigest.isEqual(actual.getBytes(StandardCharsets.UTF_8),
                    parts[3].getBytes(StandardCharsets.UTF_8));
        } catch (RuntimeException e) {
            return false;
        }
    }

    public static String randomToken(int bytes) {
        byte[] data = new byte[Math.max(8, bytes)];
        RANDOM.nextBytes(data);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(data);
    }

    private static String hash(String password, byte[] salt) {
        return hash(password, salt, ITERATIONS);
    }

    private static String hash(String password, byte[] salt, int iterations) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(salt);
            byte[] hash = digest.digest(password.getBytes(StandardCharsets.UTF_8));
            for (int i = 1; i < Math.max(1, iterations); i++) {
                digest.reset();
                hash = digest.digest(hash);
            }
            return hex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 不可用", e);
        }
    }

    private static String hex(byte[] bytes) {
        StringBuilder builder = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) {
            builder.append(Character.forDigit((value >> 4) & 0xF, 16));
            builder.append(Character.forDigit(value & 0xF, 16));
        }
        return builder.toString();
    }

    private static byte[] unhex(String value) {
        if (value.length() % 2 != 0) {
            throw new IllegalArgumentException("非法十六进制");
        }
        byte[] data = new byte[value.length() / 2];
        for (int i = 0; i < data.length; i++) {
            data[i] = (byte) ((Character.digit(value.charAt(i * 2), 16) << 4)
                    + Character.digit(value.charAt(i * 2 + 1), 16));
        }
        return data;
    }
}
package com.geotowny.web;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class WebSecurityTest {

    @Test
    void hashesAndVerifiesPasswords() {
        String stored = WebSecurity.hashPassword("secret-123");
        assertThat(stored).startsWith("sha256$10000$");
        assertThat(WebSecurity.verifyPassword("secret-123", stored)).isTrue();
        assertThat(WebSecurity.verifyPassword("wrong", stored)).isFalse();
        assertThat(WebSecurity.verifyPassword("secret-123", "bad-format")).isFalse();
        assertThat(WebSecurity.verifyPassword(null, stored)).isFalse();
    }

    @Test
    void generatesUniqueUrlSafeTokens() {
        String first = WebSecurity.randomToken(32);
        String second = WebSecurity.randomToken(32);
        assertThat(first).hasSizeGreaterThan(20);
        assertThat(first).isNotEqualTo(second);
        assertThat(first).matches("[A-Za-z0-9_-]+");
    }
}
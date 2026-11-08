package com.geotowny.web;

import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class WebAssetsTest {

    @Test
    void bundlesExternalPageAssets() throws Exception {
        assertThat(resource("web/index.html")).contains("/app.js", "/style.css", "login-form");
        assertThat(resource("web/app.js")).contains("/api/player", "X-CSRF-Token");
        assertThat(resource("web/style.css")).contains("--bg", ".card");
    }

    private static String resource(String name) throws Exception {
        try (InputStream input = WebAssetsTest.class.getClassLoader().getResourceAsStream(name)) {
            assertThat(input).as(name).isNotNull();
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
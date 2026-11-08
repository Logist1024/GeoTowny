package com.geotowny.web;

import com.geotowny.Log;
import com.geotowny.GeoTownyPlugin;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public final class WebAssetManager {

    private static final List<String> DEFAULT_ASSETS = List.of("index.html", "app.js", "style.css");
    private static final int MAX_ASSET_BYTES = 2 * 1024 * 1024;

    private final GeoTownyPlugin plugin;
    private final Path webRoot;

    public WebAssetManager(GeoTownyPlugin plugin) {
        this.plugin = plugin;
        this.webRoot = plugin.getDataFolder().toPath().resolve("web");
    }

    public void ensureAssets() {
        try {
            Files.createDirectories(webRoot);
            for (String name : DEFAULT_ASSETS) {
                Path target = webRoot.resolve(name);
                if (Files.exists(target)) {
                    continue;
                }
                try (InputStream input = plugin.getResource("web/" + name)) {
                    if (input == null) {
                        Log.warn("缺少内置 Web 资源: {}", name);
                        continue;
                    }
                    Files.writeString(target, new String(input.readAllBytes(), StandardCharsets.UTF_8));
                }
            }
        } catch (IOException e) {
            Log.error("初始化 Web 资源失败: {}", e.getMessage());
        }
    }

    public String load(String requestPath) {
        String relative = normalize(requestPath);
        if (relative == null) {
            return null;
        }
        Path file = webRoot.resolve(relative).normalize();
        if (!file.startsWith(webRoot)) {
            return null;
        }
        try {
            if (Files.exists(file) && Files.size(file) <= MAX_ASSET_BYTES) {
                return Files.readString(file);
            }
        } catch (IOException e) {
            Log.warn("读取 Web 资源失败 {}: {}", relative, e.getMessage());
        }
        try (InputStream input = plugin.getResource("web/" + relative)) {
            if (input == null) {
                return null;
            }
            byte[] data = input.readAllBytes();
            return data.length > MAX_ASSET_BYTES ? null : new String(data, StandardCharsets.UTF_8);
        } catch (IOException e) {
            return null;
        }
    }

    public Path webRoot() {
        return webRoot;
    }

    private static String normalize(String requestPath) {
        if (requestPath == null) {
            return null;
        }
        String path = requestPath;
        if (path.equals("/") || path.isEmpty()) {
            path = "index.html";
        }
        while (path.startsWith("/")) {
            path = path.substring(1);
        }
        if (path.isEmpty() || path.contains("..") || path.contains("\\") || path.contains(":")) {
            return null;
        }
        return path;
    }
}
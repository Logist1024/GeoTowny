package com.geotowny.web;

import com.geotowny.GeoTownyPlugin;
import com.geotowny.Log;
import com.geotowny.config.PluginConfig;
import com.geotowny.core.GeoTownyCore;
import com.geotowny.domain.market.Listing;
import com.geotowny.domain.market.MarketItem;
import com.geotowny.domain.model.Ideology;
import com.geotowny.domain.model.Nation;
import com.geotowny.domain.model.Resident;
import com.geotowny.domain.model.Town;
import com.geotowny.domain.model.TownLog;
import com.geotowny.domain.money.Money;
import com.geotowny.service.FreeMarketService;
import com.geotowny.service.MarketService;
import com.geotowny.service.NationService;
import com.geotowny.service.ResidentService;
import com.geotowny.service.TownLogService;
import com.geotowny.service.TownService;
import com.geotowny.util.Result;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;

public final class WebService {

    private static final String COOKIE_NAME = "geotowny_token";
    private static final String CSRF_HEADER = "X-CSRF-Token";
    private static final int MAX_JSON_BYTES = 32 * 1024;
    private static final long CALL_TIMEOUT_MS = 5000L;

    private final GeoTownyPlugin plugin;
    private final GeoTownyCore core;
    private final PluginConfig config;
    private final WebAssetManager assets;
    private final Gson gson = new GsonBuilder().disableHtmlEscaping().create();
    private final SecureRandom random = new SecureRandom();

    private final Map<String, Session> sessions = new ConcurrentHashMap<>();
    private final Map<String, OneTimeToken> oneTimeTokens = new ConcurrentHashMap<>();
    private final Map<String, LoginAttempt> loginAttempts = new ConcurrentHashMap<>();

    private HttpServer server;
    private ExecutorService executor;
    private volatile boolean running;

    public WebService(GeoTownyPlugin plugin, GeoTownyCore core, PluginConfig config, WebAssetManager assets) {
        this.plugin = plugin;
        this.core = core;
        this.config = config;
        this.assets = assets;
    }

    public void start() {
        if (running || !config.webEnabled()) {
            return;
        }
        assets.ensureAssets();
        try {
            this.server = HttpServer.create(new InetSocketAddress(config.webBind(), config.webPort()), 0);
            server.createContext("/api/", this::handleApi);
            server.createContext("/auth", this::handleOneTimeLogin);
            server.createContext("/", this::handleStatic);
            this.executor = Executors.newFixedThreadPool(Math.max(1, config.webThreadPoolSize()), runnable -> {
                Thread thread = new Thread(runnable, "GeoTowny-Web");
                thread.setDaemon(true);
                return thread;
            });
            server.setExecutor(executor);
            server.start();
            running = true;
            Log.info("Web 服务已启动: {}（绑定 {}:{}，线程 {}）",
                    baseUrl(), config.webBind(), config.webPort(), config.webThreadPoolSize());
        } catch (IOException e) {
            Log.error("Web 服务启动失败: {}", e.getMessage());
            stop();
        }
    }

    public void stop() {
        running = false;
        if (server != null) {
            server.stop(2);
            server = null;
        }
        if (executor != null) {
            executor.shutdownNow();
            executor = null;
        }
        sessions.clear();
        oneTimeTokens.clear();
        loginAttempts.clear();
    }

    public boolean isRunning() {
        return running;
    }

    public String baseUrl() {
        String configured = config.webUrl();
        if (configured != null && !configured.isBlank()) {
            return configured.replaceAll("/+$", "");
        }
        String bind = config.webBind();
        if (bind == null || bind.isBlank() || bind.equals("0.0.0.0") || bind.equals("::")) {
            bind = "127.0.0.1";
        }
        return "http://" + bind + ":" + config.webPort();
    }

    public String issueOneTimeToken(UUID playerUuid) {
        String token = WebSecurity.randomToken(32);
        oneTimeTokens.put(token, new OneTimeToken(playerUuid,
                System.currentTimeMillis() + config.webOneTimeExpiryMinutes() * 60_000L));
        return baseUrl() + "/auth?token=" + URLEncoder.encode(token, StandardCharsets.UTF_8);
    }

    public boolean setPassword(UUID uuid, String password) {
        Resident resident = core.getResident(uuid);
        if (resident == null) {
            return false;
        }
        resident.setWebPassword(WebSecurity.hashPassword(password));
        core.saveResident(resident);
        return true;
    }

    public void clearPassword(UUID uuid) {
        Resident resident = core.getResident(uuid);
        if (resident != null) {
            resident.setWebPassword(null);
            core.saveResident(resident);
        }
    }

    public void revokeSessions(UUID uuid) {
        sessions.entrySet().removeIf(entry -> entry.getValue().playerUuid.equals(uuid));
    }

    public ChatBuffer chatBuffer() {
        return ChatBuffer.getInstance();
    }

    private void handleApi(HttpExchange exchange) throws IOException {
        applySecurityHeaders(exchange);
        if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
            handleOptions(exchange);
            return;
        }
        String path = exchange.getRequestURI().getPath();
        try {
            switch (path) {
                case "/api/info" -> info(exchange);
                case "/api/auth/password" -> passwordLogin(exchange);
                case "/api/auth/logout" -> logout(exchange);
                case "/api/player" -> player(exchange);
                case "/api/inventory" -> inventory(exchange);
                case "/api/chat" -> chat(exchange);
                case "/api/sendchat" -> sendChat(exchange);
                case "/api/towns" -> towns(exchange);
                case "/api/nations" -> nations(exchange);
                case "/api/town/detail" -> townDetail(exchange);
                case "/api/nation/detail" -> nationDetail(exchange);
                case "/api/town/logs" -> townLogs(exchange);
                case "/api/town/settings" -> townSettings(exchange);
                case "/api/nation/settings" -> nationSettings(exchange);
                case "/api/town/treasury" -> townTreasury(exchange);
                case "/api/nation/treasury" -> nationTreasury(exchange);
                case "/api/town/join" -> townJoin(exchange);
                case "/api/player/transfer" -> transfer(exchange);
                case "/api/player/switch-town" -> switchTown(exchange);
                case "/api/market" -> market(exchange);
                case "/api/market/buy" -> marketTrade(exchange, true);
                case "/api/market/sell" -> marketTrade(exchange, false);
                case "/api/freemarket" -> freeMarket(exchange);
                case "/api/freemarket/list" -> freeMarketList(exchange);
                case "/api/freemarket/buy" -> freeMarketBuy(exchange);
                case "/api/freemarket/cancel" -> freeMarketCancel(exchange);
                default -> {
                    if (path.startsWith("/api/map/")) {
                        mapProxy(exchange);
                    } else {
                        sendError(exchange, 404, "Not Found");
                    }
                }
            }
        } catch (TimeoutException e) {
            sendError(exchange, 503, "服务器响应超时");
        } catch (Exception e) {
            Log.error("Web 请求失败 {}: {}", path, e.getMessage());
            sendError(exchange, 500, "内部服务器错误");
        }
    }

    private void info(HttpExchange exchange) throws Exception {
        if (!requireMethod(exchange, "GET")) {
            return;
        }
        Session session = session(exchange);
        Map<String, Object> body = runSync(() -> {
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("name", "GeoTowny");
            data.put("version", plugin.getDescription().getVersion());
            data.put("online", Bukkit.getOnlinePlayers().size());
            data.put("residents", core.allResidents().size());
            data.put("towns", core.allTowns().size());
            data.put("nations", core.allNations().size());
            return data;
        });
        if (session != null) {
            body.put("csrf", session.csrfToken);
            body.put("player", nameOf(session.playerUuid));
        }
        sendJson(exchange, 200, body);
    }

    private void passwordLogin(HttpExchange exchange) throws Exception {
        if (!requireMethod(exchange, "POST")) {
            return;
        }
        if (!originAllowed(exchange)) {
            sendError(exchange, 403, "Origin 不被允许");
            return;
        }
        JsonObject body = readJson(exchange);
        String username = string(body, "username");
        String password = string(body, "password");
        if (username == null || username.isBlank() || password == null || password.isEmpty()) {
            sendError(exchange, 400, "用户名或密码错误");
            return;
        }
        String ip = exchange.getRemoteAddress().getAddress().getHostAddress();
        String attemptKey = ip + ":" + username.toLowerCase(Locale.ROOT);
        LoginAttempt attempt = loginAttempts.computeIfAbsent(attemptKey, key -> new LoginAttempt());
        if (attempt.lockUntil > System.currentTimeMillis()) {
            sendError(exchange, 429, "登录尝试过多，请稍后再试");
            return;
        }
        Resident resident = core.getResidentByName(username);
        boolean valid = resident != null && resident.getWebPassword() != null
                && !resident.getWebPassword().isBlank()
                && WebSecurity.verifyPassword(password, resident.getWebPassword());
        if (!valid) {
            attempt.failures++;
            if (attempt.failures >= config.webLoginMaxAttempts()) {
                attempt.failures = 0;
                attempt.lockUntil = System.currentTimeMillis() + config.webLoginLockMinutes() * 60_000L;
            }
            sendError(exchange, 401, "用户名或密码错误");
            return;
        }
        LoginAttempt success = loginAttempts.get(attemptKey);
        if (success != null) {
            success.failures = 0;
            success.lockUntil = 0L;
        }
        Session session = createSession(resident.getUuid());
        setSessionCookie(exchange, session.token);
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", true);
        response.put("player", resident.getName());
        response.put("csrf", session.csrfToken);
        sendJson(exchange, 200, response);
    }

    private void logout(HttpExchange exchange) throws Exception {
        if (!requireMethod(exchange, "POST")) {
            return;
        }
        Session session = requireSession(exchange);
        if (session == null || !requireCsrf(exchange, session)) {
            return;
        }
        sessions.remove(session.token);
        exchange.getResponseHeaders().set("Set-Cookie", COOKIE_NAME + "=; Path=/; Max-Age=0; HttpOnly; SameSite=Strict");
        sendJson(exchange, 200, Map.of("success", true));
    }

    private void player(HttpExchange exchange) throws Exception {
        if (!requireMethod(exchange, "GET")) {
            return;
        }
        Session session = requireSession(exchange);
        if (session == null) {
            return;
        }
        Map<String, Object> body = runSync(() -> {
            Resident resident = core.getResident(session.playerUuid);
            if (resident == null) {
                return null;
            }
            Town town = resident.hasTown() ? core.getTown(resident.getTownUuid()) : null;
            Nation nation = town != null && town.hasNation() ? core.getNation(town.getNationUuid()) : null;
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("name", resident.getName());
            data.put("uuid", resident.getUuidString());
            data.put("balance", resident.getBalance().doubleValue());
            data.put("town", town == null ? "" : town.getName());
            data.put("nation", nation == null ? "" : nation.getName());
            data.put("townRank", resident.getTownRank() == null ? "" : resident.getTownRank().name());
            data.put("nationRank", resident.getNationRank() == null ? "" : resident.getNationRank().name());
            data.put("lastOnline", resident.getLastOnline());
            return data;
        });
        if (body == null) {
            sendError(exchange, 401, "玩家数据不存在");
            return;
        }
        body.put("csrf", session.csrfToken);
        sendJson(exchange, 200, body);
    }

    private void inventory(HttpExchange exchange) throws Exception {
        if (!requireMethod(exchange, "GET")) {
            return;
        }
        Session session = requireSession(exchange);
        if (session == null) {
            return;
        }
        Object body = runSync(() -> {
            Player player = Bukkit.getPlayer(session.playerUuid);
            if (player == null) {
                return null;
            }
            List<Map<String, Object>> items = new ArrayList<>();
            ItemStack[] contents = player.getInventory().getContents();
            for (int slot = 0; slot < contents.length; slot++) {
                ItemStack stack = contents[slot];
                if (stack == null || stack.getType() == Material.AIR) {
                    continue;
                }
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("slot", slot);
                item.put("id", stack.getType().getKey().toString());
                item.put("name", stack.getItemMeta() != null && stack.getItemMeta().hasDisplayName()
                        ? stack.getItemMeta().getDisplayName() : stack.getType().name());
                item.put("count", stack.getAmount());
                items.add(item);
            }
            return items;
        });
        if (body == null) {
            sendError(exchange, 400, "玩家不在线");
            return;
        }
        sendJson(exchange, 200, body);
    }

    private void chat(HttpExchange exchange) throws Exception {
        if (!requireMethod(exchange, "GET")) {
            return;
        }
        Session session = requireSession(exchange);
        if (session == null) {
            return;
        }
        long since = parseLong(param(exchange, "since"), 0L);
        List<Map<String, Object>> messages = new ArrayList<>();
        for (ChatBuffer.ChatMessage message : ChatBuffer.getInstance().since(since)) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("ts", message.timestamp());
            item.put("player", message.player());
            item.put("message", message.message());
            item.put("fromWeb", message.fromWeb());
            messages.add(item);
        }
        sendJson(exchange, 200, messages);
    }

    private void sendChat(HttpExchange exchange) throws Exception {
        if (!requireMethod(exchange, "POST")) {
            return;
        }
        Session session = requireSession(exchange);
        if (session == null || !requireCsrf(exchange, session)) {
            return;
        }
        JsonObject body = readJson(exchange);
        String message = string(body, "message");
        if (message == null || message.isBlank() || message.length() > 256) {
            sendError(exchange, 400, "消息长度必须为 1-256 字符");
            return;
        }
        String name = nameOf(session.playerUuid);
        runSyncVoid(() -> Bukkit.broadcastMessage(
                ChatColor.GRAY + "[Web] " + name + ": " + ChatColor.WHITE + message));
        ChatBuffer.getInstance().add(name, message, true);
        sendJson(exchange, 200, Map.of("success", true));
    }

    private void towns(HttpExchange exchange) throws Exception {
        if (!requireMethod(exchange, "GET")) {
            return;
        }
        Session session = requireSession(exchange);
        if (session == null) {
            return;
        }
        List<Map<String, Object>> body = runSync(() -> {
            List<Map<String, Object>> list = new ArrayList<>();
            for (Town town : core.allTowns()) {
                Map<String, Object> data = new LinkedHashMap<>();
                data.put("uuid", town.getUuid());
                data.put("name", town.getName());
                data.put("mayor", nameOf(town.getMayorUuid()));
                data.put("residents", town.getResidentCount());
                data.put("claims", town.getClaimCount());
                data.put("balance", town.getBalance().doubleValue());
                data.put("nation", town.hasNation() ? nameOfNation(town.getNationUuid()) : "");
                list.add(data);
            }
            return list;
        });
        sendJson(exchange, 200, body);
    }

    private void nations(HttpExchange exchange) throws Exception {
        if (!requireMethod(exchange, "GET")) {
            return;
        }
        Session session = requireSession(exchange);
        if (session == null) {
            return;
        }
        List<Map<String, Object>> body = runSync(() -> {
            List<Map<String, Object>> list = new ArrayList<>();
            for (Nation nation : core.allNations()) {
                Map<String, Object> data = new LinkedHashMap<>();
                data.put("uuid", nation.getUuid());
                data.put("name", nation.getName());
                data.put("king", nameOf(nation.getKingUuid()));
                data.put("towns", nation.getTownCount());
                data.put("influence", nation.getInfluence());
                data.put("stability", nation.getStability());
                data.put("ideology", nation.getIdeology().name());
                list.add(data);
            }
            return list;
        });
        sendJson(exchange, 200, body);
    }

    private void townDetail(HttpExchange exchange) throws Exception {
        if (!requireMethod(exchange, "GET")) {
            return;
        }
        Session session = requireSession(exchange);
        if (session == null) {
            return;
        }
        Map<String, Object> body = runSync(() -> {
            Town town = findTown(exchange);
            if (town == null) {
                return null;
            }
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("uuid", town.getUuid());
            data.put("name", town.getName());
            data.put("mayor", nameOf(town.getMayorUuid()));
            data.put("nation", town.hasNation() ? nameOfNation(town.getNationUuid()) : "");
            data.put("residents", town.getResidentCount());
            data.put("claims", town.getClaimCount());
            data.put("balance", town.getBalance().doubleValue());
            data.put("taxes", town.getTaxes().doubleValue());
            data.put("plotPrice", town.getPlotPrice().doubleValue());
            data.put("open", town.isOpen());
            data.put("public", town.isPublic());
            data.put("pvp", town.isPvpEnabled());
            data.put("board", town.getBoard() == null ? "" : town.getBoard());
            data.put("founded", town.getFounded());
            List<String> members = new ArrayList<>();
            for (String uuid : town.getResidentUuids()) {
                Resident resident = core.getResident(uuid);
                if (resident != null) {
                    members.add(resident.getName());
                }
            }
            data.put("members", members);
            return data;
        });
        if (body == null) {
            sendError(exchange, 404, "城镇不存在");
            return;
        }
        sendJson(exchange, 200, body);
    }

    private void nationDetail(HttpExchange exchange) throws Exception {
        if (!requireMethod(exchange, "GET")) {
            return;
        }
        Session session = requireSession(exchange);
        if (session == null) {
            return;
        }
        Map<String, Object> body = runSync(() -> {
            Nation nation = findNation(exchange);
            if (nation == null) {
                return null;
            }
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("uuid", nation.getUuid());
            data.put("name", nation.getName());
            data.put("king", nameOf(nation.getKingUuid()));
            data.put("capital", nation.getCapitalTownUuid() == null ? ""
                    : nameOfTown(nation.getCapitalTownUuid()));
            data.put("towns", nation.getTownCount());
            data.put("balance", nation.getBalance().doubleValue());
            data.put("taxes", nation.getTaxes().doubleValue());
            data.put("board", nation.getBoard() == null ? "" : nation.getBoard());
            data.put("ideology", nation.getIdeology().name());
            data.put("influence", nation.getInfluence());
            data.put("stability", nation.getStability());
            data.put("founded", nation.getFounded());
            List<String> townNames = new ArrayList<>();
            for (String uuid : nation.getTownUuids()) {
                Town town = core.getTown(uuid);
                if (town != null) {
                    townNames.add(town.getName());
                }
            }
            data.put("townList", townNames);
            return data;
        });
        if (body == null) {
            sendError(exchange, 404, "国家不存在");
            return;
        }
        sendJson(exchange, 200, body);
    }

    private void townLogs(HttpExchange exchange) throws Exception {
        if (!requireMethod(exchange, "GET")) {
            return;
        }
        Session session = requireSession(exchange);
        if (session == null) {
            return;
        }
        Map<String, Object> body = runSync(() -> {
            Resident resident = core.getResident(session.playerUuid);
            Town town = resident == null || !resident.hasTown() ? null : core.getTown(resident.getTownUuid());
            if (town == null) {
                return null;
            }
            if (!resident.getUuidString().equals(town.getMayorUuid())) {
                return Map.of("error", "只有市长可以查看日志");
            }
            int page = (int) parseLong(param(exchange, "page"), 1L);
            String action = param(exchange, "action");
            String player = param(exchange, "player");
            long since = parseSince(param(exchange, "since"));
            TownLogService service = plugin.townLogService();
            int pageSize = TownLogService.DEFAULT_PAGE_SIZE;
            int total = service.count(town.getUuid(), player, action, since);
            List<TownLog> logs = service.query(town.getUuid(), page, player, action, since, pageSize);
            List<Map<String, Object>> items = new ArrayList<>();
            for (TownLog log : logs) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("time", log.getTimestamp());
                item.put("player", log.getPlayerName());
                item.put("action", log.getAction() == null ? "" : log.getAction().name());
                item.put("x", log.getX());
                item.put("y", log.getY());
                item.put("z", log.getZ());
                item.put("detail", log.getDetail());
                items.add(item);
            }
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("total", total);
            data.put("page", page);
            data.put("totalPages", Math.max(1, (total + pageSize - 1) / pageSize));
            data.put("logs", items);
            return data;
        });
        if (body == null) {
            sendError(exchange, 403, "你没有加入任何城镇");
            return;
        }
        if (body.containsKey("error")) {
            sendError(exchange, 403, String.valueOf(body.get("error")));
            return;
        }
        sendJson(exchange, 200, body);
    }

    private void townSettings(HttpExchange exchange) throws Exception {
        if ("GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            Session session = requireSession(exchange);
            if (session == null) {
                return;
            }
            Map<String, Object> body = runSync(() -> {
                Resident resident = core.getResident(session.playerUuid);
                Town town = resident == null || !resident.hasTown() ? null : core.getTown(resident.getTownUuid());
                if (town == null || !resident.getUuidString().equals(town.getMayorUuid())) {
                    return null;
                }
                Map<String, Object> data = new LinkedHashMap<>();
                data.put("name", town.getName());
                data.put("taxes", town.getTaxes().doubleValue());
                data.put("open", town.isOpen());
                data.put("public", town.isPublic());
                data.put("board", town.getBoard() == null ? "" : town.getBoard());
                return data;
            });
            if (body == null) {
                sendError(exchange, 403, "只有市长可以管理设置");
                return;
            }
            sendJson(exchange, 200, body);
            return;
        }
        if (!requireMethod(exchange, "POST")) {
            return;
        }
        Session session = requireSession(exchange);
        if (session == null || !requireCsrf(exchange, session)) {
            return;
        }
        JsonObject body = readJson(exchange);
        Result result = runSync(() -> {
            Resident resident = core.getResident(session.playerUuid);
            Town town = resident == null || !resident.hasTown() ? null : core.getTown(resident.getTownUuid());
            if (town == null) {
                return Result.fail("你没有加入任何城镇");
            }
            TownService service = plugin.townService();
            Result last = Result.ok("设置已更新");
            if (body.has("taxes")) {
                last = service.setTaxes(resident, town, Money.parse(body.get("taxes").getAsString()));
                if (!last.isSuccess()) return last;
            }
            if (body.has("open")) {
                last = service.setOpen(resident, town, body.get("open").getAsBoolean());
                if (!last.isSuccess()) return last;
            }
            if (body.has("public")) {
                last = service.setPublic(resident, town, body.get("public").getAsBoolean());
                if (!last.isSuccess()) return last;
            }
            if (body.has("board")) {
                last = service.setBoard(resident, town, body.get("board").getAsString());
            }
            return last;
        });
        respondResult(exchange, result);
    }

    private void nationSettings(HttpExchange exchange) throws Exception {
        if ("GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            Session session = requireSession(exchange);
            if (session == null) {
                return;
            }
            Map<String, Object> body = runSync(() -> {
                Resident resident = core.getResident(session.playerUuid);
                Nation nation = resident == null ? null : nationOf(resident);
                if (nation == null) {
                    return null;
                }
                Map<String, Object> data = new LinkedHashMap<>();
                data.put("name", nation.getName());
                data.put("taxes", nation.getTaxes().doubleValue());
                data.put("board", nation.getBoard() == null ? "" : nation.getBoard());
                data.put("ideology", nation.getIdeology().name());
                return data;
            });
            if (body == null) {
                sendError(exchange, 403, "你没有加入任何国家");
                return;
            }
            sendJson(exchange, 200, body);
            return;
        }
        if (!requireMethod(exchange, "POST")) {
            return;
        }
        Session session = requireSession(exchange);
        if (session == null || !requireCsrf(exchange, session)) {
            return;
        }
        JsonObject body = readJson(exchange);
        Result result = runSync(() -> {
            Resident resident = core.getResident(session.playerUuid);
            Nation nation = resident == null ? null : nationOf(resident);
            if (nation == null) {
                return Result.fail("你没有加入任何国家");
            }
            NationService service = plugin.nationService();
            Result last = Result.ok("国家设置已更新");
            if (body.has("taxes")) {
                last = service.setTaxes(resident, nation, Money.parse(body.get("taxes").getAsString()));
                if (!last.isSuccess()) return last;
            }
            if (body.has("board")) {
                last = service.setBoard(resident, nation, body.get("board").getAsString());
                if (!last.isSuccess()) return last;
            }
            if (body.has("ideology")) {
                try {
                    Ideology ideology = Ideology.valueOf(body.get("ideology").getAsString().toUpperCase(Locale.ROOT));
                    last = service.setIdeology(resident, nation, ideology);
                } catch (IllegalArgumentException e) {
                    return Result.fail("未知国体");
                }
            }
            return last;
        });
        respondResult(exchange, result);
    }

    private void townTreasury(HttpExchange exchange) throws Exception {
        if (!requireMethod(exchange, "POST")) {
            return;
        }
        Session session = requireSession(exchange);
        if (session == null || !requireCsrf(exchange, session)) {
            return;
        }
        JsonObject body = readJson(exchange);
        String action = string(body, "action");
        Money amount = money(body, "amount");
        if (action == null || amount == null) {
            sendError(exchange, 400, "缺少参数");
            return;
        }
        Result result = runSync(() -> {
            Resident resident = core.getResident(session.playerUuid);
            Town town = resident == null || !resident.hasTown() ? null : core.getTown(resident.getTownUuid());
            if (town == null) {
                return Result.fail("你没有加入任何城镇");
            }
            TownService service = plugin.townService();
            if ("deposit".equalsIgnoreCase(action)) {
                return service.deposit(resident, town, amount);
            }
            if ("withdraw".equalsIgnoreCase(action)) {
                return service.withdraw(resident, town, amount);
            }
            return Result.fail("未知操作");
        });
        respondResult(exchange, result);
    }

    private void nationTreasury(HttpExchange exchange) throws Exception {
        if (!requireMethod(exchange, "POST")) {
            return;
        }
        Session session = requireSession(exchange);
        if (session == null || !requireCsrf(exchange, session)) {
            return;
        }
        JsonObject body = readJson(exchange);
        String action = string(body, "action");
        Money amount = money(body, "amount");
        if (action == null || amount == null) {
            sendError(exchange, 400, "缺少参数");
            return;
        }
        Result result = runSync(() -> {
            Resident resident = core.getResident(session.playerUuid);
            Nation nation = resident == null ? null : nationOf(resident);
            if (nation == null) {
                return Result.fail("你没有加入任何国家");
            }
            NationService service = plugin.nationService();
            if ("deposit".equalsIgnoreCase(action)) {
                return service.deposit(resident, nation, amount);
            }
            if ("withdraw".equalsIgnoreCase(action)) {
                return service.withdraw(resident, nation, amount);
            }
            return Result.fail("未知操作");
        });
        respondResult(exchange, result);
    }

    private void townJoin(HttpExchange exchange) throws Exception {
        if (!requireMethod(exchange, "POST")) {
            return;
        }
        Session session = requireSession(exchange);
        if (session == null || !requireCsrf(exchange, session)) {
            return;
        }
        JsonObject body = readJson(exchange);
        String name = string(body, "name");
        if (name == null || name.isBlank()) {
            sendError(exchange, 400, "请指定城镇名");
            return;
        }
        Result result = runSync(() -> {
            Resident resident = core.getResident(session.playerUuid);
            Town town = core.getTownByName(name);
            if (resident == null || town == null) {
                return Result.fail("城镇或玩家不存在");
            }
            return plugin.townService().joinTown(resident, town, true);
        });
        respondResult(exchange, result);
    }

    private void transfer(HttpExchange exchange) throws Exception {
        if (!requireMethod(exchange, "POST")) {
            return;
        }
        Session session = requireSession(exchange);
        if (session == null || !requireCsrf(exchange, session)) {
            return;
        }
        JsonObject body = readJson(exchange);
        String targetName = string(body, "target");
        Money amount = money(body, "amount");
        if (targetName == null || amount == null) {
            sendError(exchange, 400, "请提供目标玩家和金额");
            return;
        }
        Result result = runSync(() -> {
            Resident sender = core.getResident(session.playerUuid);
            Resident target = core.getResidentByName(targetName);
            if (sender == null || target == null) {
                return Result.fail("玩家不存在");
            }
            ResidentService service = plugin.residentService();
            return service.pay(sender, target, amount);
        });
        respondResult(exchange, result);
    }

    private void switchTown(HttpExchange exchange) throws IOException {
        sendError(exchange, 410, "单城镇模式已移除切换功能");
    }

    private void market(HttpExchange exchange) throws Exception {
        if (!requireMethod(exchange, "GET")) {
            return;
        }
        Session session = requireSession(exchange);
        if (session == null) {
            return;
        }
        String keyword = param(exchange, "search");
        List<Map<String, Object>> body = runSync(() -> {
            MarketService market = plugin.marketService();
            Resident resident = core.getResident(session.playerUuid);
            List<Map<String, Object>> list = new ArrayList<>();
            for (MarketItem item : market.items()) {
                if (keyword != null && !keyword.isBlank()
                        && !item.getItemId().toLowerCase(Locale.ROOT).contains(keyword.toLowerCase(Locale.ROOT))
                        && !item.getDisplayName().toLowerCase(Locale.ROOT).contains(keyword.toLowerCase(Locale.ROOT))) {
                    continue;
                }
                Map<String, Object> data = new LinkedHashMap<>();
                data.put("id", item.getItemId());
                data.put("name", item.getDisplayName());
                data.put("category", item.getCategory());
                data.put("buyPrice", market.currentBuyPrice(item).doubleValue());
                data.put("sellPrice", market.currentSellPrice(item).doubleValue());
                data.put("trend", item.getPriceTrend());
                data.put("playerBuyQuota", resident == null ? 0 : market.playerRemainingBuy(resident.getUuidString()));
                data.put("playerSellQuota", resident == null ? 0 : market.playerRemainingSell(resident.getUuidString()));
                data.put("serverBuyQuota", market.serverRemainingBuy());
                data.put("serverSellQuota", market.serverRemainingSell());
                list.add(data);
            }
            return list;
        });
        sendJson(exchange, 200, body);
    }

    private void marketTrade(HttpExchange exchange, boolean buy) throws Exception {
        if (!requireMethod(exchange, "POST")) {
            return;
        }
        Session session = requireSession(exchange);
        if (session == null || !requireCsrf(exchange, session)) {
            return;
        }
        JsonObject body = readJson(exchange);
        String itemId = string(body, "itemId");
        int quantity = (int) parseLong(body.has("amount") ? body.get("amount").getAsString() : "0", 0L);
        if (itemId == null || quantity <= 0 || quantity > 512) {
            sendError(exchange, 400, "参数无效");
            return;
        }
        Result result = runSync(() -> {
            Player player = Bukkit.getPlayer(session.playerUuid);
            Resident resident = core.getResident(session.playerUuid);
            if (player == null || resident == null) {
                return Result.fail("玩家不在线");
            }
            MarketService market = plugin.marketService();
            return buy ? market.buy(player, resident, itemId, quantity)
                    : market.sell(player, resident, itemId, quantity);
        });
        respondResult(exchange, result);
    }

    private void freeMarket(HttpExchange exchange) throws Exception {
        if (!requireMethod(exchange, "GET")) {
            return;
        }
        Session session = requireSession(exchange);
        if (session == null) {
            return;
        }
        String mode = param(exchange, "mode");
        List<Map<String, Object>> body = runSync(() -> {
            FreeMarketService service = plugin.freeMarketService();
            Collection<Listing> listings = "mine".equalsIgnoreCase(mode)
                    ? service.listingsBySeller(session.playerUuid.toString())
                    : service.allListings();
            List<Map<String, Object>> list = new ArrayList<>();
            for (Listing listing : listings) {
                Map<String, Object> data = new LinkedHashMap<>();
                data.put("id", listing.getId());
                data.put("seller", listing.getSellerName());
                data.put("itemId", listing.getItemId());
                data.put("name", listing.getDisplayName());
                data.put("quantity", listing.getQuantity());
                data.put("unitPrice", listing.getUnitPrice().doubleValue());
                data.put("totalPrice", listing.totalPrice().doubleValue());
                data.put("listedAt", listing.getListedAt());
                list.add(data);
            }
            return list;
        });
        sendJson(exchange, 200, body);
    }

    private void freeMarketList(HttpExchange exchange) throws Exception {
        if (!requireMethod(exchange, "POST")) {
            return;
        }
        Session session = requireSession(exchange);
        if (session == null || !requireCsrf(exchange, session)) {
            return;
        }
        JsonObject body = readJson(exchange);
        String itemId = string(body, "itemId");
        int quantity = (int) parseLong(body.has("quantity") ? body.get("quantity").getAsString() : "0", 0L);
        Money price = money(body, "unitPrice");
        if (itemId == null || quantity <= 0 || price == null) {
            sendError(exchange, 400, "参数无效");
            return;
        }
        Result result = runSync(() -> {
            Player player = Bukkit.getPlayer(session.playerUuid);
            if (player == null) {
                return Result.fail("玩家不在线");
            }
            return plugin.freeMarketService().list(player, itemId, quantity, price);
        });
        respondResult(exchange, result);
    }

    private void freeMarketBuy(HttpExchange exchange) throws Exception {
        if (!requireMethod(exchange, "POST")) {
            return;
        }
        Session session = requireSession(exchange);
        if (session == null || !requireCsrf(exchange, session)) {
            return;
        }
        JsonObject body = readJson(exchange);
        long id = parseLong(body.has("id") ? body.get("id").getAsString() : "0", 0L);
        int quantity = (int) parseLong(body.has("quantity") ? body.get("quantity").getAsString() : "0", 0L);
        if (id <= 0 || quantity <= 0) {
            sendError(exchange, 400, "参数无效");
            return;
        }
        Result result = runSync(() -> {
            Player player = Bukkit.getPlayer(session.playerUuid);
            Resident resident = core.getResident(session.playerUuid);
            if (player == null || resident == null) {
                return Result.fail("玩家不在线");
            }
            return plugin.freeMarketService().buy(player, resident, id, quantity);
        });
        respondResult(exchange, result);
    }

    private void freeMarketCancel(HttpExchange exchange) throws Exception {
        if (!requireMethod(exchange, "POST")) {
            return;
        }
        Session session = requireSession(exchange);
        if (session == null || !requireCsrf(exchange, session)) {
            return;
        }
        JsonObject body = readJson(exchange);
        long id = parseLong(body.has("id") ? body.get("id").getAsString() : "0", 0L);
        if (id <= 0) {
            sendError(exchange, 400, "挂单 ID 无效");
            return;
        }
        Result result = runSync(() -> {
            Player player = Bukkit.getPlayer(session.playerUuid);
            if (player == null) {
                return Result.fail("玩家不在线");
            }
            return plugin.freeMarketService().cancel(player, id);
        });
        respondResult(exchange, result);
    }

    private void mapProxy(HttpExchange exchange) throws IOException {
        if (!requireMethod(exchange, "GET")) {
            return;
        }
        Session session = requireSession(exchange);
        if (session == null) {
            return;
        }
        String mapUrl = config.webMapUrl();
        if (mapUrl == null || mapUrl.isBlank()) {
            sendError(exchange, 503, "地图服务未配置");
            return;
        }
        String path = exchange.getRequestURI().getPath();
        String suffix = path.substring("/api/map".length());
        String query = exchange.getRequestURI().getQuery();
        String target = mapUrl.replaceAll("/+$", "") + suffix + (query == null ? "" : "?" + query);
        try {
            java.net.HttpURLConnection connection = (java.net.HttpURLConnection) URI.create(target).toURL().openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(10_000);
            connection.setReadTimeout(10_000);
            connection.setRequestProperty("User-Agent", "GeoTowny-Web");
            int code = connection.getResponseCode();
            InputStream input = code >= 400 ? connection.getErrorStream() : connection.getInputStream();
            byte[] data = input == null ? new byte[0] : input.readAllBytes();
            exchange.getResponseHeaders().set("Content-Type",
                    connection.getContentType() == null ? "application/json; charset=UTF-8" : connection.getContentType());
            exchange.sendResponseHeaders(code, data.length);
            try (OutputStream output = exchange.getResponseBody()) {
                output.write(data);
            }
            connection.disconnect();
        } catch (RuntimeException | IOException e) {
            sendError(exchange, 502, "地图代理失败");
        }
    }

    private void handleOneTimeLogin(HttpExchange exchange) throws IOException {
        applySecurityHeaders(exchange);
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendError(exchange, 405, "Method Not Allowed");
            return;
        }
        String token = param(exchange, "token");
        OneTimeToken oneTime = token == null ? null : oneTimeTokens.remove(token);
        if (oneTime == null || oneTime.expiresAt < System.currentTimeMillis()
                || core.getResident(oneTime.playerUuid) == null) {
            sendHtml(exchange, 401, "<!doctype html><meta charset=\"utf-8\"><h1>登录链接无效或已过期</h1>");
            return;
        }
        Session session = createSession(oneTime.playerUuid);
        setSessionCookie(exchange, session.token);
        exchange.getResponseHeaders().set("Location", "/");
        exchange.sendResponseHeaders(302, -1);
    }

    private void handleStatic(HttpExchange exchange) throws IOException {
        applySecurityHeaders(exchange);
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendError(exchange, 405, "Method Not Allowed");
            return;
        }
        String path = exchange.getRequestURI().getPath();
        if (path.equals("/favicon.ico")) {
            exchange.sendResponseHeaders(204, -1);
            return;
        }
        String content = assets.load(path);
        if (content == null) {
            sendError(exchange, 404, "Not Found");
            return;
        }
        String contentType = path.endsWith(".js") ? "application/javascript; charset=UTF-8"
                : path.endsWith(".css") ? "text/css; charset=UTF-8"
                : "text/html; charset=UTF-8";
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.getResponseHeaders().set("Cache-Control", "no-cache");
        byte[] data = content.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(200, data.length);
        try (OutputStream output = exchange.getResponseBody()) {
            output.write(data);
        }
    }

    private Session createSession(UUID uuid) {
        revokeSessions(uuid);
        String token = WebSecurity.randomToken(32);
        Session session = new Session(token, uuid, WebSecurity.randomToken(24),
                System.currentTimeMillis(),
                System.currentTimeMillis() + config.webSessionExpiryHours() * 3_600_000L);
        sessions.put(token, session);
        return session;
    }

    private Session session(HttpExchange exchange) {
        String token = sessionToken(exchange);
        if (token == null) {
            return null;
        }
        Session session = sessions.get(token);
        if (session == null) {
            return null;
        }
        long now = System.currentTimeMillis();
        if (now > session.expiresAt) {
            sessions.remove(token);
            return null;
        }
        session.lastSeen = now;
        return session;
    }

    private Session requireSession(HttpExchange exchange) throws IOException {
        Session session = session(exchange);
        if (session == null) {
            sendError(exchange, 401, "未登录或登录已过期");
        }
        return session;
    }

    private boolean requireCsrf(HttpExchange exchange, Session session) throws IOException {
        if (!originAllowed(exchange)) {
            sendError(exchange, 403, "Origin 不被允许");
            return false;
        }
        String token = exchange.getRequestHeaders().getFirst(CSRF_HEADER);
        if (token == null || !java.security.MessageDigest.isEqual(
                token.getBytes(StandardCharsets.UTF_8), session.csrfToken.getBytes(StandardCharsets.UTF_8))) {
            sendError(exchange, 403, "CSRF 校验失败");
            return false;
        }
        return true;
    }

    private boolean originAllowed(HttpExchange exchange) {
        String origin = exchange.getRequestHeaders().getFirst("Origin");
        if (origin == null || origin.isBlank()) {
            return true;
        }
        String authority = authority(origin);
        String host = exchange.getRequestHeaders().getFirst("Host");
        if (authority != null && host != null && authority.equalsIgnoreCase(host)) {
            return true;
        }
        String configured = authority(baseUrl());
        if (authority != null && configured != null && authority.equalsIgnoreCase(configured)) {
            return true;
        }
        return config.webAllowedOrigins().contains(origin);
    }

    private String sessionToken(HttpExchange exchange) {
        String auth = exchange.getRequestHeaders().getFirst("Authorization");
        if (auth != null && auth.startsWith("Bearer ")) {
            return auth.substring(7).trim();
        }
        String cookie = exchange.getRequestHeaders().getFirst("Cookie");
        if (cookie == null) {
            return null;
        }
        for (String part : cookie.split(";")) {
            String value = part.trim();
            if (value.startsWith(COOKIE_NAME + "=")) {
                return value.substring(COOKIE_NAME.length() + 1);
            }
        }
        return null;
    }

    private void setSessionCookie(HttpExchange exchange, String token) {
        StringBuilder cookie = new StringBuilder(COOKIE_NAME).append('=').append(token)
                .append("; Path=/; Max-Age=").append(config.webSessionExpiryHours() * 3600L)
                .append("; HttpOnly; SameSite=Strict");
        if (baseUrl().startsWith("https://")) {
            cookie.append("; Secure");
        }
        exchange.getResponseHeaders().add("Set-Cookie", cookie.toString());
    }

    private void handleOptions(HttpExchange exchange) throws IOException {
        if (!originAllowed(exchange)) {
            sendError(exchange, 403, "Origin 不被允许");
            return;
        }
        String origin = exchange.getRequestHeaders().getFirst("Origin");
        if (origin != null) {
            exchange.getResponseHeaders().set("Access-Control-Allow-Origin", origin);
            exchange.getResponseHeaders().set("Vary", "Origin");
            exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type, X-CSRF-Token, Authorization");
            exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
        }
        exchange.sendResponseHeaders(204, -1);
    }

    private void applySecurityHeaders(HttpExchange exchange) {
        exchange.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
        exchange.getResponseHeaders().set("X-Frame-Options", "DENY");
        exchange.getResponseHeaders().set("Referrer-Policy", "no-referrer");
        exchange.getResponseHeaders().set("Content-Security-Policy",
                "default-src 'self'; script-src 'self'; style-src 'self'; img-src 'self' data:; "
                        + "connect-src 'self'; frame-ancestors 'none'; base-uri 'none'");
        String origin = exchange.getRequestHeaders().getFirst("Origin");
        if (origin != null && originAllowed(exchange)) {
            exchange.getResponseHeaders().set("Access-Control-Allow-Origin", origin);
            exchange.getResponseHeaders().set("Vary", "Origin");
        }
    }

    private void sendJson(HttpExchange exchange, int status, Object body) throws IOException {
        byte[] data = gson.toJson(body).getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        exchange.sendResponseHeaders(status, data.length);
        try (OutputStream output = exchange.getResponseBody()) {
            output.write(data);
        }
    }

    private void sendHtml(HttpExchange exchange, int status, String html) throws IOException {
        byte[] data = html.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
        exchange.sendResponseHeaders(status, data.length);
        try (OutputStream output = exchange.getResponseBody()) {
            output.write(data);
        }
    }

    private void sendError(HttpExchange exchange, int status, String message) throws IOException {
        sendJson(exchange, status, Map.of("error", message == null ? "错误" : message));
    }

    private void respondResult(HttpExchange exchange, Result result) throws IOException {
        if (result != null && result.isSuccess()) {
            sendJson(exchange, 200, Map.of("success", true, "message", result.getMessage()));
        } else {
            sendError(exchange, 400, result == null ? "操作失败" : result.getMessage());
        }
    }

    private boolean requireMethod(HttpExchange exchange, String method) throws IOException {
        if (!method.equalsIgnoreCase(exchange.getRequestMethod())) {
            sendError(exchange, 405, "Method Not Allowed");
            return false;
        }
        return true;
    }

    private JsonObject readJson(HttpExchange exchange) throws IOException {
        String body = readBody(exchange, MAX_JSON_BYTES);
        JsonElement element = JsonParser.parseString(body);
        if (!element.isJsonObject()) {
            throw new IllegalArgumentException("JSON 对象缺失");
        }
        return element.getAsJsonObject();
    }

    private String readBody(HttpExchange exchange, int maxBytes) throws IOException {
        try (InputStream input = exchange.getRequestBody();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096];
            int read;
            int total = 0;
            while ((read = input.read(buffer)) != -1) {
                total += read;
                if (total > maxBytes) {
                    throw new IOException("请求体过大");
                }
                output.write(buffer, 0, read);
            }
            return output.toString(StandardCharsets.UTF_8);
        }
    }

    private String param(HttpExchange exchange, String key) {
        String query = exchange.getRequestURI().getQuery();
        if (query == null) {
            return null;
        }
        for (String part : query.split("&")) {
            String[] pair = part.split("=", 2);
            if (pair.length == 2 && pair[0].equals(key)) {
                return URLDecoder.decode(pair[1], StandardCharsets.UTF_8);
            }
        }
        return null;
    }

    private static String string(JsonObject body, String key) {
        return body.has(key) && !body.get(key).isJsonNull() ? body.get(key).getAsString() : null;
    }

    private static Money money(JsonObject body, String key) {
        if (!body.has(key)) {
            return null;
        }
        try {
            return Money.parse(body.get(key).getAsString());
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static long parseLong(String value, long fallback) {
        try {
            return value == null ? fallback : Long.parseLong(value);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static long parseSince(String value) {
        if (value == null || value.isBlank()) {
            return 0L;
        }
        long now = System.currentTimeMillis();
        try {
            long amount = Long.parseLong(value);
            return amount > now ? amount : now - amount;
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    private static String authority(String url) {
        if (url == null) {
            return null;
        }
        int index = url.indexOf("://");
        if (index < 0) {
            return url;
        }
        String rest = url.substring(index + 3);
        int slash = rest.indexOf('/');
        return slash < 0 ? rest : rest.substring(0, slash);
    }

    private String nameOf(String uuid) {
        if (uuid == null) {
            return "?";
        }
        Resident resident = core.getResident(uuid);
        return resident == null ? "?" : resident.getName();
    }

    private String nameOf(UUID uuid) {
        if (uuid == null) {
            return "?";
        }
        Resident resident = core.getResident(uuid);
        return resident == null ? "?" : resident.getName();
    }

    private String nameOfNation(String uuid) {
        Nation nation = core.getNation(uuid);
        return nation == null ? "?" : nation.getName();
    }

    private String nameOfTown(String uuid) {
        Town town = core.getTown(uuid);
        return town == null ? "?" : town.getName();
    }

    private Nation nationOf(Resident resident) {
        if (resident == null || !resident.hasTown()) {
            return null;
        }
        Town town = core.getTown(resident.getTownUuid());
        return town == null || !town.hasNation() ? null : core.getNation(town.getNationUuid());
    }

    private Town findTown(HttpExchange exchange) {
        String uuid = param(exchange, "uuid");
        if (uuid != null && !uuid.isBlank()) {
            return core.getTown(uuid);
        }
        String name = param(exchange, "name");
        return name == null ? null : core.getTownByName(name);
    }

    private Nation findNation(HttpExchange exchange) {
        String uuid = param(exchange, "uuid");
        if (uuid != null && !uuid.isBlank()) {
            return core.getNation(uuid);
        }
        String name = param(exchange, "name");
        return name == null ? null : core.getNationByName(name);
    }

    private <T> T runSync(Supplier<T> supplier) throws Exception {
        if (!plugin.isEnabled()) {
            throw new IllegalStateException("插件未启用");
        }
        if (Bukkit.isPrimaryThread()) {
            return supplier.get();
        }
        Future<T> future = Bukkit.getScheduler().callSyncMethod(plugin, supplier::get);
        return future.get(CALL_TIMEOUT_MS, TimeUnit.MILLISECONDS);
    }

    private void runSyncVoid(Runnable runnable) throws Exception {
        runSync(() -> {
            runnable.run();
            return null;
        });
    }

    private static final class Session {
        private final String token;
        private final UUID playerUuid;
        private final String csrfToken;
        private final long createdAt;
        private final long expiresAt;
        private volatile long lastSeen;

        private Session(String token, UUID playerUuid, String csrfToken, long createdAt, long expiresAt) {
            this.token = token;
            this.playerUuid = playerUuid;
            this.csrfToken = csrfToken;
            this.createdAt = createdAt;
            this.expiresAt = expiresAt;
            this.lastSeen = createdAt;
        }
    }

    private static final class OneTimeToken {
        private final UUID playerUuid;
        private final long expiresAt;

        private OneTimeToken(UUID playerUuid, long expiresAt) {
            this.playerUuid = playerUuid;
            this.expiresAt = expiresAt;
        }
    }

    private static final class LoginAttempt {
        private int failures;
        private long lockUntil;
    }
}
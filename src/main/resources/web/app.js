(() => {
  let csrf = "";
  let player = null;
  let chatSince = 0;
  let chatTimer = null;
  let currentView = "overview";

  const $ = (id) => document.getElementById(id);
  const toast = (message) => {
    const box = $("toast");
    box.textContent = message;
    box.hidden = false;
    clearTimeout(box._timer);
    box._timer = setTimeout(() => { box.hidden = true; }, 3500);
  };

  async function api(path, options = {}) {
    const headers = Object.assign({ "Content-Type": "application/json" }, options.headers || {});
    if (options.method && options.method !== "GET" && csrf) {
      headers["X-CSRF-Token"] = csrf;
    }
    const response = await fetch(path, Object.assign({}, options, {
      headers,
      credentials: "same-origin"
    }));
    const text = await response.text();
    let data = null;
    try { data = text ? JSON.parse(text) : null; } catch (_) { data = text; }
    if (!response.ok) {
      const message = data && data.error ? data.error : `请求失败 (${response.status})`;
      throw new Error(message);
    }
    return data;
  }

  function showLogin() {
    $("login").hidden = false;
    $("app").hidden = true;
    if (chatTimer) { clearInterval(chatTimer); chatTimer = null; }
  }

  function showApp() {
    $("login").hidden = true;
    $("app").hidden = false;
    $("player-name").textContent = player ? `${player.name} · ${player.town || "无城镇"} · ${player.nation || "无国家"}` : "";
  }

  function row(label, value) {
    const el = document.createElement("div");
    el.className = "muted";
    el.textContent = `${label}: ${value}`;
    return el;
  }

  function table(columns, rows, actions) {
    const table = document.createElement("table");
    const head = document.createElement("tr");
    columns.forEach((column) => {
      const th = document.createElement("th");
      th.textContent = column;
      head.appendChild(th);
    });
    if (actions) {
      const th = document.createElement("th");
      th.textContent = "操作";
      head.appendChild(th);
    }
    table.appendChild(head);
    rows.forEach((values, index) => {
      const tr = document.createElement("tr");
      values.forEach((value) => {
        const td = document.createElement("td");
        td.textContent = value;
        tr.appendChild(td);
      });
      if (actions) {
        const td = document.createElement("td");
        actions(td, index);
        tr.appendChild(td);
      }
      table.appendChild(tr);
    });
    return table;
  }

  async function loadPlayerAndInfo() {
    const info = await api("/api/info");
    $("server-summary").textContent = `在线 ${info.online} / 居民 ${info.residents} / 城镇 ${info.towns} / 国家 ${info.nations}`;
    try {
      player = await api("/api/player");
      csrf = player.csrf || "";
      showApp();
      renderPlayer();
      loadOverview();
    } catch (_) {
      showLogin();
    }
  }

  function renderPlayer() {
    const card = $("player-card");
    card.replaceChildren(
      row("玩家", player.name),
      row("余额", Number(player.balance).toFixed(2)),
      row("城镇", player.town || "无"),
      row("国家", player.nation || "无"),
      row("城镇职位", player.townRank || "无"),
      row("国家职位", player.nationRank || "无")
    );
  }

  async function loadOverview() {
    const info = await api("/api/info");
    const card = $("info-card");
    card.replaceChildren(
      row("版本", info.version),
      row("在线玩家", info.online),
      row("居民", info.residents),
      row("城镇", info.towns),
      row("国家", info.nations)
    );
  }

  async function loadTowns() {
    const towns = await api("/api/towns");
    $("town-list").replaceChildren(table(
      ["名称", "市长", "居民", "领地", "国库", "国家"],
      towns.map((town) => [town.name, town.mayor, town.residents, town.claims,
        Number(town.balance).toFixed(2), town.nation || "无"])
    ));
  }

  async function loadNations() {
    const nations = await api("/api/nations");
    $("nation-list").replaceChildren(table(
      ["名称", "领袖", "城镇", "影响力", "稳定度", "国体"],
      nations.map((nation) => [nation.name, nation.king, nation.towns,
        Number(nation.influence).toFixed(1), Number(nation.stability).toFixed(1), nation.ideology])
    ));
  }

  async function loadMarket() {
    const items = await api("/api/market");
    $("market-list").replaceChildren(table(
      ["商品", "买价", "卖价", "趋势", "个人额度"],
      items.map((item) => [item.name, Number(item.buyPrice).toFixed(2),
        Number(item.sellPrice).toFixed(2), item.trend, `${item.playerBuyQuota}/${item.playerSellQuota}`]),
      (cell, index) => {
        const item = items[index];
        const buy = document.createElement("button");
        buy.textContent = "买 1";
        buy.addEventListener("click", () => tradeMarket(item.id, 1, true));
        const sell = document.createElement("button");
        sell.textContent = "卖 1";
        sell.className = "secondary";
        sell.addEventListener("click", () => tradeMarket(item.id, 1, false));
        cell.append(buy, sell);
      }
    ));
  }

  async function tradeMarket(itemId, amount, buy) {
    try {
      const result = await api(buy ? "/api/market/buy" : "/api/market/sell", {
        method: "POST", body: JSON.stringify({ itemId, amount })
      });
      toast(result.message || "完成");
      await loadMarket();
      await loadPlayerAndInfo();
    } catch (error) { toast(error.message); }
  }

  async function loadFreeMarket() {
    const listings = await api("/api/freemarket");
    $("fm-list").replaceChildren(table(
      ["#", "卖家", "物品", "剩余", "单价", "总价"],
      listings.map((listing) => [listing.id, listing.seller, listing.name,
        listing.quantity, Number(listing.unitPrice).toFixed(2), Number(listing.totalPrice).toFixed(2)]),
      (cell, index) => {
        const listing = listings[index];
        if (listing.seller === player.name) {
          const cancel = document.createElement("button");
          cancel.textContent = "取消";
          cancel.className = "secondary";
          cancel.addEventListener("click", () => cancelListing(listing.id));
          cell.appendChild(cancel);
        } else {
          const buy = document.createElement("button");
          buy.textContent = "买 1";
          buy.addEventListener("click", () => buyListing(listing.id));
          cell.appendChild(buy);
        }
      }
    ));
  }

  async function buyListing(id) {
    try {
      const result = await api("/api/freemarket/buy", { method: "POST", body: JSON.stringify({ id, quantity: 1 }) });
      toast(result.message || "已完成");
      await loadFreeMarket();
    } catch (error) { toast(error.message); }
  }

  async function cancelListing(id) {
    try {
      const result = await api("/api/freemarket/cancel", { method: "POST", body: JSON.stringify({ id }) });
      toast(result.message || "已取消");
      await loadFreeMarket();
    } catch (error) { toast(error.message); }
  }

  async function loadChat() {
    const messages = await api(`/api/chat?since=${chatSince}`);
    const list = $("chat-list");
    if (messages.length === 0) { return; }
    messages.forEach((message) => {
      const line = document.createElement("div");
      line.textContent = `${message.player}: ${message.message}`;
      list.appendChild(line);
      chatSince = Math.max(chatSince, message.ts);
    });
    list.scrollTop = list.scrollHeight;
  }

  async function loadSettings() {
    try {
      const town = await api("/api/town/settings");
      $("town-taxes").value = town.taxes;
      $("town-board").value = town.board || "";
      $("town-open").checked = !!town.open;
      $("town-public").checked = !!town.public;
    } catch (_) { /* 非市长 */ }
    try {
      const nation = await api("/api/nation/settings");
      $("nation-taxes").value = nation.taxes;
      $("nation-board").value = nation.board || "";
      $("nation-ideology").value = nation.ideology;
    } catch (_) { /* 非国王 */ }
  }

  function switchView(view) {
    currentView = view;
    document.querySelectorAll(".view").forEach((section) => {
      section.hidden = section.id !== `view-${view}`;
    });
    document.querySelectorAll("#nav button").forEach((button) => {
      button.classList.toggle("active", button.dataset.view === view);
    });
    if (view === "towns") loadTowns().catch((error) => toast(error.message));
    if (view === "nations") loadNations().catch((error) => toast(error.message));
    if (view === "market") loadMarket().catch((error) => toast(error.message));
    if (view === "freemarket") loadFreeMarket().catch((error) => toast(error.message));
    if (view === "chat") { loadChat().catch((error) => toast(error.message)); startChatPolling(); }
    if (view === "settings") loadSettings();
    if (view !== "chat" && chatTimer) { clearInterval(chatTimer); chatTimer = null; }
  }

  function startChatPolling() {
    if (chatTimer) { return; }
    chatTimer = setInterval(() => {
      if (currentView === "chat") loadChat().catch(() => {});
    }, 3000);
  }

  $("login-form").addEventListener("submit", async (event) => {
    event.preventDefault();
    $("login-error").textContent = "";
    try {
      await api("/api/auth/password", {
        method: "POST",
        body: JSON.stringify({ username: $("login-name").value, password: $("login-password").value })
      });
      await loadPlayerAndInfo();
    } catch (error) {
      $("login-error").textContent = error.message;
    }
  });

  $("logout").addEventListener("click", async () => {
    try { await api("/api/auth/logout", { method: "POST", body: "{}" }); } catch (_) { /* ignore */ }
    csrf = "";
    player = null;
    showLogin();
  });

  document.querySelectorAll("#nav button").forEach((button) => {
    button.addEventListener("click", () => switchView(button.dataset.view));
  });

  $("fm-form").addEventListener("submit", async (event) => {
    event.preventDefault();
    try {
      const result = await api("/api/freemarket/list", {
        method: "POST",
        body: JSON.stringify({
          itemId: $("fm-item").value,
          quantity: Number($("fm-qty").value),
          unitPrice: Number($("fm-price").value)
        })
      });
      toast(result.message || "已上架");
      await loadFreeMarket();
    } catch (error) { toast(error.message); }
  });

  $("chat-form").addEventListener("submit", async (event) => {
    event.preventDefault();
    try {
      await api("/api/sendchat", { method: "POST", body: JSON.stringify({ message: $("chat-message").value }) });
      $("chat-message").value = "";
      await loadChat();
    } catch (error) { toast(error.message); }
  });

  $("town-settings-form").addEventListener("submit", async (event) => {
    event.preventDefault();
    try {
      const result = await api("/api/town/settings", {
        method: "POST",
        body: JSON.stringify({
          taxes: Number($("town-taxes").value),
          board: $("town-board").value,
          open: $("town-open").checked,
          public: $("town-public").checked
        })
      });
      toast(result.message || "已保存");
    } catch (error) { toast(error.message); }
  });

  $("nation-settings-form").addEventListener("submit", async (event) => {
    event.preventDefault();
    try {
      const result = await api("/api/nation/settings", {
        method: "POST",
        body: JSON.stringify({
          taxes: Number($("nation-taxes").value),
          board: $("nation-board").value,
          ideology: $("nation-ideology").value
        })
      });
      toast(result.message || "已保存");
    } catch (error) { toast(error.message); }
  });

  async function treasury(path, amountId, action) {
    try {
      const result = await api(path, {
        method: "POST",
        body: JSON.stringify({ action, amount: Number($(amountId).value) })
      });
      toast(result.message || "完成");
      await loadPlayerAndInfo();
    } catch (error) { toast(error.message); }
  }

  document.querySelectorAll("#town-treasury-form button").forEach((button) => {
    button.addEventListener("click", (event) => {
      event.preventDefault();
      treasury("/api/town/treasury", "town-amount", button.dataset.action);
    });
  });

  document.querySelectorAll("#nation-treasury-form button").forEach((button) => {
    button.addEventListener("click", (event) => {
      event.preventDefault();
      treasury("/api/nation/treasury", "nation-amount", button.dataset.action);
    });
  });

  $("transfer-form").addEventListener("submit", async (event) => {
    event.preventDefault();
    try {
      const result = await api("/api/player/transfer", {
        method: "POST",
        body: JSON.stringify({
          target: $("transfer-target").value,
          amount: Number($("transfer-amount").value)
        })
      });
      toast(result.message || "已转账");
      await loadPlayerAndInfo();
    } catch (error) { toast(error.message); }
  });

  loadPlayerAndInfo().catch((error) => {
    showLogin();
    $("login-error").textContent = error.message;
  });
})();
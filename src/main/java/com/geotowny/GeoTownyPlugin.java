package com.geotowny;

import com.geotowny.command.GeoAdminCommand;
import com.geotowny.command.GeoCommand;
import com.geotowny.command.CheckInCommand;
import com.geotowny.command.FreeMarketCommand;
import com.geotowny.command.NationCommand;
import com.geotowny.command.PlotCommand;
import com.geotowny.command.PhysicalCurrencyCommand;
import com.geotowny.command.RedeemCommand;
import com.geotowny.command.ResidentCommand;
import com.geotowny.command.ShopCommand;
import com.geotowny.command.TownCommand;
import com.geotowny.config.ConfigMigrator;
import com.geotowny.config.PluginConfig;
import com.geotowny.core.GeoTownyCore;
import com.geotowny.gui.GuiListener;
import com.geotowny.listener.AntiExploitListener;
import com.geotowny.listener.CampaignListener;
import com.geotowny.listener.PlayerListener;
import com.geotowny.listener.ProtectionListener;
import com.geotowny.listener.WebChatListener;
import com.geotowny.placeholder.GeoPlaceholders;
import com.geotowny.service.AntiExploitService;
import com.geotowny.service.CheckInService;
import com.geotowny.service.ClaimService;
import com.geotowny.service.EconomyService;
import com.geotowny.service.FestivalService;
import com.geotowny.service.FreeMarketService;
import com.geotowny.service.IdeologyService;
import com.geotowny.service.InfluenceService;
import com.geotowny.service.KillDropService;
import com.geotowny.service.MarketService;
import com.geotowny.service.NationService;
import com.geotowny.service.PhysicalCurrencyService;
import com.geotowny.service.PlotService;
import com.geotowny.service.ProtectionService;
import com.geotowny.service.RedeemService;
import com.geotowny.service.ResidentService;
import com.geotowny.service.TownLogService;
import com.geotowny.service.TownService;
import com.geotowny.service.WarService;
import com.geotowny.storage.MarketStore;
import com.geotowny.storage.CheckInStore;
import com.geotowny.storage.FreeMarketStore;
import com.geotowny.storage.PhysicalCurrencyStore;
import com.geotowny.storage.RedeemStore;
import com.geotowny.storage.SettlementStateStore;
import com.geotowny.storage.StorageService;
import com.geotowny.storage.TownLogStore;
import com.geotowny.task.SettlementScheduler;
import com.geotowny.tab.TabListManager;
import com.geotowny.time.RealDayClock;
import com.geotowny.time.GameClock;
import com.geotowny.visual.BorderVisualManager;
import com.geotowny.web.WebAssetManager;
import com.geotowny.web.WebService;
import com.geotowny.world.WorldRegistry;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * GeoTowny Spigot 插件入口。
 * <p>P3：接入中央协调器、城镇服务、领地服务与 /town 核心命令。
 */
public final class GeoTownyPlugin extends JavaPlugin {

    private PluginConfig config;
    private WorldRegistry worldRegistry;
    private StorageService storage;
    private GeoTownyCore core;
    private TownService townService;
    private ClaimService claimService;
    private NationService nationService;
    private TownLogService townLogService;
    private PlotService plotService;
    private ResidentService residentService;
    private ProtectionService protectionService;
    private AntiExploitListener antiExploitListener;
    private BorderVisualManager borderVisualManager;
    private TabListManager tabListManager;
    private EconomyService economyService;
    private MarketService marketService;
    private FreeMarketService freeMarketService;
    private CheckInService checkInService;
    private RedeemService redeemService;
    private PhysicalCurrencyService physicalCurrencyService;
    private FestivalService festivalService;
    private IdeologyService ideologyService;
    private InfluenceService influenceService;
    private KillDropService killDropService;
    private WarService warService;
    private WebService webService;
    private SettlementScheduler settlementScheduler;

    @Override
    public void onEnable() {
        Log.init(getLogger());

        saveDefaultConfig();
        ConfigMigrator.migrate(this);

        this.config = new PluginConfig(getConfig());
        this.worldRegistry = new WorldRegistry(getConfig());

        try {
            this.storage = new StorageService(getDataFolder().toPath());
            this.storage.start();
        } catch (RuntimeException e) {
            Log.error("数据层初始化失败，插件将被禁用: {}", e.getMessage());
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        this.core = new GeoTownyCore(storage, config);
        this.core.load();
        this.townService = new TownService(core, config);
        this.claimService = new ClaimService(core, config);
        this.nationService = new NationService(core, config);
        this.townLogService = new TownLogService(new TownLogStore(storage.layout()));
        this.plotService = new PlotService(core);
        this.residentService = new ResidentService(core);
        this.protectionService = new ProtectionService(core, config);

        SettlementStateStore stateStore = new SettlementStateStore(storage.layout());
        this.festivalService = new FestivalService(config);
        this.economyService = new EconomyService(core, config, townService, stateStore, festivalService);
        GameClock clock = new RealDayClock();
        this.marketService = new MarketService(core, config, new MarketStore(storage.layout()), festivalService);
        this.marketService.load();
        this.marketService.initializeDay(clock.today());

        this.freeMarketService = new FreeMarketService(core, config, new FreeMarketStore(storage.layout()));
        this.freeMarketService.load();
        this.checkInService = new CheckInService(config, new CheckInStore(storage.layout()), core::saveResident);
        this.checkInService.load();
        this.redeemService = new RedeemService(config, new RedeemStore(storage.layout()), core::saveResident);
        this.redeemService.load();
        this.physicalCurrencyService = new PhysicalCurrencyService(this, core, config,
                new PhysicalCurrencyStore(storage.layout()));
        this.physicalCurrencyService.load();

        this.killDropService = new KillDropService(core, config);
        this.ideologyService = new IdeologyService(core, config);
        this.influenceService = new InfluenceService(core, config, festivalService);
        this.warService = new WarService(this, core, config, worldRegistry, townService, killDropService);
        this.warService.start();

        this.settlementScheduler = new SettlementScheduler(this, economyService, marketService,
                warService, ideologyService, influenceService, killDropService, festivalService, clock);
        this.settlementScheduler.start();

        registerAdminCommand();
        registerTownCommand();
        registerNationCommand();
        registerPlotCommand();
        registerResidentCommand();
        registerGeoCommand();
        registerShopCommand();
        registerFreeMarketCommand();
        registerCheckInCommand();
        registerRedeemCommand();
        registerPhysicalCurrencyCommand();
        getServer().getPluginManager().registerEvents(new PlayerListener(core), this);
        getServer().getPluginManager().registerEvents(new GuiListener(), this);
        getServer().getPluginManager().registerEvents(new CampaignListener(warService), this);
        getServer().getPluginManager().registerEvents(new WebChatListener(), this);
        getServer().getPluginManager().registerEvents(
                new ProtectionListener(core, protectionService, townLogService, worldRegistry), this);

        this.tabListManager = new TabListManager(this, core);
        getServer().getPluginManager().registerEvents(tabListManager, this);
        this.tabListManager.start();

        this.antiExploitListener = new AntiExploitListener(this, new AntiExploitService(), worldRegistry);
        getServer().getPluginManager().registerEvents(antiExploitListener, this);
        this.antiExploitListener.start();

        this.borderVisualManager = new BorderVisualManager(this, core, worldRegistry);
        this.borderVisualManager.start();

        this.webService = new WebService(this, core, config, new WebAssetManager(this));
        this.webService.start();

        if (getServer().getPluginManager().getPlugin("PlaceholderAPI") != null) {
            new GeoPlaceholders(core, getDescription().getVersion(), config.currencyName()).register();
            Log.info("已注册 PlaceholderAPI 扩展 %geotowny_*%。");
        }

        Log.info("GeoTowny 已启用，目标平台 Spigot 26.x，Java 运行时 {}。",
                System.getProperty("java.version"));
        Log.info("配置版本 {}，数据目录 {}，已加载城镇 {} 个、居民 {} 名。",
                config.configVersion(), getDataFolder().getAbsolutePath(),
                core.allTowns().size(), core.allResidents().size());
    }

    @Override
    public void onDisable() {
        Log.info("GeoTowny 正在关闭。");
        if (settlementScheduler != null) {
            settlementScheduler.cancel();
            settlementScheduler = null;
        }
        if (warService != null) {
            warService.stop();
            warService = null;
        }
        if (webService != null) {
            webService.stop();
            webService = null;
        }
        if (borderVisualManager != null) {
            borderVisualManager.stop();
            borderVisualManager = null;
        }
        if (tabListManager != null) {
            tabListManager.stop();
            tabListManager = null;
        }
        if (antiExploitListener != null) {
            antiExploitListener.stop();
            antiExploitListener = null;
        }
        if (marketService != null) {
            marketService.save();
            marketService = null;
        }
        if (freeMarketService != null) {
            freeMarketService.save();
            freeMarketService = null;
        }
        if (checkInService != null) {
            checkInService.save();
            checkInService = null;
        }
        if (redeemService != null) {
            redeemService.save();
            redeemService = null;
        }
        if (physicalCurrencyService != null) {
            physicalCurrencyService.save();
            physicalCurrencyService = null;
        }
        if (storage != null) {
            storage.close();
            storage = null;
        }
        this.economyService = null;
        this.festivalService = null;
        this.ideologyService = null;
        this.influenceService = null;
        this.killDropService = null;
        this.townLogService = null;
        this.nationService = null;
        this.plotService = null;
        this.residentService = null;
        this.protectionService = null;
        this.core = null;
        this.townService = null;
        this.claimService = null;
        this.worldRegistry = null;
        this.config = null;
    }

    private void registerAdminCommand() {
        PluginCommand command = getCommand("geotowny");
        if (command == null) {
            Log.error("plugin.yml 未定义 geotowny 命令，插件将无法管理。");
            return;
        }
        GeoAdminCommand executor = new GeoAdminCommand(this);
        command.setExecutor(executor);
        command.setTabCompleter(executor);
    }

    private void registerTownCommand() {
        PluginCommand command = getCommand("town");
        if (command == null) {
            Log.error("plugin.yml 未定义 town 命令。");
            return;
        }
        TownCommand executor = new TownCommand(this, core, townService, claimService, townLogService, worldRegistry);
        command.setExecutor(executor);
        command.setTabCompleter(executor);
    }

    private void registerNationCommand() {
        PluginCommand command = getCommand("nation");
        if (command == null) {
            Log.error("plugin.yml 未定义 nation 命令。");
            return;
        }
        NationCommand executor = new NationCommand(this, core, nationService, warService,
                ideologyService, influenceService, worldRegistry);
        command.setExecutor(executor);
        command.setTabCompleter(executor);
    }

    private void registerPlotCommand() {
        PluginCommand command = getCommand("plot");
        if (command == null) {
            Log.error("plugin.yml 未定义 plot 命令。");
            return;
        }
        PlotCommand executor = new PlotCommand(this, core, plotService, worldRegistry);
        command.setExecutor(executor);
        command.setTabCompleter(executor);
    }

    private void registerResidentCommand() {
        PluginCommand command = getCommand("resident");
        if (command == null) {
            Log.error("plugin.yml 未定义 resident 命令。");
            return;
        }
        ResidentCommand executor = new ResidentCommand(core, residentService);
        command.setExecutor(executor);
        command.setTabCompleter(executor);
    }

    private void registerGeoCommand() {
        PluginCommand command = getCommand("geo");
        if (command == null) {
            Log.error("plugin.yml 未定义 geo 命令。");
            return;
        }
        GeoCommand executor = new GeoCommand(this, core);
        command.setExecutor(executor);
        command.setTabCompleter(executor);
    }

    private void registerShopCommand() {
        PluginCommand command = getCommand("shop");
        if (command == null) {
            Log.error("plugin.yml 未定义 shop 命令。");
            return;
        }
        ShopCommand executor = new ShopCommand(this, marketService, freeMarketService, core, config);
        command.setExecutor(executor);
        command.setTabCompleter(executor);
    }

    private void registerFreeMarketCommand() {
        PluginCommand command = getCommand("fm");
        if (command == null) {
            Log.error("plugin.yml 未定义 fm 命令。");
            return;
        }
        FreeMarketCommand executor = new FreeMarketCommand(this, freeMarketService, core);
        command.setExecutor(executor);
        command.setTabCompleter(executor);
    }

    private void registerCheckInCommand() {
        PluginCommand command = getCommand("checkin");
        if (command == null) {
            Log.error("plugin.yml 未定义 checkin 命令。");
            return;
        }
        CheckInCommand executor = new CheckInCommand(this, checkInService, core);
        command.setExecutor(executor);
    }

    private void registerRedeemCommand() {
        PluginCommand command = getCommand("redeem");
        if (command == null) {
            Log.error("plugin.yml 未定义 redeem 命令。");
            return;
        }
        RedeemCommand executor = new RedeemCommand(this, redeemService, core);
        command.setExecutor(executor);
        command.setTabCompleter(executor);
    }

    private void registerPhysicalCurrencyCommand() {
        PluginCommand command = getCommand("currency");
        if (command == null) {
            Log.error("plugin.yml 未定义 currency 命令。");
            return;
        }
        PhysicalCurrencyCommand executor = new PhysicalCurrencyCommand(this, core, config);
        command.setExecutor(executor);
        command.setTabCompleter(executor);
    }

    public PluginConfig config() {
        return config;
    }

    public WorldRegistry worlds() {
        return worldRegistry;
    }

    public StorageService storage() {
        return storage;
    }

    public GeoTownyCore core() {
        return core;
    }

    public TownService townService() {
        return townService;
    }

    public ClaimService claimService() {
        return claimService;
    }

    public EconomyService economyService() {
        return economyService;
    }

    public MarketService marketService() {
        return marketService;
    }

    public FreeMarketService freeMarketService() {
        return freeMarketService;
    }

    public CheckInService checkInService() {
        return checkInService;
    }

    public RedeemService redeemService() {
        return redeemService;
    }

    public PhysicalCurrencyService physicalCurrencyService() {
        return physicalCurrencyService;
    }

    public WebService webService() {
        return webService;
    }

    public TownLogService townLogService() {
        return townLogService;
    }

    public NationService nationService() {
        return nationService;
    }

    public PlotService plotService() {
        return plotService;
    }

    public ResidentService residentService() {
        return residentService;
    }

    /**
     * 重新加载配置。数据层与领域服务不随配置重载。
     */
    public void reloadPluginConfig() {
        reloadConfig();
        ConfigMigrator.migrate(this);
        this.config = new PluginConfig(getConfig());
        this.worldRegistry = new WorldRegistry(getConfig());
        Log.info("配置已重新加载（部分服务参数需重启生效）。");
    }
}
package com.geotowny.placeholder;

import com.geotowny.core.GeoTownyCore;
import com.geotowny.domain.model.Nation;
import com.geotowny.domain.model.Resident;
import com.geotowny.domain.model.Town;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;

import java.util.Locale;

/**
 * PlaceholderAPI 扩展：%geotowny_<key>%。
 */
public final class GeoPlaceholders extends PlaceholderExpansion {

    private final GeoTownyCore core;
    private final String version;
    private final String currency;

    public GeoPlaceholders(GeoTownyCore core, String version, String currency) {
        this.core = core;
        this.version = version;
        this.currency = currency;
    }

    @Override
    public String getIdentifier() {
        return "geotowny";
    }

    @Override
    public String getAuthor() {
        return "GeoTowny Contributors";
    }

    @Override
    public String getVersion() {
        return version;
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String onRequest(OfflinePlayer player, String params) {
        if (params == null) {
            return "";
        }
        String key = params.toLowerCase(Locale.ROOT);
        if (key.equals("version")) {
            return version;
        }
        if (key.equals("currency")) {
            return currency;
        }
        if (player == null) {
            return "";
        }
        Resident resident = core.getResident(player.getUniqueId());
        if (resident == null) {
            return "";
        }
        Town town = core.getTown(resident.getTownUuid());
        Nation nation = town != null && town.hasNation() ? core.getNation(town.getNationUuid()) : null;

        return switch (key) {
            case "balance" -> resident.getBalance().format();
            case "town" -> town == null ? "" : town.getName();
            case "town_balance" -> town == null ? "" : town.getBalance().format();
            case "town_rank" -> resident.getTownRank() == null ? "" : resident.getTownRank().name();
            case "nation" -> nation == null ? "" : nation.getName();
            case "nation_balance" -> nation == null ? "" : nation.getBalance().format();
            case "nation_rank" -> resident.getNationRank() == null ? "" : resident.getNationRank().name();
            case "ideology" -> nation == null ? "" : nation.getIdeology().name();
            case "influence" -> nation == null ? "" : String.format("%.1f", nation.getInfluence());
            case "stability" -> nation == null ? "" : String.format("%.1f", nation.getStability());
            case "tabprefix", "prefix" -> prefix(resident, town, nation);
            default -> null;
        };
    }

    private String prefix(Resident resident, Town town, Nation nation) {
        StringBuilder builder = new StringBuilder();
        if (nation != null) {
            builder.append(ChatColor.GOLD).append('[').append(nation.getName()).append(']');
        }
        if (town != null) {
            builder.append(ChatColor.GREEN).append('[').append(town.getName()).append(']');
        }
        return builder.toString();
    }
}
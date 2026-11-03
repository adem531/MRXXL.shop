package com.mrxxl.shop;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.EntityType;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * config.yml içindeki kategorileri, eşyaları ve satış fiyatlarını okur.
 */
public final class ShopManager {

    private final ShopPlugin plugin;
    private final Map<String, Category> categories = new LinkedHashMap<>();
    private final Map<Material, Double> sellPrices = new HashMap<>();

    public ShopManager(ShopPlugin plugin) {
        this.plugin = plugin;
    }

    public void load() {
        categories.clear();
        sellPrices.clear();

        ConfigurationSection root = plugin.getConfig().getConfigurationSection("categories");
        if (root == null) {
            plugin.getLogger().warning("config.yml içinde 'categories' bölümü bulunamadı.");
            return;
        }

        int totalItems = 0;
        for (String id : root.getKeys(false)) {
            ConfigurationSection sec = root.getConfigurationSection(id);
            if (sec == null) {
                continue;
            }

            Material icon = Material.matchMaterial(sec.getString("icon", "CHEST"));
            if (icon == null || !icon.isItem() || icon.isAir()) {
                plugin.getLogger().warning("Kategori '" + id + "' için geçersiz icon, CHEST kullanıldı.");
                icon = Material.CHEST;
            }

            List<ShopItem> items = new ArrayList<>();
            ConfigurationSection itemsSec = sec.getConfigurationSection("items");
            if (itemsSec != null) {
                for (String key : itemsSec.getKeys(false)) {
                    Material mat = Material.matchMaterial(key);
                    if (mat == null || !mat.isItem() || mat.isAir()) {
                        plugin.getLogger().warning("Kategori '" + id + "': geçersiz malzeme '" + key + "', atlandı.");
                        continue;
                    }
                    double buy = itemsSec.getDouble(key + ".buy", -1);
                    double sell = itemsSec.getDouble(key + ".sell", -1);
                    if (buy < 0 && sell < 0) {
                        plugin.getLogger().warning("Kategori '" + id + "': '" + key + "' için ne buy ne sell var, atlandı.");
                        continue;
                    }
                    ShopItem item = new ShopItem(mat, buy, sell);
                    items.add(item);
                    boolean shardCategory = "shard".equalsIgnoreCase(sec.getString("currency", "money"));
                    if (item.canSell() && !shardCategory) {
                        sellPrices.putIfAbsent(mat, sell);
                    }
                }
            }

            // Spawner'lar: spawners: { ZOMBIE: {buy: 5000, name: "&2Zombi Spawner"} }
            ConfigurationSection spawnersSec = sec.getConfigurationSection("spawners");
            if (spawnersSec != null) {
                for (String key : spawnersSec.getKeys(false)) {
                    EntityType type;
                    try {
                        type = EntityType.valueOf(key.toUpperCase(Locale.ROOT));
                    } catch (IllegalArgumentException e) {
                        plugin.getLogger().warning("Kategori '" + id + "': geçersiz canlı '" + key + "', atlandı.");
                        continue;
                    }
                    double buy = spawnersSec.getDouble(key + ".buy", -1);
                    if (buy < 0) {
                        plugin.getLogger().warning("Kategori '" + id + "': '" + key + "' spawner'ı için buy yok, atlandı.");
                        continue;
                    }
                    String name = plugin.color(spawnersSec.getString(key + ".name", "&d" + key + " Spawner"));
                    items.add(new ShopItem(Material.SPAWNER, buy, -1, type, name));
                }
            }

            String lowerId = id.toLowerCase(Locale.ROOT);
            boolean shard = "shard".equalsIgnoreCase(sec.getString("currency", "money"));
            categories.put(lowerId, new Category(
                    lowerId,
                    plugin.color(sec.getString("name", id)),
                    icon,
                    sec.getInt("slot", categories.size()),
                    shard,
                    items));
            totalItems += items.size();
        }

        // Sadece /sat ile satılabilen ek eşyalar
        ConfigurationSection extra = plugin.getConfig().getConfigurationSection("extra-sell");
        if (extra != null) {
            for (String key : extra.getKeys(false)) {
                Material mat = Material.matchMaterial(key);
                if (mat == null || !mat.isItem() || mat.isAir()) {
                    plugin.getLogger().warning("extra-sell: geçersiz malzeme '" + key + "', atlandı.");
                    continue;
                }
                double price = extra.getDouble(key, -1);
                if (price >= 0) {
                    sellPrices.putIfAbsent(mat, price);
                }
            }
        }

        plugin.getLogger().info(categories.size() + " kategori, " + totalItems
                + " market eşyası, " + sellPrices.size() + " satılabilir eşya yüklendi.");
    }

    public Collection<Category> categories() {
        return categories.values();
    }

    public Category get(String id) {
        return id == null ? null : categories.get(id.toLowerCase(Locale.ROOT));
    }

    /** Eşyanın birim satış fiyatı; satılamıyorsa -1. */
    public double sellPrice(Material material) {
        return sellPrices.getOrDefault(material, -1.0);
    }
}

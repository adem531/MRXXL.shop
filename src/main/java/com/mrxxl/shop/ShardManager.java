package com.mrxxl.shop;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Oyuncu shard bakiyeleri. plugins/MRXXlShop/shards.yml dosyasında saklanır.
 */
public final class ShardManager {

    private final ShopPlugin plugin;
    private final File file;
    private final Map<UUID, Long> balances = new HashMap<>();
    private boolean dirty;

    public ShardManager(ShopPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "shards.yml");
    }

    public void load() {
        balances.clear();
        if (!file.exists()) {
            return;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection section = yaml.getConfigurationSection("balances");
        if (section == null) {
            return;
        }
        for (String key : section.getKeys(false)) {
            try {
                balances.put(UUID.fromString(key), section.getLong(key));
            } catch (IllegalArgumentException ignored) {
                plugin.getLogger().warning("shards.yml içinde geçersiz UUID: " + key);
            }
        }
    }

    public long get(UUID id) {
        return balances.getOrDefault(id, 0L);
    }

    public void add(UUID id, long amount) {
        balances.merge(id, amount, Long::sum);
        dirty = true;
    }

    /** Yeterli shard varsa düşer ve true döner. */
    public boolean take(UUID id, long amount) {
        long current = get(id);
        if (amount < 0 || current < amount) {
            return false;
        }
        balances.put(id, current - amount);
        dirty = true;
        return true;
    }

    public void saveIfDirty() {
        if (dirty) {
            save();
        }
    }

    public void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        for (Map.Entry<UUID, Long> e : balances.entrySet()) {
            yaml.set("balances." + e.getKey(), e.getValue());
        }
        try {
            plugin.getDataFolder().mkdirs();
            yaml.save(file);
            dirty = false;
        } catch (IOException ex) {
            plugin.getLogger().severe("shards.yml kaydedilemedi: " + ex.getMessage());
        }
    }
}

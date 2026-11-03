package com.mrxxl.shop;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Material;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

public final class ShopPlugin extends JavaPlugin {

    private Economy economy;
    private ShopManager shopManager;
    private ShopGUI gui;
    private SellGUI sellGui;
    private ShardManager shardManager;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        if (!setupEconomy()) {
            getLogger().severe("Vault ekonomi sağlayıcısı bulunamadı! Vault ve EssentialsX kurulu mu?");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        shardManager = new ShardManager(this);
        shardManager.load();

        shopManager = new ShopManager(this);
        shopManager.load();

        sellGui = new SellGUI(this);
        gui = new ShopGUI(this);
        gui.setSellGUI(sellGui);
        getServer().getPluginManager().registerEvents(gui, this);
        getServer().getPluginManager().registerEvents(sellGui, this);

        ShopCommand shopCommand = new ShopCommand(this, gui);
        PluginCommand shop = getCommand("shop");
        if (shop != null) {
            shop.setExecutor(shopCommand);
            shop.setTabCompleter(shopCommand);
        }

        SellCommand sellCommand = new SellCommand(this, sellGui);
        PluginCommand sat = getCommand("sat");
        if (sat != null) {
            sat.setExecutor(sellCommand);
            sat.setTabCompleter(sellCommand);
        }

        ShardCommand shardCommand = new ShardCommand(this);
        PluginCommand shard = getCommand("shard");
        if (shard != null) {
            shard.setExecutor(shardCommand);
            shard.setTabCompleter(shardCommand);
        }

        startShardTasks();

        getLogger().info("MRXXlShop aktif. Ekonomi: " + economy.getName());
    }

    /** Oyun süresi ödülü ve shard dosyasını düzenli kaydetme. */
    private void startShardTasks() {
        int amount = getConfig().getInt("shards.reward-amount", 1);
        long minutes = Math.max(1, getConfig().getLong("shards.reward-interval-minutes", 5));
        long ticks = minutes * 60L * 20L;

        if (amount > 0) {
            getServer().getScheduler().runTaskTimer(this, () -> {
                boolean notify = getConfig().getBoolean("shards.notify", true);
                for (Player p : getServer().getOnlinePlayers()) {
                    shardManager.add(p.getUniqueId(), amount);
                    if (notify) {
                        p.sendMessage(msg("shard-reward", "amount", String.valueOf(amount)));
                    }
                }
            }, ticks, ticks);
        }

        // 5 dakikada bir kaydet
        getServer().getScheduler().runTaskTimer(this, shardManager::saveIfDirty, 6000L, 6000L);
    }

    @Override
    public void onDisable() {
        if (shardManager != null) {
            shardManager.save();
        }
        // /sat menüsü açıkken kapanırsa içindeki eşyalar oyunculara geri verilsin
        for (Player p : getServer().getOnlinePlayers()) {
            if (p.getOpenInventory().getTopInventory().getHolder() instanceof SellGUI.SellHolder) {
                p.closeInventory();
            }
        }
    }

    private boolean setupEconomy() {
        if (getServer().getPluginManager().getPlugin("Vault") == null) {
            return false;
        }
        RegisteredServiceProvider<Economy> rsp =
                getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp == null) {
            return false;
        }
        economy = rsp.getProvider();
        return economy != null;
    }

    public void reload() {
        reloadConfig();
        shopManager.load();
    }

    public Economy economy() {
        return economy;
    }

    public ShardManager shards() {
        return shardManager;
    }

    public ShopManager shopManager() {
        return shopManager;
    }

    public String color(String s) {
        return ChatColor.translateAlternateColorCodes('&', s);
    }

    /**
     * Config'ten bir metni okur, {anahtar} yer tutucularını doldurur ve renklendirir.
     * pairs: "anahtar1", "değer1", "anahtar2", "değer2", ...
     */
    public String text(String path, String... pairs) {
        String raw = getConfig().getString(path, "&cEksik ayar: " + path);
        for (int i = 0; i + 1 < pairs.length; i += 2) {
            raw = raw.replace("{" + pairs[i] + "}", pairs[i + 1]);
        }
        return color(raw);
    }

    /** messages.* altındaki bir mesajı prefix ile birlikte döndürür. */
    public String msg(String key, String... pairs) {
        return color(getConfig().getString("messages.prefix", "")) + text("messages." + key, pairs);
    }

    /**
     * {item} yer tutucusu olan bir mesajı gönderir. Eşya adı, oyuncunun kendi
     * oyun dilinde (Türkçe istemci için Türkçe) görünsün diye çeviri bileşeni olarak eklenir.
     */
    public void sendItemMsg(CommandSender to, String key, Material material, String... pairs) {
        Component message = LegacyComponentSerializer.legacySection()
                .deserialize(msg(key, pairs))
                .replaceText(b -> b.matchLiteral("{item}")
                        .replacement(Component.translatable(material.translationKey())));
        to.sendMessage(message);
    }
}

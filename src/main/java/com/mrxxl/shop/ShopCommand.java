package com.mrxxl.shop;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class ShopCommand implements TabExecutor {

    private final ShopPlugin plugin;
    private final ShopGUI gui;

    public ShopCommand(ShopPlugin plugin, ShopGUI gui) {
        this.plugin = plugin;
        this.gui = gui;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length >= 1 && args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission("mrxxlshop.admin")) {
                sender.sendMessage(plugin.msg("no-permission"));
                return true;
            }
            plugin.reload();
            sender.sendMessage(plugin.msg("reloaded"));
            return true;
        }

        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.msg("players-only"));
            return true;
        }
        if (!player.hasPermission("mrxxlshop.use")) {
            player.sendMessage(plugin.msg("no-permission"));
            return true;
        }

        if (args.length >= 1) {
            Category category = plugin.shopManager().get(args[0]);
            if (category == null) {
                player.sendMessage(plugin.msg("unknown-category"));
                return true;
            }
            gui.openCategory(player, category, 0);
            return true;
        }

        gui.openMain(player);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> result = new ArrayList<>();
        if (args.length != 1) {
            return result;
        }
        String prefix = args[0].toLowerCase(Locale.ROOT);
        if (sender.hasPermission("mrxxlshop.admin") && "reload".startsWith(prefix)) {
            result.add("reload");
        }
        for (Category c : plugin.shopManager().categories()) {
            if (c.id().startsWith(prefix)) {
                result.add(c.id());
            }
        }
        return result;
    }
}

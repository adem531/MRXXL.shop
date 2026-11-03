package com.mrxxl.shop;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * /sat        -> satış menüsünü açar
 * /sat hepsi  -> envanterdeki satılabilen her şeyi satar
 * /sat el     -> elindeki eşyayı satar
 */
public final class SellCommand implements TabExecutor {

    private final ShopPlugin plugin;
    private final SellGUI sellGui;

    public SellCommand(ShopPlugin plugin, SellGUI sellGui) {
        this.plugin = plugin;
        this.sellGui = sellGui;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.msg("players-only"));
            return true;
        }
        if (!player.hasPermission("mrxxlshop.sell")) {
            player.sendMessage(plugin.msg("no-permission"));
            return true;
        }

        if (args.length >= 1) {
            String sub = args[0].toLowerCase(Locale.ROOT);
            if (sub.equals("hepsi") || sub.equals("all")) {
                sellGui.sellAll(player);
                return true;
            }
            if (sub.equals("el") || sub.equals("hand")) {
                sellGui.sellHand(player);
                return true;
            }
        }

        sellGui.open(player);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> result = new ArrayList<>();
        if (args.length == 1) {
            String prefix = args[0].toLowerCase(Locale.ROOT);
            for (String s : List.of("hepsi", "el")) {
                if (s.startsWith(prefix)) {
                    result.add(s);
                }
            }
        }
        return result;
    }
}

package com.mrxxl.shop;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * /shard                       -> kendi shard bakiyeni gösterir
 * /shard ver <oyuncu> <miktar> -> (admin) shard verir
 * /shard al <oyuncu> <miktar>  -> (admin) shard alır
 */
public final class ShardCommand implements TabExecutor {

    private final ShopPlugin plugin;

    public ShardCommand(ShopPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(plugin.msg("players-only"));
                return true;
            }
            player.sendMessage(plugin.msg("shard-balance",
                    "amount", String.valueOf(plugin.shards().get(player.getUniqueId()))));
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        if (!sub.equals("ver") && !sub.equals("al")) {
            sender.sendMessage(plugin.msg("shard-usage"));
            return true;
        }
        if (!sender.hasPermission("mrxxlshop.admin")) {
            sender.sendMessage(plugin.msg("no-permission"));
            return true;
        }
        if (args.length < 3) {
            sender.sendMessage(plugin.msg("shard-usage"));
            return true;
        }

        OfflinePlayer target = Bukkit.getOfflinePlayerIfCached(args[1]);
        if (target == null) {
            target = Bukkit.getPlayerExact(args[1]);
        }
        if (target == null) {
            sender.sendMessage(plugin.msg("player-not-found"));
            return true;
        }

        long amount;
        try {
            amount = Long.parseLong(args[2]);
        } catch (NumberFormatException e) {
            sender.sendMessage(plugin.msg("shard-usage"));
            return true;
        }
        if (amount <= 0) {
            sender.sendMessage(plugin.msg("shard-usage"));
            return true;
        }

        String name = target.getName() != null ? target.getName() : args[1];
        if (sub.equals("ver")) {
            plugin.shards().add(target.getUniqueId(), amount);
            sender.sendMessage(plugin.msg("shard-given",
                    "player", name, "amount", String.valueOf(amount)));
        } else {
            if (!plugin.shards().take(target.getUniqueId(), amount)) {
                sender.sendMessage(plugin.msg("shard-take-failed", "player", name));
                return true;
            }
            sender.sendMessage(plugin.msg("shard-taken",
                    "player", name, "amount", String.valueOf(amount)));
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> result = new ArrayList<>();
        if (!sender.hasPermission("mrxxlshop.admin")) {
            return result;
        }
        if (args.length == 1) {
            String prefix = args[0].toLowerCase(Locale.ROOT);
            for (String s : List.of("ver", "al")) {
                if (s.startsWith(prefix)) {
                    result.add(s);
                }
            }
        } else if (args.length == 2) {
            String prefix = args[1].toLowerCase(Locale.ROOT);
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.getName().toLowerCase(Locale.ROOT).startsWith(prefix)) {
                    result.add(p.getName());
                }
            }
        }
        return result;
    }
}

package com.mrxxl.shop;

import org.bukkit.Material;

import java.util.List;

/**
 * Market kategorisi (ana menüdeki bir buton ve içindeki eşyalar).
 * shard = true ise fiyatlar para yerine shard ile ödenir.
 */
public record Category(String id, String name, Material icon, int slot, boolean shard, List<ShopItem> items) {
}

package com.mrxxl.shop;

import org.bukkit.Material;
import org.bukkit.entity.EntityType;

/**
 * Marketteki tek bir eşya. buy/sell negatifse o işlem kapalıdır.
 * spawner doluysa eşya, o canlıyı üreten bir spawner'dır (name = gösterilen ad).
 */
public record ShopItem(Material material, double buy, double sell, EntityType spawner, String name) {

    public ShopItem(Material material, double buy, double sell) {
        this(material, buy, sell, null, null);
    }

    public boolean canBuy() {
        return buy >= 0;
    }

    public boolean canSell() {
        return sell >= 0;
    }

    public boolean isSpawner() {
        return spawner != null;
    }
}

package com.mrxxl.shop;

import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.CreatureSpawner;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/**
 * Ana menü + sayfalı kategori menüleri ve alış/satış mantığı.
 *
 * Kategori menüsünde:
 *   Sol tık         -> 1 adet al
 *   Shift + Sol tık -> bir yığın al
 *   Sağ tık         -> 1 adet sat
 *   Shift + Sağ tık -> envanterdeki hepsini sat
 *
 * Shard kategorilerinde (spawner market) ödeme shard iledir, spawner'lar tek tek alınır.
 */
public final class ShopGUI implements Listener {

    private static final int ITEMS_PER_PAGE = 45;
    private static final int SLOT_BACK = 45;
    private static final int SLOT_PREV = 48;
    private static final int SLOT_BALANCE = 49;
    private static final int SLOT_NEXT = 50;

    private final ShopPlugin plugin;
    private SellGUI sellGui;

    public ShopGUI(ShopPlugin plugin) {
        this.plugin = plugin;
    }

    public void setSellGUI(SellGUI sellGui) {
        this.sellGui = sellGui;
    }

    /** Menüyü tanımak için kullanılan holder. category == null ise ana menüdür. */
    private static final class Holder implements InventoryHolder {
        final Category category;
        final int page;
        Inventory inventory;

        Holder(Category category, int page) {
            this.category = category;
            this.page = page;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    private int sellSlot() {
        return plugin.getConfig().getInt("settings.sell-button-slot", 40);
    }

    private Material fillerMaterial() {
        Material m = Material.matchMaterial(plugin.getConfig().getString(
                "settings.filler-material", "LIGHT_BLUE_STAINED_GLASS_PANE"));
        return m == null || m.isAir() ? Material.LIGHT_BLUE_STAINED_GLASS_PANE : m;
    }

    // ------------------------------------------------------------------
    //  Menüleri açma
    // ------------------------------------------------------------------

    public void openMain(Player player) {
        int rows = Math.max(1, Math.min(6, plugin.getConfig().getInt("settings.main-menu-rows", 5)));
        Holder holder = new Holder(null, 0);
        Inventory inv = Bukkit.createInventory(holder, rows * 9, plugin.text("settings.main-menu-title"));
        holder.inventory = inv;

        fill(inv, 0, inv.getSize());

        for (Category c : plugin.shopManager().categories()) {
            if (c.slot() < 0 || c.slot() >= inv.getSize()) {
                continue;
            }
            List<String> lore = new ArrayList<>();
            lore.add(plugin.text("lore.open-category", "count", String.valueOf(c.items().size())));
            if (c.shard()) {
                lore.add(plugin.text("lore.shard-category"));
            }
            inv.setItem(c.slot(), button(c.icon(), c.name(), lore));
        }

        int sellSlot = sellSlot();
        if (sellSlot >= 0 && sellSlot < inv.getSize()) {
            Material sellIcon = Material.matchMaterial(
                    plugin.getConfig().getString("settings.sell-button-icon", "EMERALD"));
            if (sellIcon == null || sellIcon.isAir()) {
                sellIcon = Material.EMERALD;
            }
            List<String> sellLore = new ArrayList<>();
            sellLore.add(plugin.text("lore.sell-open"));
            inv.setItem(sellSlot, button(sellIcon, plugin.text("buttons.sell"), sellLore));
        }

        player.openInventory(inv);
    }

    public void openCategory(Player player, Category category, int page) {
        int maxPage = maxPage(category);
        page = Math.max(0, Math.min(page, maxPage));

        Holder holder = new Holder(category, page);
        Inventory inv = Bukkit.createInventory(holder, 54, plugin.text("settings.category-title",
                "category", category.name(),
                "page", String.valueOf(page + 1),
                "pages", String.valueOf(maxPage + 1)));
        holder.inventory = inv;

        List<ShopItem> items = category.items();
        for (int i = 0; i < ITEMS_PER_PAGE; i++) {
            int index = page * ITEMS_PER_PAGE + i;
            if (index >= items.size()) {
                break;
            }
            inv.setItem(i, shopItemStack(category, items.get(index)));
        }

        fill(inv, ITEMS_PER_PAGE, 54);
        inv.setItem(SLOT_BACK, button(Material.ARROW, plugin.text("buttons.back"), null));
        if (page > 0) {
            inv.setItem(SLOT_PREV, button(Material.PAPER, plugin.text("buttons.prev"), null));
        }
        if (page < maxPage) {
            inv.setItem(SLOT_NEXT, button(Material.PAPER, plugin.text("buttons.next"), null));
        }
        refreshBalance(inv, player, category);

        player.openInventory(inv);
    }

    private static int maxPage(Category category) {
        return Math.max(0, (category.items().size() - 1) / ITEMS_PER_PAGE);
    }

    // ------------------------------------------------------------------
    //  Tıklama olayları
    // ------------------------------------------------------------------

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof Holder holder)) {
            return;
        }
        // Market menüsü açıkken hiçbir eşya yerinden oynamasın (shift-click dahil).
        event.setCancelled(true);

        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        Inventory clicked = event.getClickedInventory();
        if (clicked == null || !(clicked.getHolder() instanceof Holder)) {
            return; // oyuncunun kendi envanterine tıkladı
        }

        int slot = event.getSlot();
        if (holder.category == null) {
            handleMain(player, slot);
        } else {
            handleCategory(player, holder, event.getClick(), slot);
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof Holder) {
            event.setCancelled(true);
        }
    }

    private void handleMain(Player player, int slot) {
        if (slot == sellSlot() && sellGui != null) {
            sellGui.open(player);
            return;
        }
        for (Category c : plugin.shopManager().categories()) {
            if (c.slot() == slot) {
                openCategory(player, c, 0);
                return;
            }
        }
    }

    private void handleCategory(Player player, Holder holder, ClickType click, int slot) {
        Category category = holder.category;

        if (slot == SLOT_BACK) {
            openMain(player);
            return;
        }
        if (slot == SLOT_PREV) {
            if (holder.page > 0) {
                openCategory(player, category, holder.page - 1);
            }
            return;
        }
        if (slot == SLOT_NEXT) {
            if (holder.page < maxPage(category)) {
                openCategory(player, category, holder.page + 1);
            }
            return;
        }
        if (slot < 0 || slot >= ITEMS_PER_PAGE) {
            return;
        }

        int index = holder.page * ITEMS_PER_PAGE + slot;
        if (index >= category.items().size()) {
            return;
        }
        ShopItem item = category.items().get(index);

        switch (click) {
            case LEFT -> buy(player, category, item, 1);
            case SHIFT_LEFT -> buy(player, category, item, item.material().getMaxStackSize());
            case RIGHT -> sell(player, item, 1);
            case SHIFT_RIGHT -> sell(player, item, Integer.MAX_VALUE);
            default -> {
                return;
            }
        }
        refreshBalance(holder.inventory, player, category);
    }

    // ------------------------------------------------------------------
    //  Alış / satış
    // ------------------------------------------------------------------

    private void buy(Player player, Category category, ShopItem item, int requested) {
        if (!item.canBuy()) {
            player.sendMessage(plugin.msg("cannot-buy"));
            return;
        }
        int amount = item.isSpawner() ? 1 : requested;
        ItemStack prototype = createStack(item);
        double total = round(item.buy() * amount);

        if (freeSpace(player, prototype) < amount) {
            player.sendMessage(plugin.msg("inventory-full"));
            return;
        }

        String priceText;
        if (category.shard()) {
            long cost = (long) Math.ceil(total);
            if (plugin.shards().get(player.getUniqueId()) < cost) {
                player.sendMessage(plugin.msg("not-enough-shards", "price", String.valueOf(cost)));
                return;
            }
            if (!plugin.shards().take(player.getUniqueId(), cost)) {
                player.sendMessage(plugin.msg("transaction-failed"));
                return;
            }
            priceText = cost + " shard";
        } else {
            Economy eco = plugin.economy();
            if (!eco.has(player, total)) {
                player.sendMessage(plugin.msg("not-enough-money", "price", eco.format(total)));
                return;
            }
            EconomyResponse response = eco.withdrawPlayer(player, total);
            if (!response.transactionSuccess()) {
                player.sendMessage(plugin.msg("transaction-failed"));
                return;
            }
            priceText = eco.format(total);
        }

        giveItems(player, prototype, amount);
        if (item.isSpawner()) {
            player.sendMessage(plugin.msg("bought-spawner", "item", item.name(), "price", priceText));
        } else {
            plugin.sendItemMsg(player, "bought", item.material(),
                    "amount", String.valueOf(amount),
                    "price", priceText);
        }
    }

    private void sell(Player player, ShopItem item, int requested) {
        if (!item.canSell()) {
            player.sendMessage(plugin.msg("cannot-sell"));
            return;
        }
        Economy eco = plugin.economy();

        int amount = Math.min(requested, countItems(player, item.material()));
        if (amount <= 0) {
            player.sendMessage(plugin.msg("no-items"));
            return;
        }

        double total = round(item.sell() * amount);

        removeItems(player, item.material(), amount);
        EconomyResponse response = eco.depositPlayer(player, total);
        if (!response.transactionSuccess()) {
            giveItems(player, new ItemStack(item.material()), amount); // eşyaları geri ver
            player.sendMessage(plugin.msg("transaction-failed"));
            return;
        }

        plugin.sendItemMsg(player, "sold", item.material(),
                "amount", String.valueOf(amount),
                "price", eco.format(total));
    }

    // ------------------------------------------------------------------
    //  Envanter yardımcıları
    //  Marketten satışta sadece "düz" eşyalar sayılır; büyülü/hasarlı eşyalar /sat ile satılır.
    // ------------------------------------------------------------------

    /** Satın alınacak gerçek eşya (spawner ise canlı türü ayarlanmış spawner). */
    private ItemStack createStack(ShopItem item) {
        ItemStack stack = new ItemStack(item.material());
        if (item.isSpawner()) {
            ItemMeta meta = stack.getItemMeta();
            if (meta instanceof BlockStateMeta blockMeta
                    && blockMeta.getBlockState() instanceof CreatureSpawner spawner) {
                spawner.setSpawnedType(item.spawner());
                blockMeta.setBlockState(spawner);
                blockMeta.setDisplayName(item.name());
                stack.setItemMeta(blockMeta);
            }
        }
        return stack;
    }

    private int freeSpace(Player player, ItemStack prototype) {
        int max = prototype.getMaxStackSize();
        int space = 0;
        for (ItemStack stack : player.getInventory().getStorageContents()) {
            if (stack == null || stack.getType().isAir()) {
                space += max;
            } else if (stack.isSimilar(prototype)) {
                space += Math.max(0, max - stack.getAmount());
            }
        }
        return space;
    }

    private int countItems(Player player, Material material) {
        int count = 0;
        for (ItemStack stack : player.getInventory().getStorageContents()) {
            if (stack != null && stack.getType() == material && !stack.hasItemMeta()) {
                count += stack.getAmount();
            }
        }
        return count;
    }

    private void removeItems(Player player, Material material, int amount) {
        PlayerInventory inv = player.getInventory();
        ItemStack[] contents = inv.getStorageContents();
        int left = amount;
        for (int i = 0; i < contents.length && left > 0; i++) {
            ItemStack stack = contents[i];
            if (stack == null || stack.getType() != material || stack.hasItemMeta()) {
                continue;
            }
            int take = Math.min(left, stack.getAmount());
            if (take == stack.getAmount()) {
                contents[i] = null;
            } else {
                stack.setAmount(stack.getAmount() - take);
            }
            left -= take;
        }
        inv.setStorageContents(contents);
    }

    private void giveItems(Player player, ItemStack prototype, int amount) {
        int max = prototype.getMaxStackSize();
        int left = amount;
        while (left > 0) {
            int n = Math.min(left, max);
            ItemStack stack = prototype.clone();
            stack.setAmount(n);
            player.getInventory().addItem(stack);
            left -= n;
        }
    }

    // ------------------------------------------------------------------
    //  Görünüm yardımcıları
    // ------------------------------------------------------------------

    private String priceText(Category category, double value) {
        if (category.shard()) {
            return (long) Math.ceil(value) + " shard";
        }
        return plugin.economy().format(value);
    }

    private ItemStack shopItemStack(Category category, ShopItem item) {
        ItemStack stack = createStack(item);
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return stack;
        }

        List<String> lore = new ArrayList<>();
        lore.add(item.canBuy()
                ? plugin.text("lore.buy", "price", priceText(category, item.buy()))
                : plugin.text("lore.cannot-buy"));
        if (!category.shard()) {
            lore.add(item.canSell()
                    ? plugin.text("lore.sell", "price", priceText(category, item.sell()))
                    : plugin.text("lore.cannot-sell"));
        }
        lore.add(" ");
        if (item.canBuy()) {
            if (item.isSpawner()) {
                lore.add(plugin.text("lore.click-buy-spawner"));
            } else {
                lore.add(plugin.text("lore.click-buy-1"));
                lore.add(plugin.text("lore.click-buy-2",
                        "stack", String.valueOf(item.material().getMaxStackSize())));
            }
        }
        if (item.canSell() && !category.shard()) {
            lore.add(plugin.text("lore.click-sell-1"));
            lore.add(plugin.text("lore.click-sell-2"));
        }

        meta.setLore(lore);
        stack.setItemMeta(meta);
        return stack;
    }

    private ItemStack button(Material material, String name, List<String> lore) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            if (lore != null) {
                meta.setLore(lore);
            }
            stack.setItemMeta(meta);
        }
        return stack;
    }

    private void fill(Inventory inv, int from, int to) {
        if (!plugin.getConfig().getBoolean("settings.filler", true)) {
            return;
        }
        ItemStack pane = button(fillerMaterial(), " ", null);
        for (int i = from; i < to; i++) {
            inv.setItem(i, pane);
        }
    }

    private void refreshBalance(Inventory inv, Player player, Category category) {
        if (inv == null || inv.getSize() != 54) {
            return;
        }
        if (category != null && category.shard()) {
            inv.setItem(SLOT_BALANCE, button(Material.AMETHYST_SHARD,
                    plugin.text("buttons.shard-balance",
                            "balance", String.valueOf(plugin.shards().get(player.getUniqueId()))), null));
            return;
        }
        String balance = plugin.economy().format(plugin.economy().getBalance(player));
        inv.setItem(SLOT_BALANCE, button(Material.GOLD_INGOT,
                plugin.text("buttons.balance", "balance", balance), null));
    }

    private static double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}

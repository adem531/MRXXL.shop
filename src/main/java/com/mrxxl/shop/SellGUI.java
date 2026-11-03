package com.mrxxl.shop;

import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.Container;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.inventory.meta.BundleMeta;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * /sat menüsü: üstteki 45 slota eşya koyulur, "Sat" butonuna basınca satılır.
 * Menü kapanırsa satılmamış eşyalar oyuncuya geri verilir.
 * Ayrıca /sat hepsi ve /sat el için satış mantığı burada.
 */
public final class SellGUI implements Listener {

    private static final int ITEM_SLOTS = 45;
    private static final int SLOT_INFO = 45;
    private static final int SLOT_CONFIRM = 49;
    private static final int SLOT_CLOSE = 53;

    private final ShopPlugin plugin;

    public SellGUI(ShopPlugin plugin) {
        this.plugin = plugin;
    }

    static final class SellHolder implements InventoryHolder {
        Inventory inventory;

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    private record Tally(int count, double total) {
    }

    // ------------------------------------------------------------------
    //  Menü
    // ------------------------------------------------------------------

    public void open(Player player) {
        SellHolder holder = new SellHolder();
        Inventory inv = Bukkit.createInventory(holder, 54, plugin.text("settings.sell-title"));
        holder.inventory = inv;

        ItemStack pane = button(fillerMaterial(), " ", null);
        for (int i = ITEM_SLOTS; i < 54; i++) {
            inv.setItem(i, pane);
        }

        List<String> infoLore = new ArrayList<>();
        for (String line : plugin.getConfig().getStringList("sell.info-lore")) {
            infoLore.add(plugin.color(line));
        }
        inv.setItem(SLOT_INFO, button(Material.BOOK, plugin.text("sell.info-name"), infoLore));
        inv.setItem(SLOT_CLOSE, button(Material.BARRIER, plugin.text("sell.close"), null));

        updateTotal(inv);
        player.openInventory(inv);
    }

    private void updateTotal(Inventory inv) {
        Tally tally = tally(inv.getContents(), 0, ITEM_SLOTS, false);

        List<String> lore = new ArrayList<>();
        if (tally.count() > 0) {
            lore.add(plugin.text("sell.confirm-lore-count", "count", String.valueOf(tally.count())));
            lore.add(plugin.text("sell.confirm-lore-total", "price", plugin.economy().format(tally.total())));
            lore.add(" ");
            lore.add(plugin.text("sell.confirm-lore-click"));
        } else {
            lore.add(plugin.text("sell.confirm-lore-empty"));
        }
        inv.setItem(SLOT_CONFIRM, button(Material.EMERALD, plugin.text("sell.confirm-name"), lore));
    }

    // ------------------------------------------------------------------
    //  Olaylar
    // ------------------------------------------------------------------

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof SellHolder holder)) {
            return;
        }
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        // Çift tıkla toplama alttaki butonları imleca çekebilir, kapatıyoruz.
        if (event.getClick() == ClickType.DOUBLE_CLICK) {
            event.setCancelled(true);
            return;
        }

        Inventory clicked = event.getClickedInventory();
        if (clicked != null && clicked.equals(event.getInventory())) {
            int slot = event.getSlot();
            if (slot >= ITEM_SLOTS) {
                event.setCancelled(true);
                if (slot == SLOT_CONFIRM) {
                    confirm(player, holder.inventory);
                } else if (slot == SLOT_CLOSE) {
                    Bukkit.getScheduler().runTask(plugin, player::closeInventory);
                }
                return;
            }
        }

        // Tıklama işlendikten sonra toplamı güncelle
        Bukkit.getScheduler().runTask(plugin, () -> updateTotal(holder.inventory));
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (!(event.getInventory().getHolder() instanceof SellHolder holder)) {
            return;
        }
        for (int rawSlot : event.getRawSlots()) {
            if (rawSlot >= ITEM_SLOTS && rawSlot < 54) {
                event.setCancelled(true);
                return;
            }
        }
        Bukkit.getScheduler().runTask(plugin, () -> updateTotal(holder.inventory));
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (!(event.getInventory().getHolder() instanceof SellHolder holder)) {
            return;
        }
        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }
        Inventory inv = holder.inventory;
        for (int i = 0; i < ITEM_SLOTS; i++) {
            ItemStack stack = inv.getItem(i);
            if (stack == null || stack.getType().isAir()) {
                continue;
            }
            inv.setItem(i, null);
            Map<Integer, ItemStack> left = player.getInventory().addItem(stack);
            for (ItemStack rest : left.values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), rest);
            }
        }
    }

    // ------------------------------------------------------------------
    //  Satış
    // ------------------------------------------------------------------

    private void confirm(Player player, Inventory inv) {
        ItemStack[] contents = inv.getContents();
        Tally tally = settle(player, contents, 0, ITEM_SLOTS);
        if (tally == null) {
            return;
        }
        inv.setContents(contents);
        player.sendMessage(plugin.msg("sold-all",
                "count", String.valueOf(tally.count()),
                "price", plugin.economy().format(tally.total())));
        updateTotal(inv);
    }

    /** /sat hepsi: envanterdeki satılabilen her şeyi satar. */
    public void sellAll(Player player) {
        ItemStack[] contents = player.getInventory().getStorageContents();
        Tally tally = settle(player, contents, 0, contents.length);
        if (tally == null) {
            return;
        }
        player.getInventory().setStorageContents(contents);
        player.sendMessage(plugin.msg("sold-all",
                "count", String.valueOf(tally.count()),
                "price", plugin.economy().format(tally.total())));
    }

    /** /sat el: sadece elindeki yığını satar. */
    public void sellHand(Player player) {
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (stackValue(hand) <= 0) {
            player.sendMessage(plugin.msg("not-sellable-hand"));
            return;
        }
        Material type = hand.getType();
        ItemStack[] one = new ItemStack[]{hand};
        Tally tally = settle(player, one, 0, 1);
        if (tally == null) {
            return;
        }
        player.getInventory().setItemInMainHand(new ItemStack(Material.AIR));
        plugin.sendItemMsg(player, "sold-hand", type,
                "amount", String.valueOf(tally.count()),
                "price", plugin.economy().format(tally.total()));
    }

    /**
     * Önce parayı yatırır, başarılıysa eşyaları diziden siler.
     * Satılacak bir şey yoksa ya da ödeme başarısızsa null döner (eşyalara dokunulmaz).
     */
    private Tally settle(Player player, ItemStack[] stacks, int from, int to) {
        Tally tally = tally(stacks, from, to, false);
        if (tally.count() == 0) {
            player.sendMessage(plugin.msg("nothing-to-sell"));
            return null;
        }
        EconomyResponse response = plugin.economy().depositPlayer(player, tally.total());
        if (!response.transactionSuccess()) {
            player.sendMessage(plugin.msg("transaction-failed"));
            return null;
        }
        tally(stacks, from, to, true);
        return tally;
    }

    private Tally tally(ItemStack[] stacks, int from, int to, boolean clear) {
        int count = 0;
        double total = 0;
        for (int i = from; i < to && i < stacks.length; i++) {
            ItemStack stack = stacks[i];
            double value = stackValue(stack);
            if (value <= 0) {
                continue;
            }
            count += stack.getAmount();
            total += value;
            if (clear) {
                stacks[i] = null;
            }
        }
        return new Tally(count, Math.round(total * 100.0) / 100.0);
    }

    /**
     * Yığının toplam satış değeri; satılamıyorsa -1.
     * Hasarlı eşya hasar oranında düşük, büyülü eşya her büyü seviyesi için ek para eder.
     * İçinde eşya olan shulker kutusu / bundle asla satılmaz (içindekiler kaybolmasın).
     */
    private double stackValue(ItemStack stack) {
        if (stack == null || stack.getType().isAir()) {
            return -1;
        }
        double base = plugin.shopManager().sellPrice(stack.getType());
        if (base <= 0) {
            return -1;
        }

        double unit = base;
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            if (holdsItems(meta)) {
                return -1;
            }

            int maxDurability = stack.getType().getMaxDurability();
            if (meta instanceof Damageable damageable && damageable.hasDamage() && maxDurability > 0) {
                double left = (maxDurability - damageable.getDamage()) / (double) maxDurability;
                double minFraction = plugin.getConfig().getDouble("sell.damaged-min-percent", 10) / 100.0;
                unit = base * Math.max(minFraction, Math.min(1.0, left));
            }

            int levels = 0;
            for (int level : meta.getEnchants().values()) {
                levels += level;
            }
            if (meta instanceof EnchantmentStorageMeta book) {
                for (int level : book.getStoredEnchants().values()) {
                    levels += level;
                }
            }
            unit += levels * plugin.getConfig().getDouble("sell.enchant-value-per-level", 10);
        }
        return unit * stack.getAmount();
    }

    private boolean holdsItems(ItemMeta meta) {
        if (meta instanceof BlockStateMeta blockMeta && blockMeta.hasBlockState()
                && blockMeta.getBlockState() instanceof Container container) {
            return !container.getInventory().isEmpty();
        }
        return meta instanceof BundleMeta bundle && bundle.hasItems();
    }

    // ------------------------------------------------------------------

    private Material fillerMaterial() {
        Material m = Material.matchMaterial(plugin.getConfig().getString(
                "settings.filler-material", "LIGHT_BLUE_STAINED_GLASS_PANE"));
        return m == null || m.isAir() ? Material.LIGHT_BLUE_STAINED_GLASS_PANE : m;
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
}

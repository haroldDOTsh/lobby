package sh.harold.fulcrum.lobby.feature;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import sh.harold.fulcrum.fundamentals.actionflag.ActionFlagService;
import sh.harold.fulcrum.lifecycle.ServiceLocatorImpl;
import sh.harold.fulcrum.lobby.system.LobbyFeature;
import sh.harold.fulcrum.lobby.system.LobbyFeatureContext;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Ensures all lobby players receive a consistent hotbar layout.
 */
public final class LobbyHotbarFeature implements LobbyFeature, Listener {
    private final Map<HotbarItem, ItemStack> prototypes = new EnumMap<>(HotbarItem.class);

    private JavaPlugin plugin;
    private NamespacedKey itemKey;
    private ActionFlagService actionFlagService;

    @Override
    public String id() {
        return "lobby-hotbar";
    }

    @Override
    public int priority() {
        return 70;
    }

    @Override
    public void initialize(LobbyFeatureContext context) {
        this.plugin = context.plugin();
        this.itemKey = new NamespacedKey(plugin, "lobby_hotbar_item");

        ServiceLocatorImpl locator = ServiceLocatorImpl.getInstance();
        if (locator != null) {
            actionFlagService = locator.findService(ActionFlagService.class).orElse(null);
        }

        PluginManager pluginManager = plugin.getServer().getPluginManager();
        pluginManager.registerEvents(this, plugin);

        Bukkit.getOnlinePlayers().forEach(this::applyLayout);
        context.logger().info("Lobby hotbar feature initialised.");
    }

    @Override
    public void shutdown(LobbyFeatureContext context) {
        HandlerList.unregisterAll(this);
        prototypes.clear();
        plugin = null;
        itemKey = null;
        actionFlagService = null;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        scheduleHotbarRefresh(event.getPlayer());
    }

    @EventHandler
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        scheduleHotbarRefresh(event.getPlayer());
    }

    @EventHandler
    public void onPlayerChangedWorld(PlayerChangedWorldEvent event) {
        scheduleHotbarRefresh(event.getPlayer());
    }

    @EventHandler(ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (event.getClickedInventory() instanceof PlayerInventory
                && HotbarItem.fromSlot(event.getSlot()) != null) {
            enforceInventoryLock(player, event);
            return;
        }
        int hotbarButton = event.getHotbarButton();
        if (hotbarButton >= 0 && HotbarItem.fromSlot(hotbarButton) != null) {
            enforceInventoryLock(player, event);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (!affectsManagedSlot(event)) {
            return;
        }
        enforceInventoryLock(player, event);
    }

    @EventHandler(ignoreCancelled = true)
    public void onItemDrop(PlayerDropItemEvent event) {
        if (!isManagedItem(event.getItemDrop().getItemStack())) {
            return;
        }
        enforceInventoryLock(event.getPlayer(), event);
    }

    @EventHandler(ignoreCancelled = true)
    public void onSwapHands(PlayerSwapHandItemsEvent event) {
        if (!isManagedItem(event.getMainHandItem()) && !isManagedItem(event.getOffHandItem())) {
            return;
        }
        enforceInventoryLock(event.getPlayer(), event);
    }

    private void enforceInventoryLock(Player player, Cancellable cancellable) {
        if (!shouldLockHotbar(player)) {
            return;
        }
        if (cancellable != null) {
            cancellable.setCancelled(true);
        }
        scheduleHotbarRefresh(player);
    }

    /**
     * Placeholder for an action-flag powered inventory lock. Once the relevant flag
     * is available we only need to update this method to begin enforcing it.
     */
    private boolean shouldLockHotbar(Player player) {
        if (player == null) {
            return false;
        }
        if (actionFlagService == null) {
            return false;
        }
        return false;
    }

    private void applyLayout(Player player) {
        if (player == null) {
            return;
        }
        PlayerInventory inventory = player.getInventory();
        for (HotbarItem item : HotbarItem.values()) {
            inventory.setItem(item.slot(), copyOf(item));
        }
        player.updateInventory();
    }

    private void scheduleHotbarRefresh(Player player) {
        if (player == null || plugin == null) {
            return;
        }
        Bukkit.getScheduler().runTask(plugin, () -> applyLayout(player));
    }

    private ItemStack copyOf(HotbarItem item) {
        ItemStack prototype = prototypes.computeIfAbsent(item, key -> key.createPrototype(itemKey));
        return prototype.clone();
    }

    private boolean isManagedItem(ItemStack stack) {
        if (stack == null || !stack.hasItemMeta() || itemKey == null) {
            return false;
        }
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return false;
        }
        PersistentDataContainer container = meta.getPersistentDataContainer();
        String identifier = container.get(itemKey, PersistentDataType.STRING);
        if (identifier == null) {
            return false;
        }
        return HotbarItem.fromDataId(identifier) != null;
    }

    private boolean affectsManagedSlot(InventoryDragEvent event) {
        InventoryView view = event.getView();
        int topSize = view.getTopInventory() != null ? view.getTopInventory().getSize() : 0;
        Set<Integer> rawSlots = event.getRawSlots();
        for (int rawSlot : rawSlots) {
            if (rawSlot < topSize) {
                continue;
            }
            int playerSlot = rawSlot - topSize;
            if (HotbarItem.fromSlot(playerSlot) != null) {
                return true;
            }
        }
        return false;
    }

    private enum HotbarItem {
        GAME_SELECTOR(
                "game-selector",
                0,
                Material.COMPASS,
                Component.text("Game Selector", NamedTextColor.AQUA).decoration(TextDecoration.BOLD, true),
                List.of(
                        Component.text("Browse every featured Fulcrum game.", NamedTextColor.GRAY),
                        Component.text("Right-click to open the selector.", NamedTextColor.YELLOW)
                )
        ),
        PLAYER_VISIBILITY(
                "player-visibility",
                7,
                Material.PRISMARINE_CRYSTALS,
                Component.text("Player Visibility", NamedTextColor.LIGHT_PURPLE).decoration(TextDecoration.BOLD, true),
                List.of(
                        Component.text("Toggle between showing or hiding players.", NamedTextColor.GRAY),
                        Component.text("Left-click to cycle modes.", NamedTextColor.YELLOW)
                )
        ),
        LOBBY_SELECTOR(
                "lobby-selector",
                8,
                Material.NETHER_STAR,
                Component.text("Lobby Selector", NamedTextColor.GOLD).decoration(TextDecoration.BOLD, true),
                List.of(
                        Component.text("Jump to another lobby shard.", NamedTextColor.GRAY),
                        Component.text("Right-click to pick your destination.", NamedTextColor.YELLOW)
                )
        );

        private final String dataId;
        private final int slot;
        private final Material material;
        private final Component displayName;
        private final List<Component> lore;

        HotbarItem(String dataId,
                   int slot,
                   Material material,
                   Component displayName,
                   List<Component> lore) {
            this.dataId = Objects.requireNonNull(dataId, "dataId");
            this.slot = slot;
            this.material = Objects.requireNonNull(material, "material");
            this.displayName = Objects.requireNonNull(displayName, "displayName");
            this.lore = List.copyOf(Objects.requireNonNull(lore, "lore"));
        }

        int slot() {
            return slot;
        }

        ItemStack createPrototype(NamespacedKey itemKey) {
            ItemStack stack = new ItemStack(material);
            ItemMeta meta = stack.getItemMeta();
            if (meta != null) {
                meta.displayName(displayName);
                meta.lore(lore);
                meta.addItemFlags(
                        ItemFlag.HIDE_ATTRIBUTES,
                        ItemFlag.HIDE_ENCHANTS,
                        ItemFlag.HIDE_ADDITIONAL_TOOLTIP
                );
                if (itemKey != null) {
                    meta.getPersistentDataContainer().set(itemKey, PersistentDataType.STRING, dataId);
                }
                stack.setItemMeta(meta);
            }
            stack.setAmount(1);
            return stack;
        }

        static HotbarItem fromSlot(int slot) {
            for (HotbarItem item : values()) {
                if (item.slot == slot) {
                    return item;
                }
            }
            return null;
        }

        static HotbarItem fromDataId(String dataId) {
            for (HotbarItem item : values()) {
                if (item.dataId.equalsIgnoreCase(dataId)) {
                    return item;
                }
            }
            return null;
        }
    }
}

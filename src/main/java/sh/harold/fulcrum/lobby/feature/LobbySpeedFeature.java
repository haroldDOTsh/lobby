package sh.harold.fulcrum.lobby.feature;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPotionEffectEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import sh.harold.fulcrum.lobby.system.LobbyFeature;
import sh.harold.fulcrum.lobby.system.LobbyFeatureContext;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Applies a default Speed I effect to all lobby players using an infinite potion duration.
 */
public final class LobbySpeedFeature implements LobbyFeature, Listener {
    private static final PotionEffect SPEED_EFFECT = new PotionEffect(
            PotionEffectType.SPEED,
            PotionEffect.INFINITE_DURATION,
            0,
            true,
            false,
            false
    );

    private final Set<UUID> managed = ConcurrentHashMap.newKeySet();
    private JavaPlugin plugin;
    private boolean shuttingDown;

    @Override
    public String id() {
        return "lobby-speed";
    }

    @Override
    public int priority() {
        return 85;
    }

    @Override
    public void initialize(LobbyFeatureContext context) {
        this.plugin = context.plugin();
        this.shuttingDown = false;

        PluginManager pluginManager = plugin.getServer().getPluginManager();
        pluginManager.registerEvents(this, plugin);

        plugin.getServer().getOnlinePlayers().forEach(this::applySpeedIfNeeded);

        context.logger().info("Lobby speed feature initialised.");
    }

    @Override
    public void shutdown(LobbyFeatureContext context) {
        if (plugin != null) {
            shuttingDown = true;
            plugin.getServer().getOnlinePlayers().forEach(this::removeManagedSpeed);
        }
        HandlerList.unregisterAll(this);
        managed.clear();
        plugin = null;
        shuttingDown = false;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        applySpeedIfNeeded(event.getPlayer());
    }

    @EventHandler
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        applySpeedIfNeeded(event.getPlayer());
    }

    @EventHandler
    public void onPlayerChangedWorld(PlayerChangedWorldEvent event) {
        applySpeedIfNeeded(event.getPlayer());
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        managed.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler(ignoreCancelled = true)
    public void onPotionEffectChange(EntityPotionEffectEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        if (event.getModifiedType() != PotionEffectType.SPEED) {
            return;
        }
        if (shuttingDown || plugin == null) {
            return;
        }

        PotionEffect newEffect = event.getNewEffect();
        if (newEffect != null && newEffect.getAmplifier() > SPEED_EFFECT.getAmplifier()) {
            managed.remove(player.getUniqueId());
            return;
        }

        Bukkit.getScheduler().runTask(plugin, () -> applySpeedIfNeeded(player));
    }

    private void applySpeedIfNeeded(Player player) {
        if (player == null) {
            return;
        }

        PotionEffect current = player.getPotionEffect(PotionEffectType.SPEED);
        if (current != null && current.getAmplifier() > SPEED_EFFECT.getAmplifier()) {
            managed.remove(player.getUniqueId());
            return;
        }

        boolean applied = player.addPotionEffect(SPEED_EFFECT, true);
        if (applied) {
            managed.add(player.getUniqueId());
        }
    }

    private void removeManagedSpeed(Player player) {
        if (player == null) {
            return;
        }
        if (managed.remove(player.getUniqueId())) {
            player.removePotionEffect(PotionEffectType.SPEED);
        }
    }
}

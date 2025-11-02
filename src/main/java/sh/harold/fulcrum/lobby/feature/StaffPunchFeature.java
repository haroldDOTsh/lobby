package sh.harold.fulcrum.lobby.feature;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;
import sh.harold.fulcrum.api.rank.Rank;
import sh.harold.fulcrum.api.rank.RankUtils;

import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Allows top donators to launch staff members into the sky with a left-click punch.
 */
public final class StaffPunchFeature implements LobbyFeature, Listener {
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();
    private static final double MIN_VERTICAL_VELOCITY = 1.35D;
    private static final double MAX_VERTICAL_VELOCITY = 1.75D;

    private JavaPlugin plugin;

    @Override
    public String id() {
        return "staff-punch";
    }

    @Override
    public int priority() {
        return 120;
    }

    @Override
    public void initialize(LobbyFeatureContext context) {
        this.plugin = context.plugin();
        PluginManager pluginManager = plugin.getServer().getPluginManager();
        pluginManager.registerEvents(this, plugin);
        context.logger().info("Staff punch feature initialised.");
    }

    @Override
    public void shutdown(LobbyFeatureContext context) {
        HandlerList.unregisterAll(this);
        this.plugin = null;
    }

    @EventHandler(ignoreCancelled = true)
    public void onStaffPunch(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player attacker)) {
            return;
        }
        if (!(event.getEntity() instanceof Player victim)) {
            return;
        }
        DamageCause cause = event.getCause();
        if (cause != DamageCause.ENTITY_ATTACK && cause != DamageCause.ENTITY_SWEEP_ATTACK) {
            return;
        }

        Rank attackerRank = Optional.ofNullable(RankUtils.getEffectiveRank(attacker)).orElse(Rank.DEFAULT);
        if (attackerRank != Rank.DONATOR_4) {
            return;
        }

        if (!RankUtils.isStaff(victim)) {
            return;
        }

        launchVictim(attacker, victim, attackerRank);
        event.setCancelled(true);
    }

    private void launchVictim(Player attacker, Player victim, Rank attackerRank) {
        double verticalVelocity = ThreadLocalRandom.current().nextDouble(MIN_VERTICAL_VELOCITY, MAX_VERTICAL_VELOCITY);
        Vector launchVector = new Vector(0, verticalVelocity, 0);
        victim.setVelocity(launchVector);
        victim.setFallDistance(0);

        Rank victimRank = Optional.ofNullable(RankUtils.getEffectiveRank(victim)).orElse(Rank.DEFAULT);
        Component message = Component.empty()
                .append(formatPlayer(attacker, attackerRank))
                .append(Component.text(" punched ", NamedTextColor.GRAY))
                .append(formatPlayer(victim, victimRank))
                .append(Component.text(" into the sky!", NamedTextColor.GRAY));

        if (plugin != null) {
            plugin.getServer().sendMessage(message);
        } else {
            Bukkit.getServer().sendMessage(message);
        }
    }

    private Component formatPlayer(Player player, Rank rank) {
        String fullPrefix = rank.getFullPrefix();
        Component prefix = Component.empty();
        if (fullPrefix != null && !fullPrefix.isBlank()) {
            prefix = LEGACY.deserialize(fullPrefix).append(Component.space());
        }
        NamedTextColor nameColor = Optional.ofNullable(rank.getNameColor()).orElse(NamedTextColor.WHITE);
        return prefix.append(Component.text(player.getName(), nameColor));
    }
}

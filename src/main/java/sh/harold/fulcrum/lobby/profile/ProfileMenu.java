package sh.harold.fulcrum.lobby.profile;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.Statistic;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import sh.harold.fulcrum.api.menu.MenuService;
import sh.harold.fulcrum.api.menu.TabbedMenuBuilder;
import sh.harold.fulcrum.api.menu.component.MenuButton;
import sh.harold.fulcrum.api.menu.component.MenuDisplayItem;
import sh.harold.fulcrum.api.menu.component.MenuItem;

import java.util.List;
import java.util.Objects;
import java.util.logging.Level;

/**
 * Builds the tabbed profile menu that hangs off the lobby hotbar profile item.
 */
public record ProfileMenu(MenuService menuService, JavaPlugin plugin) {
    private static final Component MENU_TITLE = Component.text("Profile", NamedTextColor.AQUA)
            .decoration(TextDecoration.ITALIC, false);
    private static final Component MENU_ERROR = Component.text("Unable to open the profile menu right now.",
            NamedTextColor.RED).decoration(TextDecoration.ITALIC, false);

    public ProfileMenu {
        Objects.requireNonNull(menuService, "menuService");
        Objects.requireNonNull(plugin, "plugin");
    }

    public void open(Player player) {
        if (player == null) {
            return;
        }
        menuService.createTabbedMenu()
                .owner(plugin)
                .title(MENU_TITLE)
                .contentRows(3)
                .divider(Material.GRAY_STAINED_GLASS_PANE)
                .defaultTab("my-profile")
                .tab(tab -> configureMyProfileTab(tab, player))
                .tab(tab -> configureFriendsTab(tab))
                .tab(tab -> configurePartyTab(tab))
                .buildAsync(player)
                .exceptionally(throwable -> {
                    plugin.getLogger().log(Level.WARNING, "Failed to open profile menu for " + player.getName(), throwable);
                    player.sendMessage(MENU_ERROR);
                    return null;
                });
    }

    private void configureMyProfileTab(TabbedMenuBuilder.TabBuilder tab, Player player) {
        tab.id("my-profile")
                .name("&aMy Profile")
                .icon(Material.PLAYER_HEAD)
                .lore("&7Stats, rewards, and settings.")
                .items(buildMyProfileItems(player));
    }

    private void configureFriendsTab(TabbedMenuBuilder.TabBuilder tab) {
        tab.id("friends")
                .name("&bFriends")
                .icon(Material.PLAYER_HEAD)
                .lore("&7Manage friends and requests.")
                .divider(Material.LIGHT_BLUE_STAINED_GLASS_PANE)
                .items(buildFriendItems());
    }

    private void configurePartyTab(TabbedMenuBuilder.TabBuilder tab) {
        tab.id("party")
                .name("&dParty")
                .icon(Material.NETHER_STAR)
                .lore("&7Create and manage parties.")
                .divider(Material.PURPLE_STAINED_GLASS_PANE)
                .items(buildPartyItems());
    }

    private List<MenuItem> buildMyProfileItems(Player player) {
        return List.of(
                profileOverviewItem(player),
                actionButton(Material.KNOWLEDGE_BOOK,
                        "&aStats Tracker",
                        "&7Review wins, experience, and unlock milestones in one place.",
                        "The stats tracker"),
                actionButton(Material.ENDER_CHEST,
                        "&eRewards & Boosts",
                        "&7Claim daily gifts, streak rewards, and activate global boosters.",
                        "Rewards and boosts"),
                actionButton(Material.COMPARATOR,
                        "&bAccount Settings",
                        "&7Update privacy, chat preferences, and cosmetics visibility.",
                        "Account settings")
        );
    }

    private List<MenuItem> buildFriendItems() {
        return List.of(
                actionButton(Material.PLAYER_HEAD,
                        "&aFriend List",
                        "&7See who is online, join them instantly, or view the server they're on.",
                        "Friend management"),
                actionButton(Material.WRITABLE_BOOK,
                        "&eFriend Requests",
                        "&7Approve or deny pending invites without leaving the lobby.",
                        "Friend requests"),
                socialStatusDisplay(),
                actionButton(Material.FILLED_MAP,
                        "&bRecent Players",
                        "&7Quickly re-connect with players you recently queued or chatted with.",
                        "Recent players")
        );
    }

    private List<MenuItem> buildPartyItems() {
        return List.of(
                actionButton(Material.NETHER_STAR,
                        "&aCreate Party",
                        "&7Start a brand new party and pick the mode before you queue.",
                        "Party creation"),
                actionButton(Material.NAME_TAG,
                        "&eInvite Players",
                        "&7Send invites directly from your friends list or by typing their name.",
                        "Party invites"),
                actionButton(Material.ENDER_EYE,
                        "&bWarp Party",
                        "&7Pull everyone with you whenever you join a new lobby or queue.",
                        "Party warp"),
                actionButton(Material.REDSTONE_TORCH,
                        "&dParty Settings",
                        "&7Promote members, toggle permissions, and manage chat focus.",
                        "Party settings")
        );
    }

    private MenuItem profileOverviewItem(Player player) {
        long ticksPlayed = player.getStatistic(Statistic.PLAY_ONE_MINUTE);
        long minutesPlayed = ticksPlayed / (20L * 60L);
        String playtime = minutesPlayed >= 60
                ? (minutesPlayed / 60) + "h " + (minutesPlayed % 60) + "m"
                : minutesPlayed + "m";
        return MenuDisplayItem.builder(Material.PLAYER_HEAD)
                .name("&a" + player.getName())
                .secondary("&7Lobby Level: " + player.getLevel())
                .description("&7Your Fulcrum identity, highlights, and seasonal progress live here.")
                .lore("&7Time Played: " + playtime, "&7XP: " + player.getTotalExperience())
                .build();
    }

    private MenuItem socialStatusDisplay() {
        return MenuDisplayItem.builder(Material.BELL)
                .name("&fSocial Status")
                .secondary("&7Never miss an invite or alert.")
                .description("&7Peek at social alerts, unread messages, and in-progress chats.")
                .lore("&7Notifications pause automatically while you play minigames.")
                .build();
    }

    private MenuButton actionButton(Material material, String name, String description, String featureName) {
        return MenuButton.builder(material)
                .name(name)
                .description(description)
                .onClick(player -> player.sendMessage(stubMessage(featureName)))
                .build();
    }

    private static Component stubMessage(String featureName) {
        return Component.text(featureName + " is coming soon.", NamedTextColor.YELLOW)
                .decoration(TextDecoration.ITALIC, false);
    }
}

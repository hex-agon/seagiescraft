package work.fking.corpa.invsnaps;

import com.google.common.base.Strings;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.JoinConfiguration;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.time.Duration;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class InventorySnapshots extends JavaPlugin implements CommandExecutor, Listener, TabCompleter {

    private static final TextColor PRIMARY_TEXT_COLOR = TextColor.color(0xFF9800);
    private static final TextColor SECONDARY_TEXT_COLOR = TextColor.color(0xBDBDBD);
    private static final TextColor TERTIARY_TEXT_COLOR = TextColor.color(0x26C6DA);

    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("y-M-d H:m:s");

    private SnapshotRepository repository;

    @Override
    public void onEnable() {
        getServer().getPluginManager().registerEvents(this, this);
        getCommand("inventorysnapshots").setTabCompleter(this); // safe because we're the ones registering this command
        try {
            var directory = getDataFolder().toPath();
            Files.createDirectories(directory);
            this.repository = SnapshotRepository.create(directory);
        } catch (IOException e) {
            throw new RuntimeException("Failed to start InventorySnapshots", e);
        }
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        var player = event.getPlayer();
        repository.save(Snapshot.from(player, SnapshotReason.PLAYER_DEATH));
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        var argPos = args.length;

        if (argPos == 1) {
            return List.of("lookup");
        } else if (argPos == 2) {
            var subcommand = args[0];

            if ("lookup".equals(subcommand)) {
                return suggestOnlinePlayers();
            }
        }
        return List.of();
    }

    private List<String> suggestOnlinePlayers() {
        return getServer().getOnlinePlayers()
                          .stream()
                          .map(Player::getName)
                          .collect(Collectors.toList());
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {

        if (args.length <= 1) {
            sender.sendMessage("Insufficient amount of arguments.");
            return true;
        }
        var subCommand = args[0];

        switch (subCommand) {
            case "restore" -> handleRestore(sender, Arrays.copyOfRange(args, 1, args.length));
            case "lookup" -> handleLookup(sender, Arrays.copyOfRange(args, 1, args.length));
            default -> {
                return false;
            }
        }
        return true;
    }

    private void handleRestore(CommandSender sender, String[] args) {
        var snapshotId = Integer.parseInt(args[0]);
        var snapshot = repository.findById(snapshotId);

        if (snapshot == null) {
            sender.sendMessage("Could not find the requested player snapshot.");
            return;
        }
        var player = getServer().getPlayer(snapshot.playerUuid());

        if (player == null) {
            sender.sendMessage("The player is not online, cannot restore snapshot.");
            return;
        }
        // snapshot the player's current state, just in case...
        repository.save(Snapshot.from(player, SnapshotReason.RESTORATION));
        var itemStacks = snapshot.itemStacks();
        player.getInventory().setContents(itemStacks);
        sender.sendMessage(Component.text("Successfully restored the player's inventory to the snapshot.").color(PRIMARY_TEXT_COLOR));
        player.sendMessage(Component.text("Your inventory has been restored to a previous state.").color(SECONDARY_TEXT_COLOR));
    }

    private void handleLookup(CommandSender sender, String[] args) {
        if (args.length < 1) {
            sender.sendMessage("Insufficient amount of arguments.");
            return;
        }
        var subject = args[0];

        try {
            // if the subject is an integer, we're looking up at a specific snapshot
            var snapshotId = Integer.parseInt(subject);
            var snapshot = repository.findById(snapshotId);

            if (snapshot == null) {
                sender.sendMessage("Could not find the requested player snapshot.");
                return;
            }
            sender.sendMessage(buildSnapshotDetails(snapshot));
        } catch (NumberFormatException e) {
            handlePlayerLookup(sender, subject);
        }
    }

    private void handlePlayerLookup(CommandSender sender, String subject) {
        var player = getServer().getPlayer(subject);

        if (player == null) {
            sender.sendMessage("Unknown player.");
            return;
        }
        var list = repository.findForList(player.getUniqueId());

        if (list.isEmpty()) {
            sender.sendMessage("This player doesn't have any snapshots.");
            return;
        }
        sender.sendMessage(buildSnapshotList(list));
    }

    private Component buildSnapshotList(List<Snapshot> snapshots) {
        List<Component> lines = new ArrayList<>();
        lines.add(Component.text("The following snapshots were found:").color(PRIMARY_TEXT_COLOR));

        for (Snapshot snapshot : snapshots) {
            var event = ClickEvent.runCommand("/is lookup " + snapshot.id());
            var timeAgo = formatDuration(snapshot.timeSince()) + " ago";
            lines.add(
                    Component.text()
                             .content("[" + DATE_TIME_FORMATTER.format(snapshot.createdAt()) + "]")
                             .color(TERTIARY_TEXT_COLOR)
                             .hoverEvent(HoverEvent.showText(Component.text(timeAgo)))
                             .clickEvent(event)
                             .append(Component.space(), Component.text("(" + snapshot.reason().fancyReason() + ")").color(SECONDARY_TEXT_COLOR))
                             .build()

            );
        }
        return Component.join(JoinConfiguration.separator(Component.newline()), lines);
    }

    private Component buildSnapshotDetails(Snapshot snapshot) {
        var offlinePlayer = getServer().getOfflinePlayer(snapshot.playerUuid());

        return Component.empty()
                        .append(Component.text(Strings.repeat("+", 53)).color(PRIMARY_TEXT_COLOR))
                        .append(Component.newline())
                        .append(buildLine("Player:", offlinePlayer.getName()))
                        .append(buildLine("Snapshot time:", DATE_TIME_FORMATTER.format(snapshot.createdAt())))
                        .append(buildItemList(snapshot.itemStacks()))
                        .append(buildConfirmation(snapshot));
    }

    private Component buildLine(String left, String right) {
        return Component.empty()
                        .append(Component.text(left).color(PRIMARY_TEXT_COLOR))
                        .append(Component.space())
                        .append(Component.text(right).color(SECONDARY_TEXT_COLOR))
                        .append(Component.newline());
    }

    private Component buildConfirmation(Snapshot snapshot) {
        var event = ClickEvent.runCommand("/is restore " + snapshot.id() + " confirm");
        return Component.text("Restore player snapshot?")
                        .append(Component.space())
                        .append(
                                Component.text()
                                         .content("[Yes]")
                                         .color(TERTIARY_TEXT_COLOR)
                                         .clickEvent(event)
                        );
    }

    private Component buildItemList(ItemStack[] itemStacks) {
        var items = new ArrayList<Component>();

        for (ItemStack itemStack : itemStacks) {

            if (itemStack == null) {
                continue;
            }
            items.add(
                    Component.join(JoinConfiguration.noSeparators(),
                            Component.text(itemStack.getAmount() + "x ").color(SECONDARY_TEXT_COLOR),
                            itemStack.displayName().hoverEvent(itemStack.asHoverEvent())
                    )
            );
        }
        return Component.empty()
                        .append(Component.text("Items:").color(PRIMARY_TEXT_COLOR))
                        .append(Component.space())
                        .append(Component.join(JoinConfiguration.separator(Component.space()), items))
                        .append(Component.newline());
    }

    private String formatDuration(Duration duration) {
        StringBuilder builder = new StringBuilder();
        long seconds = duration.getSeconds();
        long minutes = ((seconds % 3600) / 60);
        long hours = seconds / 3600;

        if (hours > 0) {
            builder.append(hours).append(" hour");

            if (hours > 1) {
                builder.append('s');
            }
            builder.append(' ');
        }
        if (minutes > 0) {
            builder.append(minutes).append(" minute");

            if (minutes > 1) {
                builder.append('s');
            }
            builder.append(' ');
        }
        seconds %= 60;
        builder.append(seconds).append(" second");

        if (seconds > 1) {
            builder.append('s');
        }
        return builder.toString();
    }
}

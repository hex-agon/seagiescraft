package work.fking.corpa.invsnaps;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.UUID;

public record Snapshot(
        int id,
        UUID playerUuid,
        SnapshotReason reason,
        byte[] inventory,
        LocalDateTime createdAt
) {

    public static Snapshot from(Player player, SnapshotReason reason) {
        var outputStream = new ByteArrayOutputStream();
        ItemStackSerializer.serialize(outputStream, player.getInventory().getContents());

        return new Snapshot(-1, player.getUniqueId(), reason, outputStream.toByteArray(), LocalDateTime.now());
    }

    public ItemStack[] itemStacks() {
        return ItemStackSerializer.deserialize(new ByteArrayInputStream(inventory));
    }

    public Duration timeSince() {
        return Duration.between(createdAt, LocalDateTime.now());
    }
}

package work.fking.corpa.invsnaps;

import org.bukkit.inventory.ItemStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public final class ItemStackSerializer {

    private static final Logger LOGGER = LoggerFactory.getLogger(ItemStackSerializer.class);
    private static final int VERSION = 1;

    private ItemStackSerializer() {
    }

    public static void serialize(OutputStream outputStream, ItemStack[] contents) {
        DataOutputStream stream = new DataOutputStream(outputStream);
        var validStacks = 0;

        for (ItemStack content : contents) {
            if (content != null) {
                validStacks++;
            }
        }
        try {
            stream.writeByte(VERSION);
            stream.writeByte(contents.length);
            stream.writeByte(validStacks);

            for (int slot = 0; slot < contents.length; slot++) {
                ItemStack item = contents[slot];

                if (item == null) {
                    continue;
                }
                var bytes = item.serializeAsBytes();
                stream.writeByte(slot);
                stream.writeShort(bytes.length);
                stream.write(bytes);
                validStacks++;
            }
            stream.writeShort(validStacks);
        } catch (IOException e) {
            LOGGER.warn("Failed to serialize", e);
        }
    }

    public static ItemStack[] deserialize(InputStream inputStream) {
        var stream = new DataInputStream(inputStream);
        int version = 0;
        try {
            version = stream.readUnsignedByte();

            if (version != VERSION) {
                throw new IOException("Unsupported serialized inventory version " + version);
            }
            var invSize = stream.readUnsignedByte();
            var validStacks = stream.readUnsignedByte();
            var stacks = new ItemStack[invSize];

            for (int i = 0; i < validStacks; i++) {
                var slot = stream.readUnsignedByte();
                var bytes = stream.readNBytes(stream.readUnsignedShort());
                var itemStack = ItemStack.deserializeBytes(bytes);
                stacks[slot] = itemStack;
            }
            return stacks;
        } catch (IOException e) {
            throw new RuntimeException("Failed to deserialize inventory", e);
        }
    }
}

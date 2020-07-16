package work.fking.paper.novoxel;

import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.messaging.PluginMessageListener;

import javax.annotation.Nonnull;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class NoVoxelMap extends JavaPlugin implements Listener, PluginMessageListener {

    private static final String PLUGIN_CHANNEL_BRAND = "minecraft:brand";
    private static final String CLIENT_BRAND_VANILLA = "vanilla";

    private static final String NO_RADAR = "§3 §6 §3 §6 §3 §6 §e ";
    private static final String NO_CAVE_MAPPING = "§3 §6 §3 §6 §3 §6 §d ";

    private final Map<Player, String> clientBrands = new HashMap<>();

    @Override
    public void onEnable() {
        Server server = getServer();
        server.getPluginManager().registerEvents(this, this);
        server.getMessenger().registerIncomingPluginChannel(this, PLUGIN_CHANNEL_BRAND, this);
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        String clientBrand = clientBrands.get(player);

        if (!Objects.equals(clientBrand, CLIENT_BRAND_VANILLA)) {
            getSLF4JLogger().info("Player {} is not using a vanilla client, attempting to disable voxelmap", player.getName());
            player.sendMessage(NO_RADAR + NO_CAVE_MAPPING);
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        clientBrands.remove(event.getPlayer());
    }

    @Override
    public void onPluginMessageReceived(@Nonnull String channel, @Nonnull Player player, @Nonnull byte[] message) {

        if (!Objects.equals(channel, PLUGIN_CHANNEL_BRAND)) {
            return;
        }
        String brand = readMCString(ByteBuffer.wrap(message));
        getSLF4JLogger().debug("Player {} logged in with client brand {}", player.getName(), brand);

        if (brand != null) {
            clientBrands.put(player, brand);
        }
    }

    private String readMCString(ByteBuffer buffer) {
        int size = readVarInt(buffer);

        if (size > 0x7FFF) {
            return null;
        }
        byte[] stringBuffer = new byte[size];
        buffer.get(stringBuffer);
        return new String(stringBuffer, StandardCharsets.UTF_8);
    }

    private int readVarInt(ByteBuffer buffer) {
        int numRead = 0;
        int result = 0;
        byte read;

        do {
            read = buffer.get();
            int value = (read & 0x7F);
            result |= (value << (7 * numRead));

            numRead++;
            if (numRead > 5) {
                throw new RuntimeException("VarInt is too big");
            }
        } while ((read & 0x80) != 0);

        return result;
    }
}

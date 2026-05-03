package com.monkey.kt.utils.listener;

import com.monkey.kt.KT;
import com.monkey.kt.storage.EffectVisibilityStorage;
import io.netty.channel.Channel;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.UUID;

public class PacketParticleListener implements Listener {

    private static final String HANDLER_NAME = "kt_particle_filter";

    private final KT plugin;

    public PacketParticleListener(KT plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        injectPlayer(event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        removePlayer(event.getPlayer());
    }

    public void injectOnlinePlayers() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            injectPlayer(player);
        }
    }

    private void injectPlayer(Player player) {
        Channel channel = getChannel(player);
        if (channel == null) return;
        if (channel.pipeline().get(HANDLER_NAME) != null) return;

        UUID playerUuid = player.getUniqueId();

        channel.pipeline().addBefore("packet_handler", HANDLER_NAME, new ChannelDuplexHandler() {
            @Override
            public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
                if (shouldSuppressPacket(playerUuid, msg)) return;
                super.write(ctx, msg, promise);
            }
        });
    }

    private boolean shouldSuppressPacket(UUID viewerUuid, Object packet) {
        if (EffectVisibilityStorage.isEnabled(viewerUuid)) return false;

        UUID killer = EffectVisibilityStorage.getCurrentKiller();
        if (killer != null && killer.equals(viewerUuid)) return false;

        String className = packet.getClass().getSimpleName();
        return className.equals("ClientboundLevelParticlesPacket")
                || className.equals("ClientboundExplodePacket");
    }

    private void removePlayer(Player player) {
        Channel channel = getChannel(player);
        if (channel == null) return;
        if (channel.pipeline().get(HANDLER_NAME) == null) return;
        channel.pipeline().remove(HANDLER_NAME);
    }

    private Channel getChannel(Player player) {
        try {
            Object craftPlayer = player.getClass().getMethod("getHandle").invoke(player);

            Object connection = null;
            for (String fieldName : new String[]{"connection", "playerConnection", "networkManager"}) {
                try {
                    Field f = findField(craftPlayer.getClass(), fieldName);
                    if (f != null) {
                        f.setAccessible(true);
                        connection = f.get(craftPlayer);
                        break;
                    }
                } catch (Exception ignored) {}
            }
            if (connection == null) return null;

            Object networkManager = null;
            for (String fieldName : new String[]{"connection", "networkManager", "network", "channel"}) {
                try {
                    Field f = findField(connection.getClass(), fieldName);
                    if (f == null) continue;
                    f.setAccessible(true);
                    Object val = f.get(connection);
                    if (val instanceof Channel) return (Channel) val;
                    networkManager = val;
                    break;
                } catch (Exception ignored) {}
            }

            if (networkManager == null) return null;

            for (String fieldName : new String[]{"channel", "k", "m"}) {
                try {
                    Field f = findField(networkManager.getClass(), fieldName);
                    if (f == null) continue;
                    f.setAccessible(true);
                    Object val = f.get(networkManager);
                    if (val instanceof Channel) return (Channel) val;
                } catch (Exception ignored) {}
            }

            return null;
        } catch (Exception e) {
            plugin.getLogger().warning("[KT] Could not inject packet filter for " + player.getName() + ": " + e.getMessage());
            return null;
        }
    }

    private Field findField(Class<?> clazz, String name) {
        Class<?> current = clazz;
        while (current != null && current != Object.class) {
            try {
                return current.getDeclaredField(name);
            } catch (NoSuchFieldException ignored) {}
            current = current.getSuperclass();
        }
        return null;
    }
}

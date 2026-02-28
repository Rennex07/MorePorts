package com.epixdevelopment.moreports;

import io.netty.channel.ChannelFuture;
import org.bukkit.Bukkit;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class PortManager {

    private final MorePorts plugin;
    private final List<ChannelFuture> activeChannels = new ArrayList<>();
    
    private Object minecraftServer;
    private Object serverConnection;
    private Method bindMethod;

    public PortManager(MorePorts plugin) {
        this.plugin = plugin;
        try {
            setupReflection();
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to setup reflection for PortManager: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void setupReflection() throws Exception {
        Object craftServer = Bukkit.getServer();
        Method getServerMethod = craftServer.getClass().getMethod("getServer");
        minecraftServer = getServerMethod.invoke(craftServer);

        // Find ServerConnection
        
        Method getConnectionMethod = null;
        for (Method m : minecraftServer.getClass().getMethods()) {
            String returnType = m.getReturnType().getSimpleName();
            if (returnType.equals("ServerConnection") || returnType.equals("ServerConnectionListener")) {
                getConnectionMethod = m;
                break;
            }
        }
        
        if (getConnectionMethod == null) {
             // Fallback: look for a field
             for (Field f : minecraftServer.getClass().getDeclaredFields()) {
                String typeName = f.getType().getSimpleName();
                if (typeName.equals("ServerConnection") || typeName.equals("ServerConnectionListener")) {
                    f.setAccessible(true);
                    serverConnection = f.get(minecraftServer);
                    break;
                }
            }
        } else {
            serverConnection = getConnectionMethod.invoke(minecraftServer);
        }

        // Deep search: If still null, look for a field that has a 'bind' method taking InetAddress and int
        if (serverConnection == null) {
            plugin.getLogger().warning("Standard ServerConnection search failed. Attempting deep search...");
            for (Field f : minecraftServer.getClass().getDeclaredFields()) {
                f.setAccessible(true);
                Object candidate = f.get(minecraftServer);
                if (candidate == null) continue;
                
                // Check if candidate has a bind(InetAddress, int) method
                boolean hasBind = false;
                try {
                    for (Method m : candidate.getClass().getDeclaredMethods()) {
                         if (m.getParameterCount() == 2 && 
                             m.getParameterTypes()[0] == InetAddress.class && 
                             m.getParameterTypes()[1] == int.class) {
                             hasBind = true;
                             break;
                         }
                    }
                } catch (Exception ignored) {}
                
                if (hasBind) {
                    serverConnection = candidate;
                    plugin.getLogger().info("Found ServerConnection candidate via deep search: " + f.getName() + " (" + f.getType().getSimpleName() + ")");
                    break;
                }
            }
        }

        if (serverConnection == null) {
            throw new RuntimeException("Could not find ServerConnection instance");
        }
        
        // Find bind method: (InetAddress, int)
        for (Method m : serverConnection.getClass().getDeclaredMethods()) {
             if (m.getParameterCount() == 2 && 
                 m.getParameterTypes()[0] == InetAddress.class && 
                 m.getParameterTypes()[1] == int.class) {
                 bindMethod = m;
                 bindMethod.setAccessible(true);
                 break;
             }
        }
        
        if (bindMethod == null) {
            throw new RuntimeException("Could not find bind method in ServerConnection");
        }
    }

    public void bindPort(int port) throws Exception {
        if (serverConnection == null || bindMethod == null) {
            throw new IllegalStateException("PortManager not initialized properly");
        }

        // We capture the list of channels BEFORE binding to see which one was added
        List<ChannelFuture> channelsBefore = getChannels();
        
        bindMethod.invoke(serverConnection, null, port); // null usually binds to wildcard or server setting
        
        // Capture list AFTER to find the new one and track it
        List<ChannelFuture> channelsAfter = getChannels();
        
        for (ChannelFuture cf : channelsAfter) {
            boolean found = false;
            for (ChannelFuture old : channelsBefore) {
                if (old == cf) {
                    found = true;
                    break;
                }
            }
            if (!found) {
                activeChannels.add(cf);
                break;
            }
        }
    }

    @SuppressWarnings("unchecked")
    private List<ChannelFuture> getChannels() {
        try {
            for (Field f : serverConnection.getClass().getDeclaredFields()) {
                 if (List.class.isAssignableFrom(f.getType())) {
                     f.setAccessible(true);
                     List<?> list = (List<?>) f.get(serverConnection);
                     if (list != null) {
                         // Empty? What in god's green earth went wrong?
                         if (!list.isEmpty() && list.get(0) instanceof ChannelFuture) {
                             return new ArrayList<>((List<ChannelFuture>) list);
                         }
                         // If it's empty, we might be early? Or it's the wrong list.
                         // But 'channels' is usually the only List field in ServerConnection.
                         // Let's assume the first List field is it if we can't check content type.
                     }
                 }
            }
            // Second pass: if we didn't find a populated list, just take the first List field?
            // Safer to just return empty and hope we find it populated later?
            // No, we need it to compare.
        } catch (Exception e) {
            e.printStackTrace();
        }
        return Collections.emptyList();
    }

    public void unbindAll() {
        for (ChannelFuture cf : activeChannels) {
            try {
                cf.channel().close().sync();
                plugin.getLogger().info("Closed channel for extra port");
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to close channel: " + e.getMessage());
            }
        }
        activeChannels.clear();
    }
    
    public List<Integer> getActivePorts() {
        // Placeholder
        return new ArrayList<>(); 
    }
}

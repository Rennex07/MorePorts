package com.epixdevelopment.moreports;

import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.logging.Level;

public class MorePorts extends JavaPlugin {

    private PortManager portManager;
    private BedrockPortManager bedrockPortManager;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        
        portManager = new PortManager(this);
        bedrockPortManager = new BedrockPortManager(this);
        
        getCommand("moreports").setExecutor(new MorePortsCommand(this));
        
        loadPorts();
        bedrockPortManager.start();
    }

    public void reloadPorts() {
        if (portManager != null) {
            portManager.unbindAll();
            loadPorts();
        }
        if (bedrockPortManager != null) {
            bedrockPortManager.shutdown();
            bedrockPortManager.start();
        }
    }

    private void loadPorts() {
        List<Integer> ports = getConfig().getIntegerList("ports");
        if (ports.isEmpty()) {
            getLogger().info("No extra Java ports configured.");
        } else {
            for (int port : ports) {
                try {
                    portManager.bindPort(port);
                    getLogger().info("Bound to Java port " + port);
                } catch (Exception e) {
                    getLogger().log(Level.SEVERE, "Failed to bind to Java port " + port, e);
                }
            }
        }
    }

    @Override
    public void onDisable() {
        if (portManager != null) {
            portManager.unbindAll();
        }
        if (bedrockPortManager != null) {
            bedrockPortManager.shutdown();
        }
    }
}

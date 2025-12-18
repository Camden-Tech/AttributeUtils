package me.baddcamden.attributeutils;

import me.baddcamden.attributeutils.attributes.AttributeManager;
import me.baddcamden.attributeutils.commands.AttributesCommand;
import me.baddcamden.attributeutils.commands.GlobalCommand;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;

public class AttributeUtilitiesPlugin extends JavaPlugin implements Listener {
    private AttributeManager attributeManager;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        ensureDataFiles();

        attributeManager = new AttributeManager(this);
        attributeManager.load();
        attributeManager.applyDefaultsToOnlinePlayers();

        getServer().getPluginManager().registerEvents(this, this);

        registerCommands();
        getLogger().info("AttributeUtils enabled.");
    }

    @Override
    public void onDisable() {
        if (attributeManager != null) {
            attributeManager.saveGlobalData();
        }
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        attributeManager.applyDefaultsToPlayer(event.getPlayer());
    }

    public void reloadPluginConfig() {
        reloadConfig();
        attributeManager.reload();
        attributeManager.applyDefaultsToOnlinePlayers();
    }

    public AttributeManager getAttributeManager() {
        return attributeManager;
    }

    private void ensureDataFiles() {
        File dataFolder = getDataFolder();
        if (!dataFolder.exists() && !dataFolder.mkdirs()) {
            getLogger().warning("Could not create plugin data folder.");
        }

        File dataDir = new File(dataFolder, "data");
        if (!dataDir.exists() && !dataDir.mkdirs()) {
            getLogger().warning("Could not create data directory.");
        }

        File global = new File(dataDir, "global.yml");
        if (!global.exists()) {
            saveResource("data/global.yml", false);
        }
    }

    private void registerCommands() {
        FileConfiguration config = getConfig();

        if (getCommand("attributes") != null) {
            getCommand("attributes").setExecutor(new AttributesCommand(this, config));
        }

        if (getCommand("attributeglobals") != null) {
            getCommand("attributeglobals").setExecutor(new GlobalCommand(this, config));
        }
    }
}

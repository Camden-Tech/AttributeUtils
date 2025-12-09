package me.BaddCamden.AttributeUtils;

import me.BaddCamden.AttributeUtils.config.AttributeConfig;
import me.BaddCamden.AttributeUtils.storage.PlayerAttributeData;
import me.BaddCamden.AttributeUtils.storage.PlayerStorageService;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class AttributeUtilities extends JavaPlugin implements Listener {

    private AttributeConfig attributeConfig;
    private AttributeDefaultsCache defaultsCache;
    private PlayerStorageService storageService;
    private final Map<UUID, PlayerAttributeData> playerCache = new HashMap<>();

    @Override
    public void onEnable() {
        saveDefaultConfig();
        reloadAttributeConfig();
        defaultsCache = new AttributeDefaultsCache();
        storageService = new PlayerStorageService(this, attributeConfig, defaultsCache, playerCache);
        getServer().getPluginManager().registerEvents(this, this);

        // Hydrate any players that were already online during a reload.
        for (Player online : Bukkit.getOnlinePlayers()) {
            loadPlayer(online);
        }
    }

    @Override
    public void onDisable() {
        for (Player online : Bukkit.getOnlinePlayers()) {
            storageService.flushToDisk(online);
        }
        playerCache.clear();
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        loadPlayer(event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        storageService.flushToDisk(event.getPlayer());
        playerCache.remove(event.getPlayer().getUniqueId());
    }

    private void loadPlayer(Player player) {
        PlayerAttributeData data = storageService.loadPlayerData(player.getUniqueId());
        storageService.applyToPlayer(player, data);
    }

    private void reloadAttributeConfig() {
        saveDefaultConfig();
        attributeConfig = new AttributeConfig(getConfig());
    }
}

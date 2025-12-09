package me.baddcamden.attributeutils;

import me.baddcamden.attributeutils.command.AttributeCommand;
import me.baddcamden.attributeutils.handler.entity.EntityAttributeHandler;
import me.baddcamden.attributeutils.handler.item.ItemAttributeHandler;
import me.baddcamden.attributeutils.listener.AttributeListener;
import me.baddcamden.attributeutils.persistence.PersistenceService;
import me.baddcamden.attributeutils.service.AttributeComputationService;
import me.baddcamden.attributeutils.service.AttributeService;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

import java.nio.file.Path;

public class AttributeUtilitiesPlugin extends JavaPlugin {

    private final AttributeService attributeService = new AttributeService();
    private final AttributeComputationService computationService = new AttributeComputationService();
    private final PersistenceService persistenceService = new PersistenceService();
    private final ItemAttributeHandler itemAttributeHandler = new ItemAttributeHandler(attributeService);
    private final EntityAttributeHandler entityAttributeHandler = new EntityAttributeHandler(attributeService, computationService);

    @Override
    public void onEnable() {
        saveDefaultConfig();
        loadCustomAttributes();
        registerCommands();
        registerListeners();
    }

    private void loadCustomAttributes() {
        if (getConfig().getBoolean("load-custom-attributes-from-folder", true)) {
            Path customFolder = getDataFolder().toPath().resolve(getConfig().getString("custom-attributes-folder", "custom-attributes"));
            persistenceService.loadAttributes(customFolder, attributeService, getConfig());
        }
    }

    private void registerCommands() {
        PluginCommand command = getCommand("attributes");
        if (command != null) {
            command.setExecutor(new AttributeCommand(attributeService, this));
        }
    }

    private void registerListeners() {
        getServer().getPluginManager().registerEvents(new AttributeListener(attributeService, itemAttributeHandler, entityAttributeHandler, computationService), this);
    }

    public AttributeService getAttributeService() {
        return attributeService;
    }
}

package me.baddcamden.attributeutils;

import me.baddcamden.attributeutils.command.AttributeCommand;
import me.baddcamden.attributeutils.command.EntityAttributeCommand;
import me.baddcamden.attributeutils.command.GlobalAttributeCommand;
import me.baddcamden.attributeutils.command.ItemAttributeCommand;
import me.baddcamden.attributeutils.command.PlayerModifierCommand;
import me.baddcamden.attributeutils.handler.entity.EntityAttributeHandler;
import me.baddcamden.attributeutils.handler.item.ItemAttributeHandler;
import me.baddcamden.attributeutils.listener.AttributeListener;
import me.baddcamden.attributeutils.persistence.PersistenceService;
import me.baddcamden.attributeutils.service.AttributeService;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

import java.nio.file.Path;

public class AttributeUtilitiesPlugin extends JavaPlugin {

    private final AttributeService attributeService = new AttributeService();
    private final PersistenceService persistenceService = new PersistenceService();
    private final ItemAttributeHandler itemAttributeHandler = new ItemAttributeHandler(attributeService);
    private final EntityAttributeHandler entityAttributeHandler = new EntityAttributeHandler(attributeService);

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
        PluginCommand attributesCommand = getCommand("attributes");
        if (attributesCommand != null) {
            attributesCommand.setExecutor(new AttributeCommand(attributeService, this));
        }

        PluginCommand globalsCommand = getCommand("attributeglobals");
        if (globalsCommand != null) {
            globalsCommand.setExecutor(new GlobalAttributeCommand());
        }

        PluginCommand modifiersCommand = getCommand("attributemodifiers");
        if (modifiersCommand != null) {
            modifiersCommand.setExecutor(new PlayerModifierCommand(this));
        }

        PluginCommand itemsCommand = getCommand("attributeitems");
        if (itemsCommand != null) {
            itemsCommand.setExecutor(new ItemAttributeCommand());
        }

        PluginCommand entitiesCommand = getCommand("attributeentities");
        if (entitiesCommand != null) {
            entitiesCommand.setExecutor(new EntityAttributeCommand());
        }
    }

    private void registerListeners() {
        getServer().getPluginManager().registerEvents(new AttributeListener(attributeService, itemAttributeHandler, entityAttributeHandler), this);
    }

    public AttributeService getAttributeService() {
        return attributeService;
    }
}

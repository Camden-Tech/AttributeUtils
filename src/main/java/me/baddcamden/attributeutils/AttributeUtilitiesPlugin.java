package me.baddcamden.attributeutils;

import me.baddcamden.attributeutils.api.AttributeFacade;
import me.baddcamden.attributeutils.command.AttributeCommand;
import me.baddcamden.attributeutils.command.EntityAttributeCommand;
import me.baddcamden.attributeutils.command.GlobalAttributeCommand;
import me.baddcamden.attributeutils.command.ItemAttributeCommand;
import me.baddcamden.attributeutils.command.PlayerModifierCommand;
import me.baddcamden.attributeutils.compute.AttributeComputationEngine;
import me.baddcamden.attributeutils.handler.entity.EntityAttributeHandler;
import me.baddcamden.attributeutils.handler.item.ItemAttributeHandler;
import me.baddcamden.attributeutils.listener.AttributeListener;
import me.baddcamden.attributeutils.model.AttributeDefinitionFactory;
import me.baddcamden.attributeutils.persistence.AttributePersistence;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.nio.file.Path;

public class AttributeUtilitiesPlugin extends JavaPlugin {

    private AttributeFacade attributeFacade;
    private AttributePersistence persistence;
    private ItemAttributeHandler itemAttributeHandler;
    private EntityAttributeHandler entityAttributeHandler;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        AttributeComputationEngine computationEngine = new AttributeComputationEngine();
        this.attributeFacade = new AttributeFacade(this, computationEngine);
        this.persistence = new AttributePersistence(getDataFolder().toPath());
        this.itemAttributeHandler = new ItemAttributeHandler(attributeFacade);
        this.entityAttributeHandler = new EntityAttributeHandler(attributeFacade);

        loadDefinitions();
        registerVanillaBaselines();
        persistence.loadGlobals(attributeFacade);
        loadCustomAttributes();
        registerCommands();
        registerListeners();
    }

    @Override
    public void onDisable() {
        persistence.saveGlobals(attributeFacade);
    }

    private void loadDefinitions() {
        AttributeDefinitionFactory.vanillaAttributes(getConfig()).values().forEach(attributeFacade::registerDefinition);
        AttributeDefinitionFactory.registerConfigCaps(attributeFacade::registerDefinition, getConfig().getConfigurationSection("global-attribute-caps"));
    }

    private void loadCustomAttributes() {
        if (getConfig().getBoolean("load-custom-attributes-from-folder", true)) {
            Path customFolder = getDataFolder().toPath().resolve(getConfig().getString("custom-attributes-folder", "custom-attributes"));
            try {
                java.nio.file.Files.createDirectories(customFolder);
            } catch (Exception e) {
                getLogger().warning("Failed to prepare custom attribute folder: " + e.getMessage());
            }
        }
    }

    private void registerVanillaBaselines() {
        attributeFacade.registerVanillaBaseline("max_hunger", Player::getFoodLevel);
        attributeFacade.registerVanillaBaseline("max_oxygen", Player::getMaximumAir);
    }

    private void registerCommands() {
        PluginCommand attributesCommand = getCommand("attributes");
        if (attributesCommand != null) {
            attributesCommand.setExecutor(new AttributeCommand(attributeFacade, this));
        }

        PluginCommand globalsCommand = getCommand("attributeglobals");
        if (globalsCommand != null) {
            globalsCommand.setExecutor(new GlobalAttributeCommand(attributeFacade));
        }

        PluginCommand modifiersCommand = getCommand("attributemodifiers");
        if (modifiersCommand != null) {
            modifiersCommand.setExecutor(new PlayerModifierCommand(this, attributeFacade));
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
        getServer().getPluginManager().registerEvents(new AttributeListener(attributeFacade, persistence, itemAttributeHandler, entityAttributeHandler), this);
    }

    public AttributeFacade getAttributeFacade() {
        return attributeFacade;
    }
}

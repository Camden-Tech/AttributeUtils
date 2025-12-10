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
import org.bukkit.attribute.Attribute;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.nio.file.Path;
import java.util.Map;

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
        this.itemAttributeHandler = new ItemAttributeHandler(attributeFacade, this);
        this.entityAttributeHandler = new EntityAttributeHandler(attributeFacade, this);

        loadDefinitions();
        registerVanillaBaselines();
        persistence.loadGlobals(attributeFacade);
        loadCustomAttributes();
        registerCommands();
        registerListeners();
    }

    @Override
    public void onDisable() {
        getServer().getOnlinePlayers().forEach(player -> persistence.savePlayer(attributeFacade, player.getUniqueId()));
        persistence.saveGlobals(attributeFacade);
    }

    private void loadDefinitions() {
        Map<String, me.baddcamden.attributeutils.model.AttributeDefinition> vanillaAttributes = AttributeDefinitionFactory.vanillaAttributes(getConfig());
        vanillaAttributes.values().forEach(attributeFacade::registerDefinition);
        AttributeDefinitionFactory.registerConfigCaps(
                attributeFacade::registerDefinition,
                getConfig().getConfigurationSection("global-attribute-caps"),
                vanillaAttributes.keySet());
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
        attributeFacade.registerVanillaBaseline("max_health", player -> getAttributeValue(player, resolveAttribute("MAX_HEALTH", "GENERIC_MAX_HEALTH"), 20));
        attributeFacade.registerVanillaBaseline("attack_damage", player -> getAttributeValue(player, resolveAttribute("ATTACK_DAMAGE", "GENERIC_ATTACK_DAMAGE"), 1));
        attributeFacade.registerVanillaBaseline("attack_speed", player -> getAttributeValue(player, resolveAttribute("ATTACK_SPEED", "GENERIC_ATTACK_SPEED"), 4));
        attributeFacade.registerVanillaBaseline("movement_speed", player -> getAttributeValue(player, resolveAttribute("MOVEMENT_SPEED", "GENERIC_MOVEMENT_SPEED"), 0.1));
        attributeFacade.registerVanillaBaseline("armor", player -> getAttributeValue(player, resolveAttribute("ARMOR", "GENERIC_ARMOR"), 0));
        attributeFacade.registerVanillaBaseline("armor_toughness", player -> getAttributeValue(player, resolveAttribute("ARMOR_TOUGHNESS", "GENERIC_ARMOR_TOUGHNESS"), 0));
        attributeFacade.registerVanillaBaseline("luck", player -> getAttributeValue(player, resolveAttribute("LUCK", "GENERIC_LUCK"), 0));
        attributeFacade.registerVanillaBaseline("knockback_resistance", player -> getAttributeValue(player, resolveAttribute("KNOCKBACK_RESISTANCE", "GENERIC_KNOCKBACK_RESISTANCE"), 0));
        attributeFacade.registerVanillaBaseline("max_hunger", Player::getFoodLevel);
        attributeFacade.registerVanillaBaseline("max_oxygen", Player::getMaximumAir);
    }

    private double getAttributeValue(Player player, Attribute attribute, double fallback) {
        if (attribute == null) {
            return fallback;
        }
        org.bukkit.attribute.AttributeInstance instance = player.getAttribute(attribute);
        if (instance == null) {
            return fallback;
        }
        return instance.getBaseValue();
    }

    private Attribute resolveAttribute(String... candidates) {
        for (String candidate : candidates) {
            try {
                return Attribute.valueOf(candidate);
            } catch (IllegalArgumentException ignored) {
                // try the next candidate
            }
        }
        return null;
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
            itemsCommand.setExecutor(new ItemAttributeCommand(this, itemAttributeHandler));
        }

        PluginCommand entitiesCommand = getCommand("attributeentities");
        if (entitiesCommand != null) {
            entitiesCommand.setExecutor(new EntityAttributeCommand(this, entityAttributeHandler));
        }
    }

    private void registerListeners() {
        getServer().getPluginManager().registerEvents(new AttributeListener(attributeFacade, persistence, itemAttributeHandler, entityAttributeHandler), this);
    }

    public AttributeFacade getAttributeFacade() {
        return attributeFacade;
    }
}

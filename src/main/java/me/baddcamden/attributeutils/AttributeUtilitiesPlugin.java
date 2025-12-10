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
        saveDefaultDataFiles();
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

    private void saveDefaultDataFiles() {
        saveResource("global.yml", false);
        if (getConfig().getBoolean("load-custom-attributes-from-folder", true)) {
            saveResource("custom-attributes/max_mana.yml", false);
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
        attributeFacade.registerVanillaBaseline("block_interaction_range", player -> getAttributeValue(player, resolveAttribute("PLAYER_BLOCK_INTERACTION_RANGE", "GENERIC_BLOCK_INTERACTION_RANGE", "BLOCK_INTERACTION_RANGE"), 4.5));
        attributeFacade.registerVanillaBaseline("entity_interaction_range", player -> getAttributeValue(player, resolveAttribute("PLAYER_ENTITY_INTERACTION_RANGE", "GENERIC_ENTITY_INTERACTION_RANGE", "ENTITY_INTERACTION_RANGE"), 3.0));
        attributeFacade.registerVanillaBaseline("mining_efficiency", player -> getAttributeValue(player, resolveAttribute("PLAYER_MINING_EFFICIENCY", "GENERIC_MINING_EFFICIENCY", "MINING_EFFICIENCY"), 1.0));
        attributeFacade.registerVanillaBaseline("block_break_speed", player -> getAttributeValue(player, resolveAttribute("PLAYER_BLOCK_BREAK_SPEED", "GENERIC_BLOCK_BREAK_SPEED", "BLOCK_BREAK_SPEED"), 1.0));
        attributeFacade.registerVanillaBaseline("gravity", player -> getAttributeValue(player, resolveAttribute("GRAVITY", "GENERIC_GRAVITY"), 0.08));
        attributeFacade.registerVanillaBaseline("scale", player -> getAttributeValue(player, resolveAttribute("SCALE", "GENERIC_SCALE"), 1.0));
        attributeFacade.registerVanillaBaseline("regeneration_rate", player -> getAttributeValue(player, resolveAttribute("REGENERATION_RATE", "GENERIC_REGENERATION_RATE"), 1.0));
        attributeFacade.registerVanillaBaseline("damage_reduction", player -> getAttributeValue(player, resolveAttribute("DAMAGE_REDUCTION", "GENERIC_DAMAGE_REDUCTION"), 0.0));
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

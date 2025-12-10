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
import me.baddcamden.attributeutils.command.CommandMessages;
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
        attributeFacade.registerVanillaBaseline("follow_range", player -> getAttributeValue(player, resolveAttribute("FOLLOW_RANGE", "GENERIC_FOLLOW_RANGE"), 32));
        attributeFacade.registerVanillaBaseline("attack_damage", player -> getAttributeValue(player, resolveAttribute("ATTACK_DAMAGE", "GENERIC_ATTACK_DAMAGE"), 1));
        attributeFacade.registerVanillaBaseline("attack_knockback", player -> getAttributeValue(player, resolveAttribute("ATTACK_KNOCKBACK", "GENERIC_ATTACK_KNOCKBACK"), 0));
        attributeFacade.registerVanillaBaseline("attack_speed", player -> getAttributeValue(player, resolveAttribute("ATTACK_SPEED", "GENERIC_ATTACK_SPEED"), 4));
        attributeFacade.registerVanillaBaseline("movement_speed", player -> getAttributeValue(player, resolveAttribute("MOVEMENT_SPEED", "GENERIC_MOVEMENT_SPEED"), 0.1));
        attributeFacade.registerVanillaBaseline("flying_speed", player -> getAttributeValue(player, resolveAttribute("FLYING_SPEED", "GENERIC_FLYING_SPEED"), 0.4));
        attributeFacade.registerVanillaBaseline("armor", player -> getAttributeValue(player, resolveAttribute("ARMOR", "GENERIC_ARMOR"), 0));
        attributeFacade.registerVanillaBaseline("armor_toughness", player -> getAttributeValue(player, resolveAttribute("ARMOR_TOUGHNESS", "GENERIC_ARMOR_TOUGHNESS"), 0));
        attributeFacade.registerVanillaBaseline("luck", player -> getAttributeValue(player, resolveAttribute("LUCK", "GENERIC_LUCK"), 0));
        attributeFacade.registerVanillaBaseline("knockback_resistance", player -> getAttributeValue(player, resolveAttribute("KNOCKBACK_RESISTANCE", "GENERIC_KNOCKBACK_RESISTANCE"), 0));
        attributeFacade.registerVanillaBaseline("max_hunger", Player::getFoodLevel);
        attributeFacade.registerVanillaBaseline("max_oxygen", Player::getMaximumAir);
        attributeFacade.registerVanillaBaseline("oxygen_bonus", player -> getAttributeValue(player, resolveAttribute("OXYGEN_BONUS", "PLAYER_OXYGEN_BONUS"), 0));
        attributeFacade.registerVanillaBaseline("block_range", player -> getAttributeValue(player, resolveAttribute("BLOCK_INTERACTION_RANGE", "PLAYER_BLOCK_INTERACTION_RANGE"), 5));
        attributeFacade.registerVanillaBaseline("interaction_range", player -> getAttributeValue(player, resolveAttribute("ENTITY_INTERACTION_RANGE", "PLAYER_ENTITY_INTERACTION_RANGE"), 3));
        attributeFacade.registerVanillaBaseline("block_break_speed", player -> getAttributeValue(player, resolveAttribute("BLOCK_BREAK_SPEED", "PLAYER_BLOCK_BREAK_SPEED"), 1));
        attributeFacade.registerVanillaBaseline("mining_efficiency", player -> getAttributeValue(player, resolveAttribute("MINING_EFFICIENCY", "PLAYER_MINING_EFFICIENCY"), 1));
        attributeFacade.registerVanillaBaseline("gravity", player -> getAttributeValue(player, resolveAttribute("GRAVITY", "GENERIC_GRAVITY"), 1));
        attributeFacade.registerVanillaBaseline("scale", player -> getAttributeValue(player, resolveAttribute("SCALE", "GENERIC_SCALE"), 1));
        attributeFacade.registerVanillaBaseline("step_height", player -> getAttributeValue(player, resolveAttribute("STEP_HEIGHT", "GENERIC_STEP_HEIGHT"), 0.6));
        attributeFacade.registerVanillaBaseline("safe_fall_distance", player -> getAttributeValue(player, resolveAttribute("SAFE_FALL_DISTANCE", "GENERIC_SAFE_FALL_DISTANCE"), 3));
        attributeFacade.registerVanillaBaseline("fall_damage_multiplier", player -> getAttributeValue(player, resolveAttribute("FALL_DAMAGE_MULTIPLIER", "GENERIC_FALL_DAMAGE_MULTIPLIER"), 1));
        attributeFacade.registerVanillaBaseline("jump_strength", player -> getAttributeValue(player, resolveAttribute("JUMP_STRENGTH", "GENERIC_JUMP_STRENGTH"), 1));
        attributeFacade.registerVanillaBaseline("sneaking_speed", player -> getAttributeValue(player, resolveAttribute("SNEAKING_SPEED", "PLAYER_SNEAKING_SPEED"), 1));
        attributeFacade.registerVanillaBaseline("sprint_speed", player -> getAttributeValue(player, resolveAttribute("SPRINT_SPEED", "PLAYER_SPRINT_SPEED", "PLAYER_SPRINTING_SPEED"), 1));
        attributeFacade.registerVanillaBaseline("swim_speed", player -> getAttributeValue(player, resolveAttribute("SWIM_SPEED", "PLAYER_SWIM_SPEED", "GENERIC_SWIM_SPEED"), 1));
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
        CommandMessages messages = new CommandMessages(this);

        PluginCommand attributesCommand = getCommand("attributes");
        if (attributesCommand != null) {
            AttributeCommand attributeCommand = new AttributeCommand(attributeFacade, this);
            attributesCommand.setExecutor(attributeCommand);
            attributesCommand.setTabCompleter(attributeCommand);
        }

        PluginCommand globalsCommand = getCommand("attributeglobals");
        if (globalsCommand != null) {
            GlobalAttributeCommand globalAttributeCommand = new GlobalAttributeCommand(attributeFacade, messages);
            globalsCommand.setExecutor(globalAttributeCommand);
            globalsCommand.setTabCompleter(globalAttributeCommand);
        }

        PluginCommand modifiersCommand = getCommand("attributemodifiers");
        if (modifiersCommand != null) {
            PlayerModifierCommand modifierCommand = new PlayerModifierCommand(this, attributeFacade);
            modifiersCommand.setExecutor(modifierCommand);
            modifiersCommand.setTabCompleter(modifierCommand);
        }

        PluginCommand itemsCommand = getCommand("attributeitems");
        if (itemsCommand != null) {
            ItemAttributeCommand itemAttributeCommand = new ItemAttributeCommand(this, itemAttributeHandler, attributeFacade);
            itemsCommand.setExecutor(itemAttributeCommand);
            itemsCommand.setTabCompleter(itemAttributeCommand);
        }

        PluginCommand entitiesCommand = getCommand("attributeentities");
        if (entitiesCommand != null) {
            EntityAttributeCommand entityAttributeCommand = new EntityAttributeCommand(this, entityAttributeHandler, attributeFacade);
            entitiesCommand.setExecutor(entityAttributeCommand);
            entitiesCommand.setTabCompleter(entityAttributeCommand);
        }
    }

    private void registerListeners() {
        getServer().getPluginManager().registerEvents(new AttributeListener(attributeFacade, persistence, itemAttributeHandler, entityAttributeHandler), this);
    }

    public AttributeFacade getAttributeFacade() {
        return attributeFacade;
    }
}

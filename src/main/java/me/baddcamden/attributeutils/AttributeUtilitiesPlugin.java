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
import org.bukkit.configuration.ConfigurationSection;

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
        ConfigurationSection defaults = getConfig().getConfigurationSection("vanilla-attribute-defaults");
        if (defaults == null) {
            getLogger().warning("No vanilla attribute defaults configured; skipping vanilla baselines.");
            return;
        }

        defaults.getKeys(false).forEach(key -> {
            ConfigurationSection entry = defaults.getConfigurationSection(key);
            if (entry == null) {
                getLogger().warning("Skipping vanilla baseline '" + key + "': value must be a configuration section.");
                return;
            }

            if (!entry.isSet("default-base")) {
                getLogger().warning("Skipping vanilla baseline '" + key + "': missing required 'default-base'.");
                return;
            }

            double defaultBase = entry.getDouble("default-base");
            String provider = entry.getString("provider", "attribute").toLowerCase(java.util.Locale.ROOT);

            java.util.function.ToDoubleFunction<Player> supplier;
            switch (provider) {
                case "food-level":
                    supplier = Player::getFoodLevel;
                    break;
                case "maximum-air":
                    supplier = Player::getMaximumAir;
                    break;
                case "static":
                    supplier = player -> defaultBase;
                    break;
                case "attribute":
                    java.util.List<String> candidates = resolveAttributeCandidates(entry);
                    if (candidates.isEmpty()) {
                        getLogger().warning("Vanilla baseline '" + key + "' is missing 'bukkit-attributes'; using default value only.");
                    }
                    Attribute attribute = resolveAttribute(candidates);
                    if (attribute == null) {
                        getLogger().warning("Vanilla baseline '" + key + "' specifies unknown Bukkit attributes: " + candidates);
                    }
                    Attribute finalAttribute = attribute;
                    supplier = player -> getAttributeValue(player, finalAttribute, defaultBase);
                    break;
                default:
                    getLogger().warning("Skipping vanilla baseline '" + key + "': unknown provider '" + provider + "'.");
                    return;
            }

            String attributeId = key.toLowerCase(java.util.Locale.ROOT).replace('-', '_');
            attributeFacade.registerVanillaBaseline(attributeId, supplier);
        });
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

    private Attribute resolveAttribute(java.util.List<String> candidates) {
        for (String candidate : candidates) {
            try {
                return Attribute.valueOf(candidate);
            } catch (IllegalArgumentException ignored) {
                // try the next candidate
            }
        }
        return null;
    }

    private java.util.List<String> resolveAttributeCandidates(ConfigurationSection entry) {
        if (entry.isList("bukkit-attributes")) {
            return entry.getStringList("bukkit-attributes");
        }
        if (entry.isString("bukkit-attributes")) {
            return java.util.List.of(entry.getString("bukkit-attributes"));
        }
        return java.util.Collections.emptyList();
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

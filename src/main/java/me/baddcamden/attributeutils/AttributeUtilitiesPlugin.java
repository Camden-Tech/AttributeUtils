package me.baddcamden.attributeutils;

import me.baddcamden.attributeutils.api.AttributeFacade;
import me.baddcamden.attributeutils.command.AttributeCommand;
import me.baddcamden.attributeutils.command.EntityAttributeCommand;
import me.baddcamden.attributeutils.command.GlobalAttributeCommand;
import me.baddcamden.attributeutils.command.ItemAttributeCommand;
import me.baddcamden.attributeutils.command.PlayerModifierCommand;
import me.baddcamden.attributeutils.compute.AttributeComputationEngine;
import me.baddcamden.attributeutils.api.VanillaAttributeSupplier;
import me.baddcamden.attributeutils.handler.entity.EntityAttributeHandler;
import me.baddcamden.attributeutils.handler.item.ItemAttributeHandler;
import me.baddcamden.attributeutils.listener.AttributeListener;
import me.baddcamden.attributeutils.model.AttributeDefinition;
import me.baddcamden.attributeutils.model.AttributeDefinitionFactory;
import me.baddcamden.attributeutils.model.CapConfig;
import me.baddcamden.attributeutils.model.MultiplierApplicability;
import me.baddcamden.attributeutils.persistence.AttributePersistence;
import me.baddcamden.attributeutils.command.CommandMessages;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.Locale;

public class AttributeUtilitiesPlugin extends JavaPlugin {

    private AttributeFacade attributeFacade;
    private AttributePersistence persistence;
    private ItemAttributeHandler itemAttributeHandler;
    private EntityAttributeHandler entityAttributeHandler;
    private Map<String, Attribute> vanillaAttributeTargets;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        initializePlugin();
    }

    @Override
    public void onDisable() {
        List<CompletableFuture<Void>> saves = new ArrayList<>();
        getServer().getOnlinePlayers().forEach(player -> saves.add(persistence.savePlayerAsync(attributeFacade, player.getUniqueId())));
        saves.add(persistence.saveGlobalsAsync(attributeFacade));
        CompletableFuture.allOf(saves.toArray(new CompletableFuture[0])).join();
    }

    public void reloadAttributes() {
        if (persistence != null && attributeFacade != null) {
            List<CompletableFuture<Void>> saves = new ArrayList<>();
            getServer().getOnlinePlayers().forEach(player -> saves.add(persistence.savePlayerAsync(attributeFacade, player.getUniqueId())));
            saves.add(persistence.saveGlobalsAsync(attributeFacade));
            CompletableFuture.allOf(saves.toArray(new CompletableFuture[0])).join();
        }

        reloadConfig();
        initializePlugin();
    }

    private void initializePlugin() {
        HandlerList.unregisterAll(this);

        AttributeComputationEngine computationEngine = new AttributeComputationEngine();
        AttributeFacade newAttributeFacade = new AttributeFacade(this, computationEngine);
        AttributePersistence newPersistence = new AttributePersistence(getDataFolder().toPath(), this);
        vanillaAttributeTargets = new HashMap<>();
        ItemAttributeHandler newItemAttributeHandler = new ItemAttributeHandler(newAttributeFacade, this);
        EntityAttributeHandler newEntityAttributeHandler = new EntityAttributeHandler(newAttributeFacade, this, vanillaAttributeTargets);

        this.attributeFacade = newAttributeFacade;
        this.persistence = newPersistence;
        this.itemAttributeHandler = newItemAttributeHandler;
        this.entityAttributeHandler = newEntityAttributeHandler;

        loadDefinitions();
        registerVanillaBaselines();
        newPersistence.loadGlobalsAsync(newAttributeFacade);
        Executor syncExecutor = command -> getServer().getScheduler().runTask(this, command);
        getServer().getOnlinePlayers().forEach(player -> newPersistence.loadPlayerAsync(newAttributeFacade, player.getUniqueId())
                .thenRunAsync(() -> {
                    newItemAttributeHandler.applyDefaults(player.getInventory());
                    newItemAttributeHandler.applyPersistentAttributes(player);
                    newEntityAttributeHandler.applyPlayerCaps(player);
                }, syncExecutor));
        loadCustomAttributes();
        registerCommands();
        registerListeners();
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

            try (java.util.stream.Stream<Path> files = Files.list(customFolder)) {
                files.filter(path -> {
                            String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
                            return name.endsWith(".yml") || name.endsWith(".yaml");
                        })
                        .forEach(path -> {
                            try {
                                AttributeDefinition definition = parseCustomAttribute(path);
                                if (definition != null) {
                                    attributeFacade.registerDefinition(definition);
                                    getLogger().info("Loaded custom attribute: " + definition.id());
                                }
                            } catch (Exception ex) {
                                getLogger().severe("Failed to load custom attribute from '" + path.getFileName() + "': " + ex.getMessage());
                            }
                        });
            } catch (IOException e) {
                getLogger().severe("Failed to scan custom attribute folder: " + e.getMessage());
            }
        }
    }

    private AttributeDefinition parseCustomAttribute(Path file) {
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file.toFile());

        String id = config.getString("id");
        if (id == null || id.isBlank()) {
            getLogger().warning("Skipping custom attribute '" + file.getFileName() + "': missing 'id'.");
            return null;
        }

        String displayName = config.getString("display-name");
        if (displayName == null || displayName.isBlank()) {
            getLogger().warning("Skipping custom attribute '" + file.getFileName() + "': missing 'display-name'.");
            return null;
        }

        boolean dynamic = config.getBoolean("dynamic", false);
        double defaultBase = config.getDouble("default-base", 0);
        double defaultCurrent = config.isSet("default-current") ? config.getDouble("default-current") : defaultBase;

        CapConfig capConfig = parseCapConfig(config.getConfigurationSection("cap"));
        MultiplierApplicability multipliers = parseMultipliers(config.getConfigurationSection("multipliers"));

        return new AttributeDefinition(
                id.toLowerCase(Locale.ROOT),
                displayName,
                dynamic,
                defaultBase,
                defaultCurrent,
                capConfig,
                multipliers
        );
    }

    private CapConfig parseCapConfig(ConfigurationSection section) {
        if (section == null) {
            return new CapConfig(0, Double.MAX_VALUE, Map.of());
        }

        double min = section.getDouble("min", 0);
        double max = section.getDouble("max", Double.MAX_VALUE);
        Map<String, Double> overrides = new LinkedHashMap<>();
        ConfigurationSection overrideSection = section.getConfigurationSection("overrides");
        if (overrideSection != null) {
            for (String key : overrideSection.getKeys(false)) {
                overrides.put(key.toLowerCase(Locale.ROOT), overrideSection.getDouble(key));
            }
        }

        return new CapConfig(min, max, overrides);
    }

    private MultiplierApplicability parseMultipliers(ConfigurationSection section) {
        if (section == null) {
            return MultiplierApplicability.allowAllMultipliers();
        }

        boolean applyAll = section.getBoolean("apply-all", true);
        Set<String> allowed = Set.copyOf(section.getStringList("allowed"));
        Set<String> ignored = Set.copyOf(section.getStringList("ignored"));

        if (applyAll) {
            if (!ignored.isEmpty()) {
                return MultiplierApplicability.optOut(ignored);
            }
            return MultiplierApplicability.allowAllMultipliers();
        }

        return MultiplierApplicability.optIn(allowed);
    }

    private void registerVanillaBaselines() {
        ConfigurationSection defaults = getConfig().getConfigurationSection("vanilla-attribute-defaults");
        if (defaults == null) {
            getLogger().warning("No vanilla attribute defaults configured; skipping vanilla baselines.");
            return;
        }

        vanillaAttributeTargets.clear();

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

            VanillaAttributeSupplier supplier;
            Attribute attribute = null;
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
                    attribute = resolveAttribute(candidates);
                    if (attribute == null) {
                        getLogger().warning("Vanilla baseline '" + key + "' specifies unknown Bukkit attributes: " + candidates);
                    }
                    String attributeId = key.toLowerCase(java.util.Locale.ROOT).replace('-', '_');
                    VanillaAttributeSupplier dynamicSupplier = createDynamicSupplier(attributeId, attribute, defaultBase);
                    if (dynamicSupplier != null) {
                        supplier = player -> dynamicSupplier.getVanillaValue(player);
                        break;
                    }
                    Attribute finalAttribute = attribute;
                    supplier = player -> getAttributeValue(player, finalAttribute, defaultBase);
                    break;
                default:
                    getLogger().warning("Skipping vanilla baseline '" + key + "': unknown provider '" + provider + "'.");
                    return;
            }

            String attributeId = key.toLowerCase(java.util.Locale.ROOT).replace('-', '_');
            if (attribute != null) {
                vanillaAttributeTargets.put(attributeId, attribute);
            }
            attributeFacade.registerVanillaBaseline(attributeId, supplier);
        });
    }

    private double getAttributeValue(Player player, Attribute attribute, double fallback) {
        if (attribute == null) {
            return fallback;
        }
        org.bukkit.attribute.AttributeInstance instance = player.getAttribute(attribute);
        if (instance != null) {
            return instance.getValue();
        }

        return computeEquipmentAttribute(player, attribute, fallback);
    }

    private VanillaAttributeSupplier createDynamicSupplier(String attributeId, Attribute attribute, double defaultBase) {
        switch (attributeId) {
            case "armor":
                return player -> resolveArmorValue(player, attribute, defaultBase);
            case "armor_toughness":
                return player -> resolveArmorToughnessValue(player, attribute, defaultBase);
            case "knockback_resistance":
                return player -> resolveKnockbackResistanceValue(player, attribute, defaultBase);
            case "attack_damage":
                return player -> resolveAttackDamage(player, attribute, defaultBase);
            case "attack_knockback":
                return player -> resolveAttackKnockback(player, attribute, defaultBase);
            case "attack_speed":
                return player -> resolveAttackSpeed(player, attribute, defaultBase);
            default:
                return null;
        }
    }

    private double resolveArmorValue(Player player, Attribute configuredAttribute, double fallback) {
        Attribute target = configuredAttribute != null
                ? configuredAttribute
                : resolveAttributeByNames("GENERIC_ARMOR", "ARMOR");
        return getAttributeValue(player, target, fallback);
    }

    private double resolveArmorToughnessValue(Player player, Attribute configuredAttribute, double fallback) {
        Attribute target = configuredAttribute != null
                ? configuredAttribute
                : resolveAttributeByNames("GENERIC_ARMOR_TOUGHNESS", "ARMOR_TOUGHNESS");
        return getAttributeValue(player, target, fallback);
    }

    private double resolveKnockbackResistanceValue(Player player, Attribute configuredAttribute, double fallback) {
        Attribute target = configuredAttribute != null
                ? configuredAttribute
                : resolveAttributeByNames("GENERIC_KNOCKBACK_RESISTANCE", "KNOCKBACK_RESISTANCE");
        return getAttributeValue(player, target, fallback);
    }

    private double resolveAttackDamage(Player player, Attribute configuredAttribute, double fallback) {
        Attribute target = configuredAttribute != null
                ? configuredAttribute
                : resolveAttributeByNames("GENERIC_ATTACK_DAMAGE", "ATTACK_DAMAGE");
        return getAttributeValue(player, target, fallback);
    }

    private double resolveAttackKnockback(Player player, Attribute configuredAttribute, double fallback) {
        Attribute target = configuredAttribute != null
                ? configuredAttribute
                : resolveAttributeByNames("GENERIC_ATTACK_KNOCKBACK", "ATTACK_KNOCKBACK");
        return getAttributeValue(player, target, fallback);
    }

    private double resolveAttackSpeed(Player player, Attribute configuredAttribute, double fallback) {
        Attribute target = configuredAttribute != null
                ? configuredAttribute
                : resolveAttributeByNames("GENERIC_ATTACK_SPEED", "ATTACK_SPEED");
        return getAttributeValue(player, target, fallback);
    }

    private Attribute resolveAttributeByNames(String... names) {
        for (String name : names) {
            try {
                return Attribute.valueOf(name);
            } catch (IllegalArgumentException ignored) {
                // try the next option
            }
        }
        return null;
    }

    private double computeEquipmentAttribute(Player player, Attribute attribute, double fallback) {
        if (player == null || attribute == null) {
            return fallback;
        }

        org.bukkit.inventory.EntityEquipment equipment = player.getEquipment();
        if (equipment == null) {
            return fallback;
        }

        Map<EquipmentSlot, ItemStack> slots = new HashMap<>();
        slots.put(EquipmentSlot.HAND, equipment.getItemInMainHand());
        slots.put(EquipmentSlot.OFF_HAND, equipment.getItemInOffHand());
        slots.put(EquipmentSlot.HEAD, equipment.getHelmet());
        slots.put(EquipmentSlot.CHEST, equipment.getChestplate());
        slots.put(EquipmentSlot.LEGS, equipment.getLeggings());
        slots.put(EquipmentSlot.FEET, equipment.getBoots());

        double additive = 0d;
        double multiplicative = 1d;

        for (Map.Entry<EquipmentSlot, ItemStack> entry : slots.entrySet()) {
            ItemStack item = entry.getValue();
            if (item == null) {
                continue;
            }
            ItemMeta meta = item.getItemMeta();
            if (meta == null) {
                continue;
            }

            Iterable<AttributeModifier> modifiers = meta.getAttributeModifiers(attribute);
            if (modifiers == null) {
                continue;
            }

            for (AttributeModifier modifier : modifiers) {
                EquipmentSlot slot = modifier.getSlot();
                if (slot != null && slot != entry.getKey()) {
                    continue;
                }
                switch (modifier.getOperation()) {
                    case ADD_NUMBER -> additive += modifier.getAmount();
                    default -> multiplicative *= 1 + modifier.getAmount();
                }
            }
        }

        return (fallback + additive) * multiplicative;
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
            GlobalAttributeCommand globalAttributeCommand = new GlobalAttributeCommand(this, attributeFacade, messages, getName());
            globalsCommand.setExecutor(globalAttributeCommand);
            globalsCommand.setTabCompleter(globalAttributeCommand);
        }

        PluginCommand modifiersCommand = getCommand("attributemodifiers");
        if (modifiersCommand != null) {
            PlayerModifierCommand modifierCommand = new PlayerModifierCommand(this, attributeFacade, entityAttributeHandler);
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
        getServer().getPluginManager().registerEvents(new AttributeListener(this, attributeFacade, persistence, itemAttributeHandler, entityAttributeHandler), this);
    }

    public AttributeFacade getAttributeFacade() {
        return attributeFacade;
    }
}

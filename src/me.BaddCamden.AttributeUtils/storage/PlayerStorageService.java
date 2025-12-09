package me.BaddCamden.AttributeUtils.storage;

import me.BaddCamden.AttributeUtils.AttributeDefaultsCache;
import me.BaddCamden.AttributeUtils.config.AttributeConfig;
import me.BaddCamden.AttributeUtils.config.CustomAttributeDefinition;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.util.EnumSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public class PlayerStorageService {
    private final Plugin plugin;
    private final AttributeConfig attributeConfig;
    private final AttributeDefaultsCache defaultsCache;
    private final File playerFolder;
    private final Map<UUID, PlayerAttributeData> cachedPlayers;

    public PlayerStorageService(Plugin plugin, AttributeConfig attributeConfig, AttributeDefaultsCache defaultsCache, Map<UUID, PlayerAttributeData> cachedPlayers) {
        this.plugin = plugin;
        this.attributeConfig = attributeConfig;
        this.defaultsCache = defaultsCache;
        this.cachedPlayers = cachedPlayers;
        this.playerFolder = new File(plugin.getDataFolder(), attributeConfig.getPlayerStorageFolder());
        if (!playerFolder.exists()) {
            //noinspection ResultOfMethodCallIgnored
            playerFolder.mkdirs();
        }
    }

    public PlayerAttributeData loadPlayerData(UUID playerId) {
        return cachedPlayers.computeIfAbsent(playerId, id -> {
            File file = playerFile(id);
            if (!file.exists()) {
                PlayerAttributeData data = new PlayerAttributeData();
                attributeConfig.getCustomAttributes().forEach((key, def) -> {
                    data.setCustomDefault(key, def.getDefaultValue());
                    data.setCustomCurrent(key, def.getDefaultValue());
                });
                return data;
            }
            YamlConfiguration configuration = YamlConfiguration.loadConfiguration(file);
            return fromConfiguration(configuration);
        });
    }

    public void applyToPlayer(Player player, PlayerAttributeData data) {
        defaultsCache.capture(player);
        Set<Attribute> observedAttributes = EnumSet.noneOf(Attribute.class);
        observedAttributes.addAll(attributeConfig.getDefaults().keySet());
        observedAttributes.addAll(attributeConfig.getCaps().keySet());
        observedAttributes.addAll(data.getCurrentPermanent().keySet());
        observedAttributes.addAll(data.getCurrentTemporary().keySet());

        for (Attribute attribute : Attribute.values()) {
            AttributeInstance instance = player.getAttribute(attribute);
            if (instance == null) {
                continue;
            }
            observedAttributes.add(attribute);
        }

        for (Attribute attribute : observedAttributes) {
            AttributeInstance instance = player.getAttribute(attribute);
            if (instance == null) {
                continue;
            }
            double vanilla = defaultsCache.getVanillaDefault(attribute, player);
            double globalDefault = attributeConfig.getDefault(attribute).orElse(vanilla);
            double globalDelta = globalDefault - vanilla;
            double baseValue = globalDefault + data.getCurrentPermanent(attribute) + data.getCurrentTemporary(attribute);
            Optional<Double> cap = attributeConfig.getCap(attribute);
            if (cap.isPresent()) {
                baseValue = Math.min(cap.get(), baseValue);
            }
            instance.setBaseValue(baseValue);

            // Recalculate player-side deltas so the save step stays aligned with live values.
            double recalculatedPermanent = baseValue - vanilla - globalDelta - data.getCurrentTemporary(attribute);
            data.setCurrentPermanent(attribute, recalculatedPermanent);
            if (!data.getDefaultPermanent().containsKey(attribute)) {
                data.setDefaultPermanent(attribute, recalculatedPermanent);
            }
        }

        applyCustomAttributes(player, data);
    }

    private void applyCustomAttributes(Player player, PlayerAttributeData data) {
        PersistentDataContainer container = player.getPersistentDataContainer();
        for (CustomAttributeDefinition definition : attributeConfig.getCustomAttributes().values()) {
            double defaultValue = data.getCustomDefault(definition.getKey());
            if (!data.getCustomDefaults().containsKey(definition.getKey())) {
                defaultValue = definition.getDefaultValue();
                data.setCustomDefault(definition.getKey(), defaultValue);
            }
            if (!data.getCustomCurrents().containsKey(definition.getKey())) {
                data.setCustomCurrent(definition.getKey(), defaultValue);
            }
            double currentValue = data.getCustomCurrent(definition.getKey());
            NamespacedKey valueKey = new NamespacedKey(plugin, "custom_" + definition.getKey());
            NamespacedKey labelKey = new NamespacedKey(plugin, "custom_" + definition.getKey() + "_label");
            container.set(valueKey, PersistentDataType.DOUBLE, currentValue);
            container.set(labelKey, PersistentDataType.STRING, definition.labelFor(currentValue));
        }
    }

    public void flushToDisk(Player player) {
        PlayerAttributeData data = cachedPlayers.get(player.getUniqueId());
        if (data == null) {
            return;
        }

        // Ensure the data reflects the current live state before saving.
        refreshFromPlayer(player, data);
        YamlConfiguration configuration = new YamlConfiguration();

        writeAttributeMap(configuration, "defaults.permanent", data.getDefaultPermanent());
        writeAttributeMap(configuration, "defaults.temporary", data.getDefaultTemporary());
        writeAttributeMap(configuration, "current.permanent", data.getCurrentPermanent());
        writeAttributeMap(configuration, "current.temporary", data.getCurrentTemporary());

        ConfigurationSection customDefaultSection = configuration.createSection("custom.default");
        for (Map.Entry<String, Double> entry : data.getCustomDefaults().entrySet()) {
            customDefaultSection.set(entry.getKey(), entry.getValue());
        }
        ConfigurationSection customCurrentSection = configuration.createSection("custom.current");
        for (Map.Entry<String, Double> entry : data.getCustomCurrents().entrySet()) {
            customCurrentSection.set(entry.getKey(), entry.getValue());
        }

        try {
            configuration.save(playerFile(player.getUniqueId()));
        } catch (IOException ex) {
            plugin.getLogger().warning("Failed to save attribute data for " + player.getName() + ": " + ex.getMessage());
        }
    }

    private void refreshFromPlayer(Player player, PlayerAttributeData data) {
        defaultsCache.capture(player);
        for (Attribute attribute : Attribute.values()) {
            AttributeInstance instance = player.getAttribute(attribute);
            if (instance == null) {
                continue;
            }
            double vanilla = defaultsCache.getVanillaDefault(attribute, player);
            double globalDefault = attributeConfig.getDefault(attribute).orElse(vanilla);
            double globalDelta = globalDefault - vanilla;
            double temporary = data.getCurrentTemporary(attribute);
            double permanentDelta = instance.getBaseValue() - vanilla - globalDelta - temporary;
            data.setCurrentPermanent(attribute, permanentDelta);
            if (!data.getDefaultPermanent().containsKey(attribute)) {
                data.setDefaultPermanent(attribute, permanentDelta);
            }
        }

        for (CustomAttributeDefinition definition : attributeConfig.getCustomAttributes().values()) {
            NamespacedKey key = new NamespacedKey(plugin, "custom_" + definition.getKey());
            Double stored = player.getPersistentDataContainer().get(key, PersistentDataType.DOUBLE);
            if (stored != null) {
                data.setCustomCurrent(definition.getKey(), stored);
            }
        }
    }

    private PlayerAttributeData fromConfiguration(YamlConfiguration configuration) {
        PlayerAttributeData data = new PlayerAttributeData();
        readAttributeMap(configuration.getConfigurationSection("defaults.permanent"), data::setDefaultPermanent);
        readAttributeMap(configuration.getConfigurationSection("defaults.temporary"), data::setDefaultTemporary);
        readAttributeMap(configuration.getConfigurationSection("current.permanent"), data::setCurrentPermanent);
        readAttributeMap(configuration.getConfigurationSection("current.temporary"), data::setCurrentTemporary);

        ConfigurationSection customDefaults = configuration.getConfigurationSection("custom.default");
        if (customDefaults != null) {
            for (String key : customDefaults.getKeys(false)) {
                data.setCustomDefault(key, customDefaults.getDouble(key));
            }
        }
        ConfigurationSection customCurrents = configuration.getConfigurationSection("custom.current");
        if (customCurrents != null) {
            for (String key : customCurrents.getKeys(false)) {
                data.setCustomCurrent(key, customCurrents.getDouble(key));
            }
        }

        attributeConfig.getCustomAttributes().forEach((key, definition) -> {
            data.getCustomDefaults().putIfAbsent(key, definition.getDefaultValue());
            data.getCustomCurrents().putIfAbsent(key, definition.getDefaultValue());
        });

        data.getDefaultPermanent().forEach((attribute, value) -> data.getCurrentPermanent().putIfAbsent(attribute, value));
        data.getDefaultTemporary().forEach((attribute, value) -> data.getCurrentTemporary().putIfAbsent(attribute, value));

        return data;
    }

    private void writeAttributeMap(YamlConfiguration configuration, String path, Map<Attribute, Double> source) {
        ConfigurationSection section = configuration.createSection(path);
        for (Map.Entry<Attribute, Double> entry : source.entrySet()) {
            section.set(entry.getKey().name(), entry.getValue());
        }
    }

    private void readAttributeMap(ConfigurationSection section, AttributeConsumer consumer) {
        if (section == null) {
            return;
        }
        for (String key : section.getKeys(false)) {
            try {
                Attribute attribute = Attribute.valueOf(key);
                consumer.accept(attribute, section.getDouble(key));
            } catch (IllegalArgumentException ignored) {
            }
        }
    }

    private File playerFile(UUID id) {
        return new File(playerFolder, id.toString() + ".yml");
    }

    private interface AttributeConsumer {
        void accept(Attribute attribute, double value);
    }
}

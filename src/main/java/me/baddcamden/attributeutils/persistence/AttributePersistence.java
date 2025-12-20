package me.baddcamden.attributeutils.persistence;

import me.baddcamden.attributeutils.api.AttributeFacade;
import me.baddcamden.attributeutils.model.AttributeDefinition;
import me.baddcamden.attributeutils.model.AttributeInstance;
import me.baddcamden.attributeutils.model.ModifierEntry;
import me.baddcamden.attributeutils.model.ModifierOperation;
import me.baddcamden.attributeutils.persistence.ResourceMeterState;
import me.baddcamden.attributeutils.persistence.ResourceMeterStore;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Supplier;

/**
 * Handles serialization of {@link AttributeInstance} state to YAML. Baselines for both default and
 * current layers are stored alongside the last computed default final baseline so static
 * attributes can keep their current deltas in sync. Modifier entries are written with their
 * bucket flags so they can be restored into the correct stage buckets during reload. Caps are
 * respected when loading to prevent persisted values from exceeding configured limits or override
 * maxima.
 */
public class AttributePersistence {

    private final JavaPlugin plugin;
    private final Path dataFolder;
    private final Executor asyncExecutor;
    private final Executor syncExecutor;

    public AttributePersistence(Path dataFolder, JavaPlugin plugin) {
        this.plugin = plugin;
        this.dataFolder = dataFolder;
        this.asyncExecutor = runnable -> plugin.getServer().getScheduler().runTaskAsynchronously(plugin, runnable);
        this.syncExecutor = runnable -> plugin.getServer().getScheduler().runTask(plugin, runnable);
    }

    public void loadGlobals(AttributeFacade facade) {
        Path file = dataFolder.resolve("global.yml");
        if (Files.notExists(file)) {
            return;
        }

        FileConfiguration config = YamlConfiguration.loadConfiguration(file.toFile());
        loadCapOverrides(facade, config.getConfigurationSection("caps"));
        loadInstances(facade, config.getConfigurationSection("attributes"), null);
    }

    public void saveGlobals(AttributeFacade facade) {
        FileConfiguration config = new YamlConfiguration();
        ConfigurationSection attributes = config.createSection("attributes");
        writeInstances(attributes, facade.getGlobalInstances());
        ConfigurationSection caps = config.createSection("caps");
        writeCapOverrides(caps, facade);
        save(config, dataFolder.resolve("global.yml"));
    }

    public CompletableFuture<Void> loadGlobalsAsync(AttributeFacade facade) {
        Path file = dataFolder.resolve("global.yml");
        return supplyAsync(() -> Files.notExists(file) ? null : YamlConfiguration.loadConfiguration(file.toFile()))
                .thenCompose(config -> config == null
                        ? CompletableFuture.completedFuture(null)
                        : runSync(() -> {
                            loadCapOverrides(facade, config.getConfigurationSection("caps"));
                            loadInstances(facade, config.getConfigurationSection("attributes"), null);
                        }));
    }

    public CompletableFuture<Void> saveGlobalsAsync(AttributeFacade facade) {
        return supplySync(() -> {
                    FileConfiguration config = new YamlConfiguration();
                    ConfigurationSection attributes = config.createSection("attributes");
                    writeInstances(attributes, facade.getGlobalInstances());
                    ConfigurationSection caps = config.createSection("caps");
                    writeCapOverrides(caps, facade);
                    return new PersistedConfig(config, dataFolder.resolve("global.yml"));
                })
                .thenCompose(this::writeAsync);
    }

    public void loadPlayer(AttributeFacade facade, UUID playerId, ResourceMeterStore meterStore) {
        Path file = dataFolder.resolve("players").resolve(playerId.toString() + ".yml");
        if (Files.notExists(file)) {
            return;
        }
        FileConfiguration config = YamlConfiguration.loadConfiguration(file.toFile());
        loadInstances(facade, config.getConfigurationSection("attributes"), playerId);
        loadMeters(config.getConfigurationSection("meters"), playerId, meterStore);
    }

    public void savePlayer(AttributeFacade facade, UUID playerId, ResourceMeterStore meterStore) {
        FileConfiguration config = new YamlConfiguration();
        ConfigurationSection attributes = config.createSection("attributes");
        writeInstances(attributes, facade.getPlayerInstances(playerId));
        writeMeters(config, playerId, meterStore);
        Path folder = dataFolder.resolve("players");
        try {
            Files.createDirectories(folder);
        } catch (IOException ignored) {
        }
        save(config, folder.resolve(playerId.toString() + ".yml"));
    }

    public CompletableFuture<Void> loadPlayerAsync(AttributeFacade facade, UUID playerId, ResourceMeterStore meterStore) {
        Path file = dataFolder.resolve("players").resolve(playerId.toString() + ".yml");
        return supplyAsync(() -> Files.notExists(file) ? null : YamlConfiguration.loadConfiguration(file.toFile()))
                .thenCompose(config -> config == null
                        ? CompletableFuture.completedFuture(null)
                        : runSync(() -> {
                            loadInstances(facade, config.getConfigurationSection("attributes"), playerId);
                            loadMeters(config.getConfigurationSection("meters"), playerId, meterStore);
                        }));
    }

    public CompletableFuture<Void> savePlayerAsync(AttributeFacade facade, UUID playerId, ResourceMeterStore meterStore) {
        return supplySync(() -> {
                    FileConfiguration config = new YamlConfiguration();
                    ConfigurationSection attributes = config.createSection("attributes");
                    writeInstances(attributes, facade.getPlayerInstances(playerId));
                    writeMeters(config, playerId, meterStore);
                    Path folder = dataFolder.resolve("players");
                    return new PersistedConfig(config, folder.resolve(playerId.toString() + ".yml"));
                })
                .thenCompose(this::writeAsync);
    }

    /**
     * Updates every persisted player file with the provided attribute default so that global edits
     * immediately affect offline players. Online players can be excluded to avoid redundant writes
     * while their in-memory instances are updated separately.
     */
    public void updateOfflinePlayerAttribute(String attributeId, double value, Set<UUID> excludePlayers) {
        Path playersDir = dataFolder.resolve("players");
        if (Files.notExists(playersDir)) {
            return;
        }

        try (java.util.stream.Stream<Path> files = Files.list(playersDir)) {
            files.filter(Files::isRegularFile).forEach(path -> {
                String name = path.getFileName().toString();
                if (name.toLowerCase(Locale.ROOT).endsWith(".yml")) {
                    String idPart = name.substring(0, name.length() - 4);
                    try {
                        UUID playerId = UUID.fromString(idPart);
                        if (excludePlayers != null && excludePlayers.contains(playerId)) {
                            return;
                        }
                    } catch (IllegalArgumentException ignored) {
                        // not a player file; continue processing to be safe
                    }
                }

                FileConfiguration config = YamlConfiguration.loadConfiguration(path.toFile());
                ConfigurationSection attributes = config.getConfigurationSection("attributes");
                if (attributes == null) {
                    attributes = config.createSection("attributes");
                }
                String normalizedId = attributeId.toLowerCase(Locale.ROOT);
                ConfigurationSection attributeSection = attributes.getConfigurationSection(normalizedId);
                if (attributeSection == null) {
                    attributeSection = attributes.createSection(normalizedId);
                }
                attributeSection.set("default-base", value);
                attributeSection.set("current-base", value);
                attributeSection.set("base", value);
                attributeSection.set("default-final-baseline", value);
                save(config, path);
            });
        } catch (IOException ex) {
            plugin.getLogger().warning("Failed to update offline player attributes: " + ex.getMessage());
        }
    }

    /**
     * Loads instances into the facade, clamping baselines to cap values for the associated override
     * key (player ID) and reconstructing modifier buckets with their configured stage flags.
     */
    private void loadInstances(AttributeFacade facade, ConfigurationSection section, UUID playerId) {
        if (section == null) {
            return;
        }

        for (String key : section.getKeys(false)) {
            facade.getDefinition(key).ifPresentOrElse(definition -> {
                double defaultBase = section.getDouble(key + ".default-base",
                        section.getDouble(key + ".base", definition.defaultBaseValue()));
                double currentBase = section.getDouble(key + ".current-base",
                        section.getDouble(key + ".base", definition.defaultCurrentValue()));
                double defaultFinalBaseline = section.getDouble(
                        key + ".default-final-baseline", definition.defaultCurrentValue());
                AttributeInstance instance = playerId == null
                        ? facade.getOrCreateGlobalInstance(definition.id())
                        : facade.getOrCreatePlayerInstance(playerId, definition.id());
                String capKey = playerId == null ? null : playerId.toString();
                instance.setDefaultBaseValue(definition.capConfig().clamp(defaultBase, capKey));
                instance.setCurrentBaseValue(definition.capConfig().clamp(currentBase, capKey));
                instance.setDefaultFinalBaseline(definition.capConfig().clamp(defaultFinalBaseline, capKey));
                ConfigurationSection modifiers = section.getConfigurationSection(key + ".modifiers");
                if (modifiers != null) {
                    for (String modKey : modifiers.getKeys(false)) {
                        ModifierOperation operation = ModifierOperation.valueOf(modifiers.getString(modKey + ".operation", "ADD").toUpperCase(Locale.ROOT));
                        double amount = modifiers.getDouble(modKey + ".amount", 0);
                        boolean temporary = modifiers.getBoolean(modKey + ".temporary", false);
                        boolean appliesToDefault = modifiers.getBoolean(modKey + ".default", false);
                        boolean appliesToCurrent = modifiers.getBoolean(modKey + ".current", !appliesToDefault);
                        boolean useMultiplierKeys = modifiers.getBoolean(modKey + ".use-multiplier-keys", false);
                        Double durationSeconds = null;
                        if (temporary && modifiers.contains(modKey + ".duration-seconds")) {
                            double duration = modifiers.getDouble(modKey + ".duration-seconds", 0);
                            if (duration > 0) {
                                durationSeconds = duration;
                            }
                        }
                        List<String> keyList = modifiers.getStringList(modKey + ".multiplier-keys");
                        Set<String> normalizedKeys = new HashSet<>();
                        keyList.stream().filter(entry -> entry != null && !entry.isBlank()).forEach(entry -> normalizedKeys.add(entry.toLowerCase(Locale.ROOT)));
                        instance.addModifier(new ModifierEntry(modKey, operation, amount, temporary, appliesToDefault, appliesToCurrent, useMultiplierKeys, normalizedKeys, durationSeconds));
                    }
                }
            }, () -> facade.compute(key, null));
        }
    }

    private void loadMeters(ConfigurationSection section, UUID playerId, ResourceMeterStore meterStore) {
        if (section == null || meterStore == null) {
            return;
        }

        ResourceMeterState hunger = readMeter(section.getConfigurationSection("hunger"));
        ResourceMeterState oxygen = readMeter(section.getConfigurationSection("oxygen"));
        if (hunger != null || oxygen != null) {
            meterStore.hydrateMeters(playerId, hunger, oxygen);
        }
    }

    private ResourceMeterState readMeter(ConfigurationSection section) {
        if (section == null) {
            return null;
        }
        if (!section.contains("current") && !section.contains("max")) {
            return null;
        }
        double current = section.getDouble("current", 0);
        double max = section.getDouble("max", 0);
        return new ResourceMeterState(current, max);
    }

    /**
     * Writes the full state of each instance: default/current baselines, the last default final
     * baseline (used for resynchronizing static attributes), and all modifiers with their bucket
     * metadata so they can be restored to the same computation stage.
     */
    private void writeInstances(ConfigurationSection section, Map<String, AttributeInstance> instances) {
        for (Map.Entry<String, AttributeInstance> entry : instances.entrySet()) {
            String attributeId = entry.getKey();
            AttributeInstance instance = entry.getValue();
            section.set(attributeId + ".default-base", instance.getDefaultBaseValue());
            section.set(attributeId + ".current-base", instance.getCurrentBaseValue());
            section.set(attributeId + ".base", instance.getDefaultBaseValue());
            section.set(attributeId + ".default-final-baseline", instance.getDefaultFinalBaseline());
            ConfigurationSection modifiers = section.createSection(attributeId + ".modifiers");
            instance.getModifiers().forEach((key, modifier) -> {
                modifiers.set(key + ".operation", modifier.operation().name());
                modifiers.set(key + ".amount", modifier.amount());
                modifiers.set(key + ".temporary", modifier.isTemporary());
                modifiers.set(key + ".default", modifier.isDefaultModifier());
                modifiers.set(key + ".current", modifier.appliesToCurrent());
                modifiers.set(key + ".use-multiplier-keys", modifier.useMultiplierKeys());
                modifiers.set(key + ".multiplier-keys", List.copyOf(modifier.multiplierKeys()));
                if (modifier.isTemporary()) {
                    modifier.durationSecondsOptional().ifPresent(duration -> modifiers.set(key + ".duration-seconds", duration));
                }
            });
        }
    }

    private void loadCapOverrides(AttributeFacade facade, ConfigurationSection caps) {
        if (caps == null) {
            return;
        }

        loadCapOverrides(facade, caps, "");
    }

    private void loadCapOverrides(AttributeFacade facade, ConfigurationSection section, String prefix) {
        for (String key : section.getKeys(false)) {
            ConfigurationSection child = section.getConfigurationSection(key);
            if (child == null) {
                continue;
            }

            String attributeId = prefix.isEmpty() ? key : prefix + "." + key;
            boolean hasNestedSections = child.getKeys(false).stream().anyMatch(child::isConfigurationSection);
            if (hasNestedSections) {
                loadCapOverrides(facade, child, attributeId);
                continue;
            }

            facade.getDefinition(attributeId).ifPresent(definition -> {
                Map<String, Double> overrideTargets = definition.capConfig().overrideMaxValues();
                child.getKeys(false).forEach(overrideKey -> overrideTargets.put(
                        overrideKey.toLowerCase(Locale.ROOT),
                        child.getDouble(overrideKey)));
            });
        }
    }

    private void writeCapOverrides(ConfigurationSection section, AttributeFacade facade) {
        if (section == null) {
            return;
        }

        for (AttributeDefinition definition : facade.getDefinitions()) {
            Map<String, Double> overrides = definition.capConfig().overrideMaxValues();
            if (overrides.isEmpty()) {
                continue;
            }
            ConfigurationSection attributeSection = section.createSection(definition.id());
            overrides.forEach(attributeSection::set);
        }
    }

    private void writeMeters(FileConfiguration config, UUID playerId, ResourceMeterStore meterStore) {
        if (meterStore == null) {
            return;
        }
        ResourceMeterState hunger = meterStore.getHungerMeter(playerId);
        ResourceMeterState oxygen = meterStore.getOxygenMeter(playerId);

        if (hunger == null && oxygen == null) {
            return;
        }

        ConfigurationSection metersSection = config.createSection("meters");
        writeMeter(metersSection.createSection("hunger"), hunger);
        writeMeter(metersSection.createSection("oxygen"), oxygen);
    }

    private void writeMeter(ConfigurationSection section, ResourceMeterState meter) {
        if (meter == null) {
            return;
        }
        section.set("current", meter.current());
        section.set("max", meter.max());
    }

    private void save(FileConfiguration config, Path target) {
        try {
            Files.createDirectories(target.getParent());
            config.save(target.toFile());
        } catch (IOException ignored) {
        }
    }

    private CompletableFuture<Void> writeAsync(PersistedConfig config) {
        return runAsync(() -> save(config.configuration(), config.target()));
    }

    private CompletableFuture<Void> runAsync(Runnable runnable) {
        return CompletableFuture.runAsync(runnable, asyncExecutor);
    }

    private CompletableFuture<Void> runSync(Runnable runnable) {
        return CompletableFuture.runAsync(runnable, syncExecutor);
    }

    private <T> CompletableFuture<T> supplyAsync(Supplier<T> supplier) {
        return CompletableFuture.supplyAsync(supplier, asyncExecutor);
    }

    private <T> CompletableFuture<T> supplySync(Supplier<T> supplier) {
        return CompletableFuture.supplyAsync(supplier, syncExecutor);
    }

    private record PersistedConfig(FileConfiguration configuration, Path target) {
    }
}

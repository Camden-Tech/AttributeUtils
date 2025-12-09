package me.baddcamden.attributeutils.persistence;

import me.baddcamden.attributeutils.api.AttributeApi;
import me.baddcamden.attributeutils.api.AttributeDefinition;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class PersistenceService {

    public void loadAttributes(Path attributesFolder, AttributeApi attributeApi, FileConfiguration config) {
        try {
            if (Files.notExists(attributesFolder)) {
                Files.createDirectories(attributesFolder);
            }
        } catch (IOException e) {
            throw new IllegalStateException("Unable to prepare attributes directory: " + attributesFolder, e);
        }

        registerConfigCaps(attributeApi, config.getConfigurationSection("global-attribute-caps"));
        registerPlayerStats(attributeApi, config);
    }

    private void registerConfigCaps(AttributeApi attributeApi, ConfigurationSection caps) {
        if (caps == null) {
            return;
        }

        for (String key : caps.getKeys(false)) {
            double capValue = caps.getDouble(key);
            attributeApi.registerAttributeDefinition(new AttributeDefinition(key, capValue, capValue));
        }
    }

    private void registerPlayerStats(AttributeApi attributeApi, FileConfiguration config) {
        double maxHunger = config.getDouble("max-hunger", 20);
        double maxOxygen = config.getDouble("max-oxygen", 20);

        attributeApi.registerAttributeDefinition(new AttributeDefinition("max_hunger", maxHunger, maxHunger));
        attributeApi.registerAttributeDefinition(new AttributeDefinition("max_oxygen", maxOxygen, maxOxygen));

        attributeApi.registerVanillaBaseline("max_hunger", player -> player == null ? maxHunger : player.getFoodLevel());
        attributeApi.registerVanillaBaseline("max_oxygen", player -> {
            if (player == null) {
                return maxOxygen;
            }
            return player.getMaximumAir();
        });
    }
}

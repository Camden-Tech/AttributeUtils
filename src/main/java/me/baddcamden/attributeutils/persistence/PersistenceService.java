package me.baddcamden.attributeutils.persistence;

import me.baddcamden.attributeutils.attributes.model.AttributeModel;
import me.baddcamden.attributeutils.service.AttributeService;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class PersistenceService {

    public void loadAttributes(Path attributesFolder, AttributeService attributeService, FileConfiguration config) {
        try {
            if (Files.notExists(attributesFolder)) {
                Files.createDirectories(attributesFolder);
            }
        } catch (IOException e) {
            throw new IllegalStateException("Unable to prepare attributes directory: " + attributesFolder, e);
        }

        registerConfigCaps(attributeService, config.getConfigurationSection("global-attribute-caps"));
        registerPlayerStats(attributeService, config);
    }

    private void registerConfigCaps(AttributeService attributeService, ConfigurationSection caps) {
        if (caps == null) {
            return;
        }

        for (String key : caps.getKeys(false)) {
            double capValue = caps.getDouble(key);
            attributeService.registerAttribute(new AttributeModel(key, capValue, capValue));
        }
    }

    private void registerPlayerStats(AttributeService attributeService, FileConfiguration config) {
        double maxHunger = config.getDouble("max-hunger", 20);
        double maxOxygen = config.getDouble("max-oxygen", 20);

        attributeService.registerAttribute(new AttributeModel("max_hunger", maxHunger, maxHunger));
        attributeService.registerAttribute(new AttributeModel("max_oxygen", maxOxygen, maxOxygen));
    }
}

package me.baddcamden.attributeutils.persistence;

import me.baddcamden.attributeutils.model.AttributeDefinitionFactory;
import me.baddcamden.attributeutils.service.AttributeService;
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

        AttributeDefinitionFactory.registerConfigCaps(attributeService::registerAttribute, config.getConfigurationSection("global-attribute-caps"));
        AttributeDefinitionFactory.vanillaAttributes(config).values().forEach(attributeService::registerAttribute);
    }
}

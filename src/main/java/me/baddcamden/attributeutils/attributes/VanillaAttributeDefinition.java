package me.baddcamden.attributeutils.attributes;

import org.bukkit.attribute.Attribute;

import java.util.List;

public record VanillaAttributeDefinition(String key, List<Attribute> attributes, double defaultBase) {
    public String dataPath() {
        return "attributes." + key;
    }
}

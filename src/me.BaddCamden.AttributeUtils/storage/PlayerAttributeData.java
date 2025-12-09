package me.BaddCamden.AttributeUtils.storage;

import org.bukkit.attribute.Attribute;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

public class PlayerAttributeData {
    private final Map<Attribute, Double> defaultPermanent = new EnumMap<>(Attribute.class);
    private final Map<Attribute, Double> defaultTemporary = new EnumMap<>(Attribute.class);
    private final Map<Attribute, Double> currentPermanent = new EnumMap<>(Attribute.class);
    private final Map<Attribute, Double> currentTemporary = new EnumMap<>(Attribute.class);
    private final Map<String, Double> customDefaults = new HashMap<>();
    private final Map<String, Double> customCurrents = new HashMap<>();

    public double getDefaultPermanent(Attribute attribute) {
        return defaultPermanent.getOrDefault(attribute, 0.0);
    }

    public void setDefaultPermanent(Attribute attribute, double value) {
        defaultPermanent.put(attribute, value);
    }

    public double getDefaultTemporary(Attribute attribute) {
        return defaultTemporary.getOrDefault(attribute, 0.0);
    }

    public void setDefaultTemporary(Attribute attribute, double value) {
        defaultTemporary.put(attribute, value);
    }

    public double getCurrentPermanent(Attribute attribute) {
        return currentPermanent.getOrDefault(attribute, 0.0);
    }

    public void setCurrentPermanent(Attribute attribute, double value) {
        currentPermanent.put(attribute, value);
    }

    public double getCurrentTemporary(Attribute attribute) {
        return currentTemporary.getOrDefault(attribute, 0.0);
    }

    public void setCurrentTemporary(Attribute attribute, double value) {
        currentTemporary.put(attribute, value);
    }

    public double getCustomDefault(String key) {
        return customDefaults.getOrDefault(key, 0.0);
    }

    public void setCustomDefault(String key, double value) {
        customDefaults.put(key, value);
    }

    public double getCustomCurrent(String key) {
        return customCurrents.getOrDefault(key, 0.0);
    }

    public void setCustomCurrent(String key, double value) {
        customCurrents.put(key, value);
    }

    public Map<Attribute, Double> getDefaultPermanent() {
        return defaultPermanent;
    }

    public Map<Attribute, Double> getDefaultTemporary() {
        return defaultTemporary;
    }

    public Map<Attribute, Double> getCurrentPermanent() {
        return currentPermanent;
    }

    public Map<Attribute, Double> getCurrentTemporary() {
        return currentTemporary;
    }

    public Map<String, Double> getCustomDefaults() {
        return customDefaults;
    }

    public Map<String, Double> getCustomCurrents() {
        return customCurrents;
    }
}

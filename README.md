# AttributeUtils

AttributeUtils is a Minecraft 1.21.10 plugin that centralizes vanilla and custom attribute math so every plugin on your server shares the same numbers. It replaces ad-hoc modifier stacks with a predictable computation pipeline, offers hooks for retrieving staged values, and ships utilities for applying caps, handling vanilla baselines, and refreshing live entities.

## Why use AttributeUtils?
- **Unified baselines**: Register definitions for vanilla or custom attributes and let the plugin track global defaults alongside per-player overrides.
- **Deterministic math**: AttributeComputationEngine applies additives before multipliers in a fixed order for both default and current layers.
- **Rich integration hooks**: Access staged values, inject modifiers, or wire your own vanilla baseline suppliers without touching internal storage.

## Core API entrypoint: `AttributeFacade`
Use `AttributeFacade` as the single surface for registrations, reads, and writes:
1. **Register definitions** you care about (vanilla or custom). Each `AttributeDefinition` stores ids, baselines, caps, and multiplier applicability rules. `registerDefinition` normalizes ids and seeds a global `AttributeInstance` for storage.
2. **Register vanilla baselines** for dynamic attributes with `registerVanillaBaseline`. Provide a `VanillaAttributeSupplier` lambda that reports the live vanilla value (e.g., pulling armor from equipment or food level from Bukkit APIs). When omitted, defaults fall back to the definition’s `defaultCurrentValue`.
3. **Compute staged values** with `compute(String id, Player player)` to receive an `AttributeValueStages` snapshot (raw and post-modifier values for both default and current layers, already clamped to caps).
4. **Modify attributes** by calling `setGlobalModifier`, `setPlayerModifier`, or `setPlayerCapOverride`. Modifier keys must follow `[plugin].[key]` and are automatically bucketed into additive or multiplier groups for default/current and permanent/temporary scopes.
5. **React to removals** by registering an `AttributeRefreshListener` so live players/entities can be refreshed when modifiers are cleared.

```java
// Example: register a custom attribute and wire a vanilla baseline supplier
AttributeDefinition mana = new AttributeDefinition(
    "max_mana", "Max Mana", false,
    20.0, 20.0,
    new CapConfig(0, 200, Map.of()),
    MultiplierApplicability.allowAllMultipliers()
);
attributeFacade.registerDefinition(mana);
attributeFacade.registerVanillaBaseline("max_mana", player -> 20.0); // static baseline

// Apply a temporary current additive and retrieve staged values
ModifierEntry potionBuff = new ModifierEntry("mymod.mana_potion", 5.0, ModifierOperation.ADD, true, true, Set.of(), false);
attributeFacade.setPlayerModifier(player.getUniqueId(), "max_mana", potionBuff);
AttributeValueStages stages = attributeFacade.compute("max_mana", player);
```

## Computation pipeline
`AttributeComputationEngine` evaluates attributes in a deterministic sequence so integrations can target the right stage:
1. Start from the default baseline, clamp to caps, and apply default permanent additives, default temporary additives, default permanent multipliers, then default temporary multipliers.
2. For non-dynamic attributes, synchronize the current baseline with the computed default result so stored deltas remain meaningful.
3. Establish the current baseline (vanilla-aware for dynamic attributes) and clamp.
4. Apply current permanent additives, current temporary additives, current permanent multipliers, then current temporary multipliers, clamping after each stage.
5. Return the staged values (raw, after permanent, after all modifiers for both default and current).

## Working with modifier buckets
`AttributeInstance` stores one modifier map per scope: default/current × permanent/temporary × additive/multiplier. Adding a `ModifierEntry` automatically distributes it to the correct buckets and replaces any existing entry with the same key. Temporary modifiers can be purged globally or per-player to clear session-bound effects.

### Multiplier scoping
Each additive can opt into specific multiplier keys via `useMultiplierKeys` and `multiplierKeys`. When set, only the referenced multipliers apply to that additive; otherwise, all applicable multipliers in the stage are used. `MultiplierApplicability` on the definition also filters which multipliers participate at all.

## Cap management
`CapConfig` provides global min/max values plus optional overrides keyed by `capOverrideKey`. Use `setPlayerCapOverride` on the façade to clamp overrides to the attribute’s global minimum and persist the cap key for future computations. Caps are applied after every computation step to keep both baselines and modifier results within expected ranges.

## Vanilla resolver utilities
`AttributeUtilitiesPlugin` wires helper suppliers that read Bukkit attributes, food level, maximum air, or equipment-derived stats. When you register a vanilla baseline through the config or API, the plugin will:
- Resolve the target Bukkit attribute (with fallbacks for armor, toughness, knockback resistance, attack damage/speed, etc.).
- For dynamic attributes, prefer live vanilla values; for static ones, fall back to `defaultCurrentValue`.
- Cache attribute targets so entity handlers can reapply values after refreshes.

You can mirror this pattern by providing your own `VanillaAttributeSupplier` or by calling `attributeFacade.registerVanillaBaseline` with a custom resolver.

## Persistence and lifecycle hooks
`AttributePersistence` loads global data on enable, player data on join, and saves everything on disable. The plugin exposes `reloadAttributes` to flush current state, reload config/custom attributes, and reapply item and entity handlers. If you register an `AttributeRefreshListener`, modifier removals will trigger refresh callbacks for specific players or everyone, letting you reapply computed values to live entities.

## Configuration at a glance
- **Global definitions and caps**: configured in `config.yml` under `vanilla-attribute-defaults` and `global-attribute-caps` (definitions can also be registered programmatically).
- **Custom attributes**: YAML files in `custom-attributes/` (configurable) with fields for id, display name, dynamic flag, default baselines, caps, and multiplier applicability.
- **Plugin commands**: admin commands (e.g., `/attributes`, `/attributes entity`, `/attributes item`) are registered automatically to inspect or change values, spawn test entities, and generate items with embedded modifiers.

## Getting started
1. Drop the built jar into your server’s plugins folder and start the server to generate config files.
2. Define vanilla baselines and caps in `config.yml`, or add custom attributes in the custom attributes folder.
3. In your plugin, obtain the `AttributeFacade` instance (via the plugin’s getter or service registration) and register any additional attributes or vanilla suppliers you need.
4. Use the façade to set modifiers, cap overrides, and to compute staged values when your plugin needs current numbers for gameplay logic.

With these hooks and utilities, AttributeUtils becomes the authoritative source for all attribute calculations on your server.

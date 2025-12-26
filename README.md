# AttributeUtils

AttributeUtils is a Minecraft 1.21.10 plugin that centralizes vanilla and custom attribute math so every plugin on your server shares the same numbers. It exposes a minimal façade for registration and computation, while still letting hook plugins inject modifiers, caps, and vanilla baselines.

## What makes AttributeUtils different?
- **Deterministic pipeline** – Additives then multipliers, default layer before current layer, with clamping after every step so numbers stay predictable.
- **Vanilla-aware** – Optional suppliers fetch live Bukkit attribute values, food level, air, and equipment-driven stats before custom modifiers kick in.
- **Modifier safety** – Namespaced keys (`plugin.key`) prevent collisions and make it easy to replace or purge individual entries.
- **Refresh hooks** – A single listener surface re-applies computed values to online players whenever modifiers change.

## Quick start: registering and computing
```java
// Acquire the façade from your plugin on enable (AttributeUtilitiesPlugin exposes a getter)
AttributeFacade attributes = ...;

// 1) Register your attribute definition
AttributeDefinition mana = new AttributeDefinition(
    "max_mana", "Max Mana", false,
    20.0, 20.0,
    new CapConfig(0, 200, Map.of()),
    MultiplierApplicability.allowAllMultipliers(),
    ModifierOperation.ADD
);
attributes.registerDefinition(mana);

// 2) Wire a vanilla baseline (optional)
attributes.registerVanillaBaseline("max_mana", player -> 20.0);

// 3) Apply modifiers using namespaced keys
ModifierEntry potionBuff = new ModifierEntry(
    "mymod.mana_potion", 5.0, ModifierOperation.ADD, true, true, Set.of(), false);
attributes.setPlayerModifier(player.getUniqueId(), "max_mana", potionBuff);

// 4) Compute staged values for gameplay logic
AttributeValueStages stages = attributes.compute("max_mana", player);
```

### Using staged values
`AttributeValueStages` returns baselines and post-modifier totals for both layers. You can render tooltips from `rawDefault`/`defaultFinal` while consuming `currentFinal` for live combat calculations.

### Modifier lifecycles
Temporary modifiers are purged automatically when you call `purgeTemporary(playerId)` for disconnects or `purgeGlobalTemporary()` for server reloads. Caps can be overridden per player via `setPlayerCapOverride`, which also clamps to the global minimum before persisting.

## Plugin integration examples
- **Equipment-driven stats**: Register a `VanillaAttributeSupplier` that inspects armor or held items, then feed the computed baseline into the engine for consistent stacking.
- **Consumable buffs**: Add temporary additive or multiplier modifiers (with `ModifierOperation`) keyed to your plugin; remove them later by calling `removePlayerModifier`/`removeGlobalModifier` with the same key.
- **Cross-plugin synchronization**: Use `refreshAllAttributesForPlayer` after you change vanilla values (e.g., custom health systems) so any listening handlers reapply computed numbers.

## Hook surface: classes, fields, and methods worth knowing
These are the types most hook plugins interact with, along with why you would use them:

- **`AttributeFacade`** – Central API for registration, computation, and modifier management.
  - `registerDefinition(AttributeDefinition)` seeds an attribute and its global storage.【F:src/main/java/me/baddcamden/attributeutils/api/AttributeFacade.java†L53-L76】
  - `registerVanillaBaseline(String, VanillaAttributeSupplier)` provides live vanilla values for the computation pipeline.【F:src/main/java/me/baddcamden/attributeutils/api/AttributeFacade.java†L78-L93】
  - `compute(String, Player)` / `compute(String, UUID, Player)` return `AttributeValueStages` for rendering or gameplay.【F:src/main/java/me/baddcamden/attributeutils/api/AttributeFacade.java†L107-L149】
  - `setGlobalModifier` / `setPlayerModifier` add or replace modifier entries with refresh notifications.【F:src/main/java/me/baddcamden/attributeutils/api/AttributeFacade.java†L151-L208】
  - `setPlayerCapOverride` clamps and persists per-player caps.【F:src/main/java/me/baddcamden/attributeutils/api/AttributeFacade.java†L210-L243】
  - `removeGlobalModifier` / `removePlayerModifier` purge modifiers by key (useful for clearing consumables or session effects).【F:src/main/java/me/baddcamden/attributeutils/api/AttributeFacade.java†L245-L287】
  - `getGlobalInstances` / `getPlayerInstances` expose read-only state for diagnostics or UI.【F:src/main/java/me/baddcamden/attributeutils/api/AttributeFacade.java†L289-L315】
  - `refreshAllAttributesForPlayer` / `refreshAllAttributes` tell listeners to reapply computed values.【F:src/main/java/me/baddcamden/attributeutils/api/AttributeFacade.java†L317-L352】
  - `purgeTemporary(UUID)` / `purgeGlobalTemporary()` clear temporary buckets.【F:src/main/java/me/baddcamden/attributeutils/api/AttributeFacade.java†L388-L398】
  - `setAttributeRefreshListener(AttributeRefreshListener)` registers your callback for live entity updates.【F:src/main/java/me/baddcamden/attributeutils/api/AttributeFacade.java†L414-L446】

- **`AttributeDefinition`** – Immutable description of an attribute (id, name, dynamic flag, baselines, caps, multiplier rules, default operation). Use `newInstance()` to seed a fresh `AttributeInstance`.【F:src/main/java/me/baddcamden/attributeutils/model/AttributeDefinition.java†L10-L36】

- **`AttributeInstance`** – Mutable storage for baselines and modifier buckets. Key entry points for hook plugins inspecting state:
  - `getModifiers()` to view all registered entries keyed by normalized key.【F:src/main/java/me/baddcamden/attributeutils/model/AttributeInstance.java†L74-L120】
  - `getDefaultPermanentAdditives()` / `getCurrentPermanentAdditives()` (and their temporary/multiplier counterparts) expose bucket contents for debugging or exports.【F:src/main/java/me/baddcamden/attributeutils/model/AttributeInstance.java†L122-L188】
  - `addModifier(ModifierEntry)` distributes a modifier across relevant buckets; `removeModifier(String)` clears it from every bucket.【F:src/main/java/me/baddcamden/attributeutils/model/AttributeInstance.java†L190-L224】
  - Baseline helpers (`getBaseValue`, `setBaseValue`, `getCurrentBaseValue`, `setCurrentBaseValue`, `synchronizeCurrentBaseWithDefault`) let you adjust stored baselines before recomputing.【F:src/main/java/me/baddcamden/attributeutils/model/AttributeInstance.java†L52-L95】【F:src/main/java/me/baddcamden/attributeutils/model/AttributeInstance.java†L226-L240】

- **`ModifierEntry`** – Single modifier with key, value, operation (add/multiply), permanence, layer targeting, multiplier scoping, and whether to clamp to caps. Ideal for serializing buffs or items.【F:src/main/java/me/baddcamden/attributeutils/model/ModifierEntry.java†L1-L82】

- **`AttributeValueStages`** – Record capturing raw and post-modifier values for default/current layers; useful for tooltips or network packets.【F:src/main/java/me/baddcamden/attributeutils/model/AttributeValueStages.java†L1-L36】

- **`CapConfig`** – Global min/max values plus optional override map keyed by `capOverrideKey`; used by `setPlayerCapOverride` to clamp per-player caps.【F:src/main/java/me/baddcamden/attributeutils/model/CapConfig.java†L1-L34】

- **`MultiplierApplicability`** – Filters which multipliers apply to which additive buckets (allow all vs. allow lists/denylists).【F:src/main/java/me/baddcamden/attributeutils/model/MultiplierApplicability.java†L1-L57】

- **`VanillaAttributeSupplier`** – Functional interface for providing live vanilla baselines given a player. Register with `registerVanillaBaseline`.【F:src/main/java/me/baddcamden/attributeutils/api/VanillaAttributeSupplier.java†L1-L15】

- **`AttributeFacade.AttributeRefreshListener`** – Callback interface fired when modifiers are removed so you can reapply values to entities.【F:src/main/java/me/baddcamden/attributeutils/api/AttributeFacade.java†L418-L446】

With these hooks, AttributeUtils becomes the authoritative source for all attribute calculations on your server, letting your plugins agree on numbers while keeping integration simple.

# AttributeUtils Plugin Outline

AttributeUtils is a Minecraft 1.21.10 plugin that standardizes vanilla and custom player attributes for use by other plugins. It exposes consistent storage, computation, and API access for players, items, and entities.

## Purpose and scope
- Define a unified attribute system other plugins can hook into without relying on vanilla attribute modifiers.
- Normalize vanilla attributes and custom attributes, including additional maxima for hunger/oxygen and configurable custom attributes such as a Max Mana example.
- Provide admin tools, API access points, and data files for globals, players, items, and entities.

## Attribute model
- Tracked per attribute:
  - **Default value** (global baseline) and **current value** (vanilla value for dynamic attributes; otherwise equals the default).
  - **Default** and **current** additive maps (permanent, temporary) keyed as `[plugin].[key]`.
  - **Default** and **current** multiplier maps (permanent, temporary) keyed as `[plugin].[key]`.
- Additives decide multiplier participation:
  - If an additive’s boolean is **true**, it lists multiplier keys that apply; an empty list means no multipliers.
  - If **false**, all applicable multipliers apply. Default multipliers only affect default values before non-default modifiers.
- Key format is strictly `[plugin].[key]`; reject anything else to allow coarse-grained plugin-level targeting (e.g., `[plugin]`).
- Order of operations:
  - **Default total:** default value → default permanent add → default temporary add → default permanent mult → default temporary mult.
  - **Current total:** current value (vanilla dynamic base + default total) → current permanent add → current temporary add → current permanent mult → current temporary mult.
- Dynamic attributes retain vanilla inputs (e.g., armor, knockback resistance); non-dynamic attributes replace the vanilla value with the default value before computations.
- Changing the default value or its modifiers immediately shifts the stored current value by the delta between old and new defaults. Cache old defaults separately to support this adjustment.

## Persistence and lifecycle
- **Global defaults** (defaults, default modifiers, caps) live in `globalattributes.yml`; load on enable, save on disable.
- **Player-specific data** (current values, current modifiers, hunger/custom actual values) live in `[uuid].yml`; load on join, save on quit. Oxygen uses a direct max-air attribute (measured in ticks) and is not persisted as a meter.
- **Custom global attributes** reside in individual files under `CustomAttributes/`, including a sample `MaxMana` file that also appears in player files with both max mana and mana entries.
- Avoid runtime config I/O during normal operations; rely on in-memory structures after load.

## Vanilla and custom attributes covered
- Vanilla hooks include: Maximum Health, Movement Speed, Armor, Armor Toughness, Knockback Resistance, Attack Damage, Attack Speed, Block Interaction Range, Entity Interaction Range, Attack Reach, Jump Strength, Attack Knockback, Block Break Speed, Burning Time, Camera Distance, Explosion Knockback Resistance, Fall Damage Multiplier, Flying Speed, Gravity, Luck, Max Absorption, Mining Efficiency, Movement Efficiency, Scale, Sneaking Speed, Step Height, Submerged Mining Speed, Sweeping Damage Ratio, Water Movement Efficiency, Exhaustion Rate, Damage Reduction, Regeneration Rate.
- Additional special stats: Max Hunger, Max Oxygen (defaults: 20 and 10 bubbles = 300 ticks, configurable), and custom attributes such as Max Mana (toggleable in config).
- All current values start from their vanilla values (for dynamic stats) or default values (for non-dynamic stats) before AttributeUtils computations apply.

## Caps
- Every attribute has a global min/max cap.
- Each attribute entry (player/entity/item) can optionally override caps; when the override flag is false, the global cap applies.

## API access patterns
- Provide getters for:
  - Default/current raw values before any modifiers.
  - Default/current values after permanent modifiers only (no temps).
  - Default/current values after all modifiers.
  - Vanilla interfaces for raw vanilla inputs (e.g., pre-computation armor values).
- Allow retrieval of current or default values before computations, after permanent-only computations, and after full computations. Support both default and current views to enable hook plugins to choose their stage.

## Special handling: hunger, oxygen, and custom stats
- Max hunger is a float (default 20) with a corresponding actual value stored per player. Max oxygen is measured directly in air ticks and applied to Bukkit's maximum air instead of being tracked as a meter.
- Visual bars show vanilla hunger UI as a percentage of the custom maximum: `20 * (real / currentMax)`, rounded up for the bar. Hunger gain/lose events (FoodLevelChangeEvent) operate on the custom values; oxygen changes clamp to the max-air ticks without a separate meter.
- Custom attributes can declare labels for actual-value representations; they are computational only, with hook plugins implementing gameplay effects.

## Items and modifiers
- Items can carry temporary additives/multipliers in NBT. Global default values for items live under `CustomItemAttributes/`. If no defaults exist, no default adjustments apply.
- Trigger contexts per modifier: inventory, held, use (right-click), attack (left-click), full swing, crit, equipped (armor), off-hand, hotbar, or custom events (custom handled by hook plugins).

## Entity attributes
- Entities store attribute data in NBT similar to player-specific files. Item modifiers propagate when trigger criteria are met.
- Entities and custom entities can have global default files. Custom entities use a plugin-specific NBT tag for their ID; when present, only that custom default applies (the vanilla type is ignored).

## Caps and storage per scope
- Global caps exist for each attribute.
- Player, item, and entity entries include a boolean to decide whether to use global caps or custom min/max caps for that entry.

## Commands
- Admin commands must:
  - Edit any player attribute (add/remove permanent or temporary modifiers with keys).
  - Adjust global attribute settings and caps.
  - Create items with custom attributes.
  - Spawn a test zombie (or other entities) with an arbitrary set of attributes in a single command (supporting multiple attributes per invocation).

## Example workflow
- Lifesteal plugin: permanent additives keyed `lifesteal.pvp` adjust max health on kills/deaths; if the post-permanent (pre-temp) max health hits zero, the player is banned. Temporary buffs (e.g., `lifesteal.potion` or timed global events) are ignored for the ban check.
- Temporary effect example: “Adrenaline” increases health via a temporary additive; a global timed event adds health temporarily as well.
- Item example: “Assassins Knife” increases movement efficiency and attack damage while sneaking and outside line of sight (custom events by hook plugin).
- Entity example: Custom zombie `ZombieCaptain` has global defaults of max health 30 and attack damage 15, while vanilla zombies keep their standard defaults.

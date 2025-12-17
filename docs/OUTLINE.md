# AttributeUtils Plugin Outline

This document summarizes the planned AttributeUtils plugin for Minecraft 1.21.10. The plugin standardizes vanilla and custom attributes for players, items, and entities so other plugins can query and modify them consistently.

## Attribute structure
- Each attribute tracks the following values:
  - **Default value** and **current value** (current mirrors vanilla value for dynamic attributes; otherwise it equals the default).
  - **Permanent** and **temporary** additive maps, and **permanent** and **temporary** multiplier maps for both default and current values.
- Additive/multiplier maps are keyed as `[plugin].[key]`; reject other formats. Additives can opt into specific multipliers; otherwise all apply. Default multipliers only affect default values before non-default modifiers.
- Order of operations:
  - **Default total:** default value → default permanent add → default temporary add → default permanent mult → default temporary mult.
  - **Current total:** current value (vanilla base + default total) → current permanent add → current temporary add → current permanent mult → current temporary mult.
- Changing a default value (including its modifiers) immediately adjusts the stored current value by the delta between the new and old defaults. Old defaults are cached separately.

## Persistence and lifecycle
- **Global defaults** (including default modifiers and caps) are stored in `globalattributes.yml` and loaded on enable, saved on disable.
- **Player-specific data** (current values and current modifiers, plus hunger/oxygen/custom actual values) live in `[uuid].yml`, loaded on join and saved on quit.
- Custom global attributes live in individual files within `CustomAttributes/`, e.g., a `MaxMana` example that also appears in player files with `max mana` and `mana` entries.
- Avoid runtime config I/O; rely on in-memory structures after load.

## Caps
- Every attribute has global min/max caps. Each player/entity/item attribute entry can optionally override caps; when the override flag is false, the global cap applies.

## API expectations
- Provide getters for:
  - Default/current raw values before modifiers.
  - Values after permanent modifiers (without temps).
  - Values after all modifiers.
  - Vanilla interfaces (e.g., pre-computation armor values).
- Expose vanilla/dynamic attribute inputs for current values (e.g., armor from equipment, damage reduction from protection) while applying plugin computations instead of vanilla formulas where needed.

## Custom attributes and special stats
- Support custom max hunger and max oxygen with defaults of 20 and 10 respectively, plus current hunger/oxygen values tracked per player.
- Display vanilla hunger/oxygen bars as percentage of the custom maxima: `20 * (real / currentMax)` rounded up.
- Custom attributes can declare actual-value labels and are purely computational hooks for other plugins.

## Item-based modifiers
- Items can carry temporary modifiers in NBT under `CustomItemAttributes/` defaults. If no defaults exist, no default adjustments apply.
- Modifiers can trigger based on presence/use context: inventory, held, use (right-click), attack (left-click), full swing, crit, equipped (armor), off-hand, hotbar, or custom events (handled by hook plugins).

## Entity attributes
- Entities can hold attribute data similar to player files, stored in NBT. Items applied to entities propagate their modifiers when criteria are met.
- Entities and custom entities can have global default files; custom entities use a plugin-specific NBT ID to select their defaults instead of the vanilla type.

## Commands
- Provide admin commands to:
  - Edit any player attribute (including adding permanent/temporary modifiers by key).
  - Adjust global attribute settings and caps.
  - Create items with custom attributes.
  - Spawn test entities (e.g., a zombie) with arbitrary attribute sets in one command.

## Example workflow
- Lifesteal plugin changes max health via permanent additives keyed `lifesteal.pvp` on kills/deaths and bans players whose post-permanent (pre-temp) max health hits zero.
- Temporary buffs like `lifesteal.potion` or timed global events use temporary additives and are ignored for ban checks.
- A weapon “Assassins Knife” applies movement efficiency and attack damage modifiers while sneaking and unseen (custom events).
- Custom zombie `ZombieCaptain` uses global defaults for max health (30) and attack damage (15) while vanilla zombies retain standard defaults.

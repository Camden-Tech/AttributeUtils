# AttributeUtils Plugin Outline

AttributeUtils is a Minecraft 1.21.10 plugin that standardizes vanilla and custom attributes across players, items, and entities so that other plugins can share one coherent attribute system. It avoids vanilla attribute modifiers whenever possible, opting for explicit data models, predictable math, and rich API hooks that expose every stage of computation.

## Purpose and scope
- Provide a unified attribute layer for vanilla and custom attributes, letting hook plugins reason about the same numbers even when vanilla logic would normally drift or conflict.
- Ship a 1.21.10-ready plugin named **AttributeUtils** that focuses on standardizing player stats, while also covering items, entities, and custom attributes such as an example **Max Mana**.
- Offer extensive documentation, examples, and admin tooling so server operators can tweak defaults, caps, and modifiers without touching code.

## Attribute model (values and maps)
- Each attribute tracks **Default Value** and **Current Value**:
  - **Default Value** is the global baseline (e.g., vanilla player max health default 20.0). It is defined in global data and is **not** the vanilla runtime value unless the attribute is non-dynamic.
  - **Current Value** is the runtime baseline before AttributeUtils computations. For dynamic stats it equals the live vanilla value; for non-dynamic stats it equals the default value. It rarely changes directly and mainly serves as the vanilla-facing storage that computations read from.
- For both default and current scopes, maintain four hash maps keyed strictly as `[plugin].[key]`:
  - Permanent additive map
  - Temporary additive map
  - Permanent multiplier map
  - Temporary multiplier map
- All keys **must** match `[plugin].[key]`; reject anything else. This enables coarse-grained addressing (e.g., `lifesteal` affects all `lifesteal.*`, or `lifesteal.pvp` for a specific modifier).
- Additive multiplier participation flag:
  - Every additive entry carries a boolean. If **true**, the additive lists specific multiplier keys that apply; an empty list means **no** multipliers touch it.
  - If **false**, **all** applicable multipliers apply. Default multipliers only affect default values and do so **before** non-default additives are applied.

## Order of operations (with detailed stages)
- **Default Total (global perspective)**
  1) Start with the default value.
  2) Apply default permanent additives.
  3) Apply default temporary additives.
  4) Apply default permanent multipliers.
  5) Apply default temporary multipliers.
- **Current Total (player/item/entity perspective)**
  1) Start with current value (for dynamic stats, the live vanilla value; otherwise the computed default total replaces vanilla).
  2) Apply current permanent additives.
  3) Apply current temporary additives.
  4) Apply current permanent multipliers.
  5) Apply current temporary multipliers.
- Changing defaults affects currents immediately:
  - Store “old default” snapshots (e.g., in a cache folder not meant for config editing).
  - When defaults shift, compute the delta between new and old defaults and add that delta to the stored current value **before** non-default modifiers run.
  - This keeps dynamic and non-dynamic attributes in sync while honoring vanilla interfaces.
- Example (Max Health):
  - Default value 20.0, default perm add `event.global:+4`, default temp mult `festival.buff:*1.1`.
  - Default total = `((20 + 4) * 1.1)` = 26.4.
  - Current value starts at vanilla 20.0 (dynamic example) then inherits default total (26.4) before non-default modifiers.
  - Add current perm add `lifesteal.pvp:+2`, temp add `adrenaline:+3`, perm mult `setbonus:*1.05`, temp mult `potion:*1.2`.
  - Current total = `(((26.4 + 2 + 3) * 1.05) * 1.2)` = 38.808. Caps may clamp this later.

## Dynamic vs non-dynamic attributes
- **Dynamic attributes** keep vanilla variability (e.g., armor changing with equipment, knockback resistance modified by netherite). The current value reflects the live vanilla figure before AttributeUtils modifiers.
- **Non-dynamic attributes** have static vanilla baselines (e.g., maximum health, movement speed). The default value replaces vanilla outright before computations.
- Hook plugins can still retrieve pure vanilla inputs via API (see API section), enabling comparisons or custom logic.

## Persistence, files, and lifecycle
- Global data lives in `globalattributes.yml` and includes default values, default modifiers, global caps, and any default-only metadata. Load onEnable; save onDisable.
- Player-specific data lives in `[uuid].yml` and includes current values, current modifiers, per-player caps, and any “actual value” fields for custom stats (e.g., real hunger). Load on player join; save on player quit.
- Custom global attributes sit in `CustomAttributes/` as separate files named after the attribute (e.g., `MaxMana.yml`). Each custom attribute can specify:
  - Whether it is enabled.
  - Default value and default modifier maps.
  - Labels for any “actual value” representations that should appear in player files.
  - Caps and override settings.
- Old defaults are cached in an internal location (non-configurable) so differences can be added to current values when defaults change.
- Aim for no runtime file I/O during normal play. Load into static collections on enable/join and flush to disk only on disable/quit.

## Vanilla and custom attributes covered
- Vanilla hooks: Maximum Health, Movement Speed, Armor, Armor Toughness, Knockback Resistance, Attack Damage, Attack Speed, Block Interaction Range, Entity Interaction Range, Attack Reach, Jump Strength, Attack Knockback, Block Break Speed, Burning Time, Camera Distance, Explosion Knockback Resistance, Fall Damage Multiplier, Flying Speed, Gravity, Luck, Max Absorption, Mining Efficiency, Movement Efficiency, Scale, Sneaking Speed, Step Height, Submerged Mining Speed, Sweeping Damage Ratio, Water Movement Efficiency, Exhaustion Rate, Damage Reduction, Regeneration Rate.
- Customizable stat toggles include extras like **Max Mana** (provided as a detailed sample).
- All current values begin as their real-world values (vanilla for dynamic, default for non-dynamic) **before** AttributeUtils computations kick in.

## Hunger and oxygen reworks (custom maxima + real values)
- Introduce configurable max hunger and max oxygen attributes:
  - Max Hunger: any float; default 20.
  - Max Oxygen: any float; default 10.
- Player files track both the **max** values and the **actual current** hunger/oxygen values as separate entries alongside other attributes.
- UI bar mapping keeps the vanilla bar as a percentage of real values:
  - Rendered hunger = `vanillaBarMax(20) * (realHunger / retrievedCurrentMaxHunger)`, rounded up with `Math.ceil`.
  - Example: real hunger 6, retrieved max hunger 26 → displayed = `ceil(20 * (6 / 26))`.
  - Oxygen uses the same formula with its own max.
- Hunger changes listen to `FoodLevelChangeEvent`; oxygen listens to `EntityAirChangeEvent`. Vanilla bars become purely representational percentages of the true values maintained by AttributeUtils.
- Custom attributes with “actual value” representations follow the same player-file pattern: a named max and a named current value stored together, but without vanilla UI remapping (they are computational only).

## Caps (global and per entry)
- Every attribute has global minimum and maximum caps defined in global data.
- Each player/item/entity entry can set a boolean flag:
  - If **false**, use the global caps.
  - If **true**, define custom min/max caps that override the global values.
- Example: global damage reduction cap 80%, but player `BaldyGate` enables per-entity caps and sets max to 75%, so their effective cap is 75%.

## API access patterns (multi-stage getters)
- Provide explicit getters for both **default** and **current** scopes at multiple stages:
  1) Raw value before any modifiers (pure default or pure current).
  2) Value after permanent modifiers only (no temporary modifiers).
  3) Value after all modifiers (permanent + temporary).
  4) Vanilla interfaces for raw vanilla values (e.g., armor from equipment before AttributeUtils or default replacements).
- Hook plugins can request any stage to drive logic such as ban checks, UI overlays, or effect calculations. For instance, a lifesteal check might use “current after permanent, before temp,” while a UI might display “current after all modifiers.”

## Items and modifier triggers
- Items store attribute modifiers in NBT mirroring player-specific structures: permanent/temporary additives and multipliers keyed by `[plugin].[key]`, plus cap overrides.
- Global item defaults live in `CustomItemAttributes/`. If an item lacks entries there, no default adjustments apply to that item.
- Trigger contexts per modifier include: inventory presence, held, use/right-click, attack/left-click, full swing, critical hit, equipped in armor slot, off-hand, hotbar presence, and custom events (custom handled by hook plugins). The plugin handles all non-custom triggers internally.

## Entity attributes and custom entities
- Entities use NBT-backed storage similar to player files; item modifiers propagate to entities when trigger criteria are satisfied.
- Entities can have global default files, and **custom entities** can too. Custom entities are identified via an AttributeUtils-specific NBT tag for their custom ID.
- If an entity has a custom ID, only the custom entity defaults apply; ignore the vanilla type defaults in that case (e.g., a `BossZombie` with custom ID ignores standard Zombie defaults).
- API helpers let other plugins set or query an entity’s custom ID NBT so AttributeUtils knows which defaults to apply.

## Commands and admin tooling
- Provide commands that allow administrators to:
  - Inspect and modify any player attribute (add/remove permanent or temporary additives/multipliers with explicit keys).
  - Change global attribute settings, defaults, caps, and toggles for custom attributes.
  - Generate items with custom attributes embedded in NBT, specifying trigger contexts and modifier keys/values.
  - Spawn test entities (e.g., a zombie) with arbitrary attributes in a single invocation, supporting multiple attribute entries per command call.

## Example-heavy scenarios
- **Lifesteal workflow**: On player kill, add `lifesteal.pvp:+2` to max health permanent additives; on death, add `lifesteal.pvp:-2`. Ban logic checks “current after permanent, before temporary” and bans if it reaches 0. Temporary buffs like `lifesteal.potion:+3` or timed global events do **not** prevent bans because they are temporary.
- **Adrenaline effect**: Temporary additive `adrenaline:+3` to max health, keyed per player and fully controlled by the hook plugin’s timer.
- **Event buff**: A server-wide festival adds default temporary additive `festival.buff:+4` and default temporary multiplier `festival.buff:*1.1`, raising the baseline for everyone until the event expires.
- **Item example—Assassins Knife**: Adds movement efficiency `+0.8` and attack damage `+15` while sneaking and out of line of sight. Uses custom trigger events supplied by the hook plugin; modifiers are stored in NBT with keys like `assassinsknife.sneak`.
- **Entity example—ZombieCaptain**: Custom zombie with global defaults max health 30 and attack damage 15. A normal Zombie still keeps vanilla defaults. If a `BossZombie` custom entity ID is set via NBT, only the `BossZombie` defaults apply.
- **Custom attribute example—Max Mana**: A `CustomAttributes/MaxMana.yml` file defines default max mana, default modifiers, caps, and an “actual value” label (e.g., `mana`) that appears in player files. Player files store both `max_mana` (current cap) and `mana` (current resource). Hook plugins spend/restore mana but rely on AttributeUtils for all computations and caps.

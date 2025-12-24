package me.baddcamden.attributeutils.command;

import me.baddcamden.attributeutils.AttributeUtilitiesPlugin;
import me.baddcamden.attributeutils.api.AttributeFacade;
import me.baddcamden.attributeutils.handler.entity.EntityAttributeHandler;
import me.baddcamden.attributeutils.handler.item.ItemAttributeHandler;
import me.baddcamden.attributeutils.model.AttributeDefinition;
import me.baddcamden.attributeutils.model.ModifierEntry;
import me.baddcamden.attributeutils.model.ModifierOperation;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Zombie;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

/**
 * Command that spawns an attribute-heavy zombie horde for quick sandbox testing. Each equipped item
 * is decorated with randomized attribute definitions, and matching modifier entries are applied
 * directly to the spawned entities so the attribute computation pipeline mirrors real gameplay
 * interactions for gear and direct entity modifiers alike.
 */
public class TestHordeCommand implements CommandExecutor {

    private static final int MIN_ZOMBIES = 10;
    private static final int MAX_ZOMBIES = 20;
    private static final int MIN_ITEM_ATTRIBUTES = 1;
    private static final int MAX_ITEM_ATTRIBUTES = 3;
    private static final double MIN_ADDITIVE = 0.01d;
    private static final double MAX_ADDITIVE = 1.5d;
    private static final double MIN_MULTIPLIER = 1.01d;
    private static final double MAX_MULTIPLIER = 1.5d;

    private final AttributeUtilitiesPlugin plugin;
    private final AttributeFacade attributeFacade;
    private final ItemAttributeHandler itemAttributeHandler;
    private final EntityAttributeHandler entityAttributeHandler;
    private final CommandMessages messages;

    /**
     * Creates a new sandbox horde command used for validating the attribute pipeline end-to-end.
     *
     * @param plugin                 owning plugin for scheduling and logging.
     * @param attributeFacade        facade for attribute definitions and modifier management.
     * @param itemAttributeHandler   builder that decorates items with attribute data.
     * @param entityAttributeHandler handler used to apply vanilla attributes after modifiers are set.
     */
    public TestHordeCommand(AttributeUtilitiesPlugin plugin,
                            AttributeFacade attributeFacade,
                            ItemAttributeHandler itemAttributeHandler,
                            EntityAttributeHandler entityAttributeHandler) {
        this.plugin = plugin;
        this.attributeFacade = attributeFacade;
        this.itemAttributeHandler = itemAttributeHandler;
        this.entityAttributeHandler = entityAttributeHandler;
        this.messages = new CommandMessages(plugin);
    }

    /**
     * Spawns a random number of attributed zombies around the invoking player, optionally running in dry-run mode to
     * preview the spawn count. Attribute rolls are derived from registered definitions and applied both to equipped
     * items and directly to the entities so the computation pipeline can be exercised.
     */
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(messages.format(
                    "messages.test-horde-command.invalid-sender",
                    ChatColor.RED + "Only players can run this sandbox horde test."));
            return true;
        }

        if (!player.isOp() && !player.hasPermission("attributeutils.command.testhorde")) {
            sender.sendMessage(messages.format(
                    "messages.test-horde-command.no-permission",
                    ChatColor.RED + "You do not have permission to spawn a test horde."));
            return true;
        }

        List<AttributeDefinition> definitions = attributeFacade.getDefinitions().stream().toList();
        if (definitions.isEmpty()) {
            sender.sendMessage(messages.format(
                    "messages.test-horde-command.no-attributes",
                    ChatColor.RED + "No attributes are registered; cannot build randomized gear."));
            return true;
        }

        // Accept a lightweight "dryrun" flag for quick previewing without world impact.
        boolean dryRun = args.length > 0 && "dryrun".equalsIgnoreCase(args[0]);
        int zombieCount = ThreadLocalRandom.current().nextInt(MIN_ZOMBIES, MAX_ZOMBIES + 1);
        if (dryRun) {
            sender.sendMessage(messages.format(
                    "messages.test-horde-command.dry-run",
                    buildPreviewPlaceholders(zombieCount),
                    ChatColor.YELLOW + "Dry run: would spawn {count} zombies with randomized gear."));
            return true;
        }

        List<String> summaries = new ArrayList<>();

        for (int index = 0; index < zombieCount; index++) {
            try {
                // Spawn each zombie slightly offset from the player to avoid crowding at a single block.
                Zombie zombie = player.getWorld().spawn(adjustedLocation(player.getLocation()), Zombie.class);
                HordeLoadout loadout = decorateZombie(zombie, definitions);
                applyVanillaTouches(zombie, loadout.touchedAttributes());
                summaries.add(loadout.summary());
            } catch (Exception ex) {
                plugin.getLogger().warning("Failed to spawn test horde zombie: " + ex.getMessage());
                sender.sendMessage(messages.format(
                        "messages.test-horde-command.spawn-failed",
                        ChatColor.RED + "A zombie failed to spawn or equip attributes: " + ex.getMessage()));
            }
        }

        sender.sendMessage(messages.format(
                "messages.test-horde-command.spawned",
                buildSpawnPlaceholders(zombieCount, summaries),
                ChatColor.GREEN + "Spawned {count} zombies with randomized attribute gear."));
        return true;
    }

    /**
     * Offsets a location randomly on the X/Z plane to spread spawned zombies around the player.
     */
    private Location adjustedLocation(Location origin) {
        if (origin == null) {
            throw new IllegalArgumentException("Missing player location for horde spawn");
        }

        double offsetX = ThreadLocalRandom.current().nextDouble(-4.0d, 4.0d);
        double offsetZ = ThreadLocalRandom.current().nextDouble(-4.0d, 4.0d);
        return origin.clone().add(offsetX, 0, offsetZ);
    }

    /**
     * Builds gear and modifiers for a single zombie, wiring attributes into armor, weapon, and direct modifiers while
     * tracking which attributes were touched for later vanilla synchronization.
     */
    private HordeLoadout decorateZombie(Zombie zombie, List<AttributeDefinition> definitions) {
        List<AttributedItem> attributedItems = new ArrayList<>();
        attributedItems.add(buildAttributedItem(Material.LEATHER_HELMET, definitions));
        attributedItems.add(buildAttributedItem(Material.LEATHER_CHESTPLATE, definitions));
        attributedItems.add(buildAttributedItem(Material.LEATHER_LEGGINGS, definitions));
        attributedItems.add(buildAttributedItem(Material.LEATHER_BOOTS, definitions));
        attributedItems.add(buildAttributedItem(Material.WOODEN_SWORD, definitions));

        EntityEquipment equipment = zombie.getEquipment();
        if (equipment == null) {
            throw new IllegalStateException("Zombie has no equipment holder");
        }

        applyEquipment(equipment, attributedItems);

        Set<String> touchedAttributes = new HashSet<>();
        for (AttributedItem attributedItem : attributedItems) {
            // Apply modifiers directly to the entity so behavior mirrors equipped items in combat calculations.
            applyModifiers(zombie, attributedItem, touchedAttributes);
        }

        return new HordeLoadout(touchedAttributes, summarize(attributedItems));
    }

    /**
     * Equips the provided attributed items onto the zombie and removes drop chances to keep the sandbox clean.
     */
    private void applyEquipment(EntityEquipment equipment, List<AttributedItem> attributedItems) {
        for (AttributedItem attributedItem : attributedItems) {
            ItemStack itemStack = attributedItem.item();
            if (itemStack == null) {
                continue;
            }

            switch (itemStack.getType()) {
                case LEATHER_HELMET -> equipment.setHelmet(itemStack);
                case LEATHER_CHESTPLATE -> equipment.setChestplate(itemStack);
                case LEATHER_LEGGINGS -> equipment.setLeggings(itemStack);
                case LEATHER_BOOTS -> equipment.setBoots(itemStack);
                case WOODEN_SWORD -> equipment.setItemInMainHand(itemStack);
                default -> {
                }
            }
        }

        equipment.setHelmetDropChance(0.0f);
        equipment.setChestplateDropChance(0.0f);
        equipment.setLeggingsDropChance(0.0f);
        equipment.setBootsDropChance(0.0f);
        equipment.setItemInMainHandDropChance(0.0f);
    }

    /**
     * Writes modifier entries from an attributed item directly to the entity and records touched attribute ids.
     */
    private void applyModifiers(LivingEntity entity, AttributedItem attributedItem, Set<String> touchedAttributes) {
        for (AttributeRoll roll : attributedItem.rolls()) {
            ModifierEntry entry = new ModifierEntry(
                    buildModifierKey(entity, attributedItem, roll),
                    roll.operation(),
                    roll.amount(),
                    true,
                    false,
                    true,
                    false,
                    Set.of());

            //VAGUE/IMPROVEMENT NEEDED Using player-scoped modifier storage for mobs may not be intentional; confirm API expectations.
            attributeFacade.setPlayerModifier(entity.getUniqueId(), roll.attributeId(), entry);
            touchedAttributes.add(roll.attributeId());
        }
    }

    /**
     * Builds a unique modifier key for a generated item roll using the entity id and material.
     */
    private String buildModifierKey(LivingEntity entity, AttributedItem attributedItem, AttributeRoll roll) {
        String materialKey = attributedItem.item().getType().name().toLowerCase(Locale.ROOT);
        return "attributeutils.testhorde." + entity.getUniqueId() + "." + materialKey + "." + roll.attributeId();
    }

    /**
     * Invokes vanilla attribute application for every attribute mutated during horde generation.
     */
    private void applyVanillaTouches(LivingEntity entity, Set<String> touchedAttributes) {
        if (touchedAttributes.isEmpty()) {
            return;
        }

        for (String attributeId : touchedAttributes) {
            entityAttributeHandler.applyVanillaAttribute(entity, attributeId);
        }
    }

    /**
     * Builds an attributed item with randomized rolls, throwing when the material cannot hold attribute data so callers
     * can surface useful error messages.
     */
    private AttributedItem buildAttributedItem(Material material, List<AttributeDefinition> definitions) {
        List<AttributeRoll> rolls = randomRolls(definitions);
        List<CommandParsingUtils.AttributeDefinition> commandDefinitions = rolls.stream()
                .map(roll -> new CommandParsingUtils.AttributeDefinition(roll.key(), roll.amount(), null, null))
                .toList();

        try {
            ItemAttributeHandler.ItemBuildResult result = itemAttributeHandler.buildAttributeItem(material, commandDefinitions);
            return new AttributedItem(result.itemStack(), rolls);
        } catch (IllegalArgumentException ex) {
            throw new IllegalStateException("Failed to build attribute item for " + material + ": " + ex.getMessage(), ex);
        }
    }

    /**
     * Generates a random set of attribute rolls for an item, choosing additive or multiplicative operations and amounts
     * within predefined ranges.
     */
    private List<AttributeRoll> randomRolls(List<AttributeDefinition> definitions) {
        int rollCount = ThreadLocalRandom.current().nextInt(MIN_ITEM_ATTRIBUTES, MAX_ITEM_ATTRIBUTES + 1);
        List<AttributeRoll> rolls = new ArrayList<>();
        for (int index = 0; index < rollCount; index++) {
            AttributeDefinition definition = definitions.get(ThreadLocalRandom.current().nextInt(definitions.size()));
            ModifierOperation operation = ThreadLocalRandom.current().nextBoolean() ? ModifierOperation.ADD : ModifierOperation.MULTIPLY;
            double amount = operation == ModifierOperation.ADD
                    ? ThreadLocalRandom.current().nextDouble(MIN_ADDITIVE, MAX_ADDITIVE)
                    : ThreadLocalRandom.current().nextDouble(MIN_MULTIPLIER, MAX_MULTIPLIER);

            rolls.add(new AttributeRoll(definition.id(), resolveKey(definition.id()), operation, amount));
        }
        return rolls;
    }

    /**
     * Resolves a namespaced attribute key from an attribute id, using the plugin namespace when one is missing.
     */
    private CommandParsingUtils.NamespacedAttributeKey resolveKey(String attributeId) {
        String normalized = Objects.requireNonNull(attributeId, "attributeId").toLowerCase(Locale.ROOT);
        if (normalized.contains(".")) {
            String[] segments = normalized.split("\\.", 2);
            return new CommandParsingUtils.NamespacedAttributeKey(segments[0], segments[1]);
        }
        return new CommandParsingUtils.NamespacedAttributeKey(plugin.getName().toLowerCase(Locale.ROOT), normalized);
    }

    /**
     * Summarizes how many rolls were applied to each item type for inclusion in the success message.
     */
    private String summarize(List<AttributedItem> items) {
        return items.stream()
                .map(item -> item.item().getType().name() + "=" + item.rolls().size())
                .collect(Collectors.joining(", "));
    }

    /**
     * Builds placeholder map for a dry-run preview message.
     */
    private Map<String, String> buildPreviewPlaceholders(int zombieCount) {
        return Map.of("count", Integer.toString(zombieCount));
    }

    /**
     * Builds placeholder map for the spawn completion message containing count and item summaries.
     */
    private Map<String, String> buildSpawnPlaceholders(int zombieCount, List<String> summaries) {
        return Map.of(
                "count", Integer.toString(zombieCount),
                "summary", String.join("; ", summaries)
        );
    }

    /**
     * Immutable value object describing a randomized attribute roll applied to an item and its owning entity.
     *
     * @param attributeId identifier of the attribute whose modifier is being applied.
     * @param key         namespaced key derived from the id for item serialization.
     * @param operation   additive or multiplicative operation selected for the roll.
     * @param amount      magnitude of the modifier according to the chosen operation.
     */
    private record AttributeRoll(String attributeId,
                                 CommandParsingUtils.NamespacedAttributeKey key,
                                 ModifierOperation operation,
                                 double amount) {
    }

    /**
     * Captures an item generated for the horde along with the attribute rolls used to decorate it.
     *
     * @param item  fully built item stack equipped onto the zombie.
     * @param rolls collection of rolls written both to the item and to the entity modifiers.
     */
    private record AttributedItem(ItemStack item, List<AttributeRoll> rolls) {
    }

    /**
     * Represents the result of generating gear and modifiers for a single zombie for later reporting.
     *
     * @param touchedAttributes attribute ids that were modified on the entity.
     * @param summary           textual summary of roll counts per item for messaging.
     */
    private record HordeLoadout(Set<String> touchedAttributes, String summary) {
    }
}

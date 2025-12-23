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
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

/**
 * Spawns an attribute-heavy zombie horde for quick sandbox testing. Each equipped item is decorated
 * with randomized attribute definitions, and matching modifier entries are applied directly to the
 * spawned entities so the attribute computation pipeline mirrors real gameplay interactions.
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

    private Location adjustedLocation(Location origin) {
        if (origin == null) {
            throw new IllegalArgumentException("Missing player location for horde spawn");
        }

        double offsetX = ThreadLocalRandom.current().nextDouble(-4.0d, 4.0d);
        double offsetZ = ThreadLocalRandom.current().nextDouble(-4.0d, 4.0d);
        return origin.clone().add(offsetX, 0, offsetZ);
    }

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
            applyModifiers(zombie, attributedItem, touchedAttributes);
        }

        return new HordeLoadout(touchedAttributes, summarize(attributedItems));
    }

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

            attributeFacade.setPlayerModifier(entity.getUniqueId(), roll.attributeId(), entry);
            touchedAttributes.add(roll.attributeId());
        }
    }

    private String buildModifierKey(LivingEntity entity, AttributedItem attributedItem, AttributeRoll roll) {
        String materialKey = attributedItem.item().getType().name().toLowerCase(Locale.ROOT);
        return "attributeutils.testhorde." + entity.getUniqueId() + "." + materialKey + "." + roll.attributeId();
    }

    private void applyVanillaTouches(LivingEntity entity, Set<String> touchedAttributes) {
        if (touchedAttributes.isEmpty()) {
            return;
        }

        for (String attributeId : touchedAttributes) {
            entityAttributeHandler.applyVanillaAttribute(entity, attributeId);
        }
    }

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

    private CommandParsingUtils.NamespacedAttributeKey resolveKey(String attributeId) {
        String normalized = Objects.requireNonNull(attributeId, "attributeId").toLowerCase(Locale.ROOT);
        if (normalized.contains(".")) {
            String[] segments = normalized.split("\\.", 2);
            return new CommandParsingUtils.NamespacedAttributeKey(segments[0], segments[1]);
        }
        return new CommandParsingUtils.NamespacedAttributeKey(plugin.getName().toLowerCase(Locale.ROOT), normalized);
    }

    private String summarize(List<AttributedItem> items) {
        return items.stream()
                .map(item -> item.item().getType().name() + "=" + item.rolls().size())
                .collect(Collectors.joining(", "));
    }

    private java.util.Map<String, String> buildPreviewPlaceholders(int zombieCount) {
        return java.util.Map.of("count", Integer.toString(zombieCount));
    }

    private java.util.Map<String, String> buildSpawnPlaceholders(int zombieCount, List<String> summaries) {
        return java.util.Map.of(
                "count", Integer.toString(zombieCount),
                "summary", String.join("; ", summaries)
        );
    }

    private record AttributeRoll(String attributeId,
                                 CommandParsingUtils.NamespacedAttributeKey key,
                                 ModifierOperation operation,
                                 double amount) {
    }

    private record AttributedItem(ItemStack item, List<AttributeRoll> rolls) {
    }

    private record HordeLoadout(Set<String> touchedAttributes, String summary) {
    }
}

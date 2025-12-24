package me.baddcamden.attributeutils.command;

import me.baddcamden.attributeutils.AttributeUtilitiesPlugin;
import me.baddcamden.attributeutils.api.AttributeFacade;
import me.baddcamden.attributeutils.model.AttributeValueStages;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Command entrypoint for the plugin's {@code /attribute} command.
 * <p>
 * <ul>
 *     <li>If executed by a player without arguments, it lists every registered attribute and
 *     the player's computed values using {@link AttributeFacade#compute(String, Player)}.</li>
 *     <li>If executed with {@code reload}, it reloads plugin configuration when the sender has
 *     the {@code attributeutils.reload} permission.</li>
 *     <li>Console senders are only able to reload; they are shown an informational message when
 *     attempting to view player-focused attribute details.</li>
 * </ul>
 */
public class AttributeCommand implements CommandExecutor, TabCompleter {

    private final AttributeFacade attributeFacade;
    private final AttributeUtilitiesPlugin plugin;
    private final CommandMessages messages;

    /**
     * Creates a new command handler wired to the attribute facade and plugin instance.
     *
     * @param attributeFacade facade used to access definitions and compute values.
     * @param plugin          owning plugin used for configuration reloads and message formatting.
     */
    public AttributeCommand(AttributeFacade attributeFacade, AttributeUtilitiesPlugin plugin) {
        this.attributeFacade = attributeFacade;
        this.plugin = plugin;
        this.messages = new CommandMessages(plugin);
    }

    /**
     * Executes the {@code /attribute} command.
     *
     * @param sender  command executor; reload is available to any sender with permission, but
     *                attribute listings require an in-game {@link Player}.
     * @param command command instance provided by Bukkit.
     * @param label   label used to invoke the command.
     * @param args    command arguments; if the first argument is {@code reload}, the plugin
     *                configuration is refreshed.
     * @return {@code true} to indicate the command was handled for both player and console
     *         contexts.
     */
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        // Handle reload explicitly first to avoid evaluating sender type when unnecessary.
        if (args.length > 0 && args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission("attributeutils.reload")) {
                sender.sendMessage(messages.format(
                        "messages.attribute-command.reload-no-permission",
                        "§cYou do not have permission to reload attributes."));
                return true;
            }

            plugin.reloadAttributes();
            sender.sendMessage(messages.format(
                    "messages.attribute-command.reload-success",
                    "§aAttribute configuration reloaded."));
            return true;
        }

        if (!(sender instanceof Player)) {
            sender.sendMessage(messages.format(
                    "messages.attribute-command.invalid-sender",
                    "§eAttributes are player-focused; use in-game for details."));
            return true;
        }

        Player player = (Player) sender;
        sender.sendMessage(messages.format(
                "messages.attribute-command.header",
                "§bRegistered attributes:"));
        attributeFacade.getDefinitions().forEach(definition -> {
            AttributeValueStages stages = attributeFacade.compute(definition.id(), player);
            sender.sendMessage(buildPlayerLine(definition.displayName(), stages));
        });
        return true;
    }

    /**
     * Builds the formatted output line for a player's attribute values.
     *
     * @param displayName human-friendly attribute display name.
     * @param stages      computed stages for the player. The record contains intermediate values
     *                    for raw/default/current states, but only the raw and final values are
     *                    surfaced to keep chat output concise.
     * @return formatted line containing the raw default, final default, raw current, and final
     * current values for the attribute.
     */
    private String buildPlayerLine(String displayName, AttributeValueStages stages) {
        return ChatColor.GRAY + " - " + displayName + ChatColor.WHITE +
                " rawDefault=" + stages.rawDefault() +
                " default=" + stages.defaultFinal() +
                " rawCurrent=" + stages.rawCurrent() +
                " final=" + stages.currentFinal();
    }

    /**
     * Provides tab completion suggestions for the command.
     *
     * @param sender  command executor; only those with {@code attributeutils.reload} permission
     *                receive the reload suggestion.
     * @param command command instance provided by Bukkit.
     * @param alias   alias used to invoke the command.
     * @param args    current arguments; suggestions are offered only for the first argument when
     *                it partially matches {@code reload}.
     * @return singleton list containing {@code reload} when eligible, otherwise an empty list.
     */
    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        // Tab completion only exposes "reload" when the argument is in the first position,
        // the sender has permission, and the partial input matches the literal.
        if (args.length == 1
                && sender.hasPermission("attributeutils.reload")
                && "reload".toLowerCase(Locale.ROOT).startsWith(args[0].toLowerCase(Locale.ROOT))) {
            return Collections.singletonList("reload");
        }
        return Collections.emptyList();
    }
}

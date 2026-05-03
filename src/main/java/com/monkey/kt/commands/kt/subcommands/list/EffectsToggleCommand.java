package com.monkey.kt.commands.kt.subcommands.list;

import com.monkey.kt.KT;
import com.monkey.kt.commands.kt.subcommands.inter.SubCommand;
import com.monkey.kt.storage.EffectVisibilityStorage;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class EffectsToggleCommand implements SubCommand {

    private final KT plugin;

    public EffectsToggleCommand(KT plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getName() {
        return "effects";
    }

    @Override
    public String getPermission() {
        return null;
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.translateAlternateColorCodes('&',
                    plugin.getConfig().getString("messages.only_players")));
            return;
        }

        Player player = (Player) sender;
        EffectVisibilityStorage.toggle(player.getUniqueId());

        boolean nowEnabled = EffectVisibilityStorage.isEnabled(player.getUniqueId());
        String messageKey = nowEnabled ? "messages.effects_visibility_enabled" : "messages.effects_visibility_disabled";
        String message = plugin.getConfig().getString(messageKey);
        player.sendMessage(ChatColor.translateAlternateColorCodes('&', message));
    }
}

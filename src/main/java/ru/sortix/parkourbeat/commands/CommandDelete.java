package ru.sortix.parkourbeat.commands;

import java.util.List;
import lombok.NonNull;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import ru.sortix.parkourbeat.ParkourBeat;
import ru.sortix.parkourbeat.activity.ActivityManager;
import ru.sortix.parkourbeat.levels.Level;
import ru.sortix.parkourbeat.levels.LevelsManager;
import ru.sortix.parkourbeat.levels.settings.GameSettings;

public class CommandDelete extends ParkourBeatCommand implements TabCompleter {
    private final LevelsManager levelsManager;

    public CommandDelete(@NonNull ParkourBeat plugin) {
        super(plugin);
        this.levelsManager = plugin.get(LevelsManager.class);
    }

    @Override
    public boolean onCommand(
            @NonNull CommandSender sender, @NonNull Command command, @NonNull String label, @NonNull String[] args) {
        if (sender instanceof Player) {
            sender.sendMessage("Используйте предмет \"Параметры уровня\"");
            return true;
        }

        if (!sender.hasPermission("parkourbeat.command.delete")) {
            sender.sendMessage("Недостаточно прав");
            return true;
        }

        if (args.length == 0) {
            sender.sendMessage("Пожалуйста, укажите уровень!");
            return true;
        }

        String levelName = String.join(" ", args);
        GameSettings settings = this.levelsManager.findLevel(levelName);

        if (settings == null) {
            sender.sendMessage("Уровень \"" + levelName + "\" не найден!");
            return true;
        }

        if (!settings.isOwner(sender, true, true)) {
            sender.sendMessage("Вы не являетесь владельцем этого уровня");
            return true;
        }

        deleteLevel(this.plugin, sender, settings);
        return true;
    }

    @Override
    public List<String> onTabComplete(
            @NonNull CommandSender sender, @NonNull Command command, @NonNull String label, @NonNull String[] args) {
        if (sender instanceof Player) return null;
        if (args.length == 0) return null;
        return this.levelsManager.getUniqueLevelNames(String.join(" ", args), sender);
    }

    public static void deleteLevel(
            @NonNull ParkourBeat plugin, @NonNull CommandSender sender, @NonNull GameSettings settings) {
        LevelsManager levelsManager = plugin.get(LevelsManager.class);
        ActivityManager activityManager = plugin.get(ActivityManager.class);

        Level loadedLevel = levelsManager.getLoadedLevel(settings.getUniqueId());
        if (loadedLevel != null) {
            for (Player player : loadedLevel.getWorld().getPlayers()) {
                if (player != sender) {
                    player.sendMessage("Уровень \"" + settings.getDisplayName() + "\" был удален");
                }
                activityManager.setActivity(player, null);
            }
        }

        levelsManager.deleteLevelAsync(settings).thenAccept(success -> {
            if (success) {
                sender.sendMessage("Вы успешно удалили уровень \"" + settings.getDisplayName() + "\"");
            } else {
                sender.sendMessage("Не удалось удалить уровень \"" + settings.getDisplayName() + "\"");
            }
        });
    }
}

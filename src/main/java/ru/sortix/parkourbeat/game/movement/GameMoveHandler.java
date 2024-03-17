package ru.sortix.parkourbeat.game.movement;

import lombok.Getter;
import lombok.NonNull;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerToggleSprintEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;
import ru.sortix.parkourbeat.game.Game;
import ru.sortix.parkourbeat.levels.settings.LevelSettings;
import ru.sortix.parkourbeat.levels.settings.WorldSettings;

public class GameMoveHandler {
    private static final int NOT_SPRINT_DAMAGE_PER_PERIOD = 1;
    private static final int NOT_SPRINT_DAMAGE_PERIOD_TICKS = 1;

    private final Game game;
    private final Location startBorder, finishBorder;

    @Getter
    private final MovementAccuracyChecker accuracyChecker;

    private BukkitTask task;

    public GameMoveHandler(Game game) {
        this.game = game;

        WorldSettings worldSettings = game.getLevel().getLevelSettings().getWorldSettings();
        this.accuracyChecker = new MovementAccuracyChecker(
                worldSettings.getWaypoints(), game.getLevel().getLevelSettings().getDirectionChecker());

        startBorder = worldSettings.getStartBorderLoc();
        finishBorder = worldSettings.getFinishBorderLoc();
    }

    public void onPreparingState(PlayerMoveEvent event) {
        event.setCancelled(true);
    }

    public void onReadyState(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        LevelSettings settings = game.getLevel().getLevelSettings();
        if (settings.getDirectionChecker().isCorrectDirection(startBorder, player.getLocation())) {
            game.start();
            if ((task == null || task.isCancelled()) && !player.isSprinting()) {
                startDamageTask(player, "§cНе переставайте бежать", "§cВы остановились");
            }
        }
    }

    public void onRunningState(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        LevelSettings settings = this.game.getLevel().getLevelSettings();
        if (settings.getDirectionChecker().isCorrectDirection(this.finishBorder, player.getLocation())) {
            this.game.completeLevel();
            return;
        }
        if (!isLookingAtFinish(player)) {
            this.game.failLevel("§cНельзя бежать назад");
            return;
        }
        if (!settings.getDirectionChecker().isCorrectDirection(event.getFrom(), event.getTo())) {
            if (settings.getDirectionChecker().isSameDirection(event.getFrom(), event.getTo())) {
                this.game.failLevel("§cВы остановились");
            } else {
                this.game.failLevel("§cНельзя бежать назад");
            }
            return;
        }
        this.accuracyChecker.onPlayerLocationChange(event.getTo());
        player.sendActionBar("§aТочность: " + String.format("%.2f", this.accuracyChecker.getAccuracy() * 100f) + "%");
    }

    public void onRunningState(PlayerToggleSprintEvent event) {
        Player player = event.getPlayer();
        if (!event.isSprinting()) {
            startDamageTask(player, "§cНе переставайте бежать", "§cВы остановились");
        } else {
            if (this.task != null) {
                this.task.cancel();
            }
        }
    }

    private boolean isLookingAtFinish(Player player) {
        Vector playerDir = player.getLocation().getDirection();
        Vector targetDir = finishBorder.toVector().subtract(startBorder.toVector());
        double angle = playerDir.angle(targetDir);
        return Math.toDegrees(angle) < 100;
    }

    private void startDamageTask(Player player, String reason, String stopReason) {
        player.playSound(player.getLocation(), Sound.ENTITY_WOLF_HURT, 1, 1);

        this.task = new BukkitRunnable() {
            @Override
            public void run() {
                if (!player.isOnline() || game.getCurrentState() != Game.State.RUNNING) {
                    this.cancel();
                    return;
                }

                player.sendTitle(reason, null, 0, 5, 5);
                boolean stopped;
                if (player.getHealth() <= NOT_SPRINT_DAMAGE_PER_PERIOD) {
                    game.failLevel(stopReason);
                    stopped = true;
                } else {
                    if (player.getNoDamageTicks() <= 0) {
                        player.setHealth(player.getHealth() - NOT_SPRINT_DAMAGE_PER_PERIOD);
                        player.setNoDamageTicks(NOT_SPRINT_DAMAGE_PERIOD_TICKS);
                    }
                    stopped = false;
                }

                if (stopped) {
                    this.cancel();
                }
            }
        }.runTaskTimer(this.getPlugin(), 0, 2);
    }

    @NonNull public Plugin getPlugin() {
        return this.game.getPlugin();
    }
}

package ru.sortix.parkourbeat.game.movement;

import lombok.Getter;
import lombok.NonNull;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerToggleSprintEvent;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;
import ru.sortix.parkourbeat.game.Game;
import ru.sortix.parkourbeat.levels.settings.LevelSettings;
import ru.sortix.parkourbeat.levels.settings.WorldSettings;

import javax.annotation.Nullable;

public class GameMoveHandler {
    private static final boolean DISPLAY_DEBUG_FAIL_REASONS = true;

    private static final int NOT_SPRINT_DAMAGE_PER_PERIOD = 1;
    private static final int NOT_SPRINT_DAMAGE_PERIOD_TICKS = 1;

    private final @NonNull Game game;
    private final @NonNull Location startWaypoint;
    private final @NonNull Location finishWaypoint;
    private final @NonNull Vector startToFinishVector;

    @Getter
    private final @NonNull MovementAccuracyChecker accuracyChecker;

    private BukkitTask task;

    public GameMoveHandler(@NonNull Game game) {
        this.game = game;

        LevelSettings settings = game.getLevel().getLevelSettings();
        WorldSettings worldSettings = settings.getWorldSettings();
        this.accuracyChecker = new MovementAccuracyChecker(
            worldSettings.getWaypoints(), settings.getDirectionChecker());

        this.startWaypoint = settings.getStartWaypointLoc();
        this.finishWaypoint = settings.getFinishWaypointLoc();
        this.startToFinishVector = this.finishWaypoint.toVector().subtract(this.startWaypoint.toVector());
    }

    public void onPreparingState(@NonNull PlayerMoveEvent event) {
        event.setCancelled(true);
    }

    public void onReadyState(@NonNull Player player) {
        LevelSettings settings = this.game.getLevel().getLevelSettings();
        if (settings.getDirectionChecker().isCorrectDirection(this.startWaypoint, player.getLocation())) {
            this.game.start();
            if ((this.task == null || this.task.isCancelled()) && !player.isSprinting()) {
                this.startDamageTask(player,
                    "§cНе отпускайте клавишу Ctrl!", null,
                    "§cВы проиграли =(", null
                );
            }
        }
    }

    public void onRunningState(@NonNull Player player, @NonNull Location from, @NonNull Location to) {
        LevelSettings settings = this.game.getLevel().getLevelSettings();
        if (settings.getDirectionChecker().isCorrectDirection(this.finishWaypoint, player.getLocation())) {
            this.game.completeLevel();
            return;
        }
        double angle = getLeftOrRightRotationAngle(player);
        if (angle > 100) {
            if (DISPLAY_DEBUG_FAIL_REASONS) {
                this.game.failLevel("§cНеверный угол поворота: ", String.valueOf(angle));
            } else {
                this.game.failLevel("§cНельзя бежать назад!", null);
            }
            return;
        }
        if (!settings.getDirectionChecker().isCorrectDirection(from, to)) {
            if (DISPLAY_DEBUG_FAIL_REASONS) {
                double fromPos = settings.getDirectionChecker().getCoordinate(from);
                double toPos = settings.getDirectionChecker().getCoordinate(to);
                this.game.failLevel("§cНеверные координаты: ", fromPos + " -> " + toPos);
            } else {
                this.game.failLevel("§cНельзя бежать назад!", null);
            }
            return;
        }
        this.accuracyChecker.onPlayerLocationChange(to);
        player.sendActionBar(Component.text(
            "Точность: " + String.format("%.2f", this.accuracyChecker.getAccuracy() * 100f) + "%",
            NamedTextColor.GREEN
        ));
    }

    public void onRunningState(@NonNull PlayerToggleSprintEvent event) {
        Player player = event.getPlayer();
        if (!event.isSprinting()) {
            this.startDamageTask(player,
                "§cНе отпускайте клавишу Ctrl!", null,
                "§cВы проиграли =(", null
            );
        } else {
            if (this.task != null) {
                this.task.cancel();
            }
        }
    }

    private double getLeftOrRightRotationAngle(@NonNull Player player) {
        Vector playerVector = player.getLocation().getDirection();
        return Math.toDegrees(playerVector.angle(this.startToFinishVector));
    }

    @SuppressWarnings("SameParameterValue")
    private void startDamageTask(@NonNull Player player,
                                 @Nullable String warnReasonFirstLine, @Nullable String warnReasonSecondLine,
                                 @Nullable String failReasonFirstLine, @Nullable String failReasonSecondLine
    ) {
        player.playSound(player.getLocation(), Sound.ENTITY_WOLF_HURT, 1, 1);

        this.task = new BukkitRunnable() {
            @Override
            public void run() {
                if (!player.isOnline() || game.getCurrentState() != Game.State.RUNNING) {
                    this.cancel();
                    return;
                }

                boolean levelFailed;
                if (player.getHealth() <= NOT_SPRINT_DAMAGE_PER_PERIOD) {
                    levelFailed = true;
                    game.failLevel(failReasonFirstLine, failReasonSecondLine);
                } else {
                    levelFailed = false;
                    player.sendTitle(
                        warnReasonFirstLine == null ? "" : warnReasonFirstLine,
                        warnReasonSecondLine == null ? "" : warnReasonSecondLine,
                        0, 5, 5
                    );
                    if (player.getNoDamageTicks() <= 0) {
                        player.setHealth(player.getHealth() - NOT_SPRINT_DAMAGE_PER_PERIOD);
                        player.setNoDamageTicks(NOT_SPRINT_DAMAGE_PERIOD_TICKS);
                    }
                }

                if (levelFailed) {
                    this.cancel();
                }
            }
        }.runTaskTimer(this.game.getPlugin(), 0, 2);
    }
}

package ru.sortix.parkourbeat.listeners;

import javax.annotation.Nullable;
import lombok.NonNull;
import org.bukkit.GameMode;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityRegainHealthEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.*;
import org.spigotmc.event.player.PlayerSpawnLocationEvent;
import ru.sortix.parkourbeat.ParkourBeat;
import ru.sortix.parkourbeat.data.Settings;
import ru.sortix.parkourbeat.editor.EditorSession;
import ru.sortix.parkourbeat.editor.LevelEditorsManager;
import ru.sortix.parkourbeat.game.Game;
import ru.sortix.parkourbeat.game.GameManager;
import ru.sortix.parkourbeat.game.movement.GameMoveHandler;
import ru.sortix.parkourbeat.levels.Level;
import ru.sortix.parkourbeat.levels.dao.LevelSettingDAO;
import ru.sortix.parkourbeat.utils.TeleportUtils;

public final class GamesListener implements Listener {
    private final GameManager gameManager;
    private final LevelEditorsManager levelEditorsManager;
    private final LevelSettingDAO levelSettingDAO;

    public GamesListener(
            @NonNull ParkourBeat plugin,
            @NonNull GameManager gameManager,
            @NonNull LevelEditorsManager levelEditorsManager) {
        this.gameManager = gameManager;
        this.levelEditorsManager = levelEditorsManager;
        this.levelSettingDAO =
                this.levelEditorsManager.getLevelsManager().getLevelsSettings().getLevelSettingDAO();

        for (Player player : plugin.getServer().getOnlinePlayers()) {
            if (this.getWorldType(player) != WorldType.PB_LEVEL) continue;
            TeleportUtils.teleport(player, Settings.getLobbySpawn());
            player.setGameMode(GameMode.ADVENTURE);
            player.getInventory().clear();
            player.sendMessage("Перезагрузка плагина привела к телепортации в лобби");
        }
    }

    @EventHandler
    private void on(PlayerSpawnLocationEvent event) {
        event.setSpawnLocation(Settings.getLobbySpawn());
    }

    @EventHandler
    private void on(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        if (!levelEditorsManager.removeEditorSession(player)) gameManager.removeGame(player);
    }

    @EventHandler
    private void on(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (this.getWorldType(player) == WorldType.NON_PB) return;
        TeleportUtils.teleport(player, Settings.getLobbySpawn());
        player.setHealth(20);
        player.setFoodLevel(20);
        player.setSaturation(5.0F);
        player.setExhaustion(0.0F);
        player.setFireTicks(-40);
        player.setGameMode(GameMode.ADVENTURE);
        player.getInventory().clear();
    }

    @EventHandler
    private void on(EntityDamageEvent event) {
        if (event.getEntity().getType() != EntityType.PLAYER) {
            return;
        }
        Player player = (Player) event.getEntity();
        if (this.getWorldType(player) == WorldType.NON_PB) return;
        event.setCancelled(true);
    }

    @EventHandler
    private void on(EntityRegainHealthEvent event) {
        if (!(event.getEntity() instanceof Player)) return;
        Player player = ((Player) event.getEntity());
        if (this.getWorldType(player) == WorldType.NON_PB) return;
        event.setCancelled(true);
    }

    @EventHandler
    private void on(PlayerDeathEvent event) {
        Player player = event.getEntity();
        if (this.getWorldType(player) == WorldType.NON_PB) return;

        event.setKeepInventory(true);
        event.getDrops().clear();
        player.spigot().respawn();

        Game game = this.gameManager.getCurrentGame(player);
        if (game != null) game.stopGame(Game.StopReason.DEATH);
    }

    @EventHandler
    private void on(FoodLevelChangeEvent event) {
        if (this.getWorldType((Player) event.getEntity()) == WorldType.NON_PB) return;
        if (event.getFoodLevel() != 20) {
            event.setFoodLevel(20);
        }
    }

    @EventHandler
    private void on1(PlayerMoveEvent event) {
        if (event.getFrom().getY() <= event.getTo().getY()) return;

        Player player = event.getPlayer();
        if (this.getWorldType(player) == WorldType.NON_PB) return;

        Level level = this.getEditOrGameLevel(player);
        int minWorldHeight = this.getFallHeight(level, this.gameManager.getCurrentGame(player) != null);
        if (event.getTo().getY() > minWorldHeight) return;

        Game game = this.gameManager.getCurrentGame(player);
        if (game != null) game.stopGame(Game.StopReason.FALL);
        else TeleportUtils.teleport(player, player.getWorld().getSpawnLocation());
    }

    private int getFallHeight(@Nullable Level level, boolean play) {
        if (!play) return -5;
        return (level == null ? 0 : level.getLevelSettings().getWorldSettings().getMinWorldHeight()) - 1;
    }

    @EventHandler
    private void on2(PlayerMoveEvent event) {
        Player player = event.getPlayer();

        Game game = this.gameManager.getCurrentGame(player);
        if (game == null) return;

        Game.State state = game.getCurrentState();
        GameMoveHandler gameMoveHandler = game.getGameMoveHandler();
        if (state == Game.State.PREPARING) {
            gameMoveHandler.onPreparingState(event);
        } else if (state == Game.State.READY) {
            gameMoveHandler.onReadyState(event);
        } else if (state == Game.State.RUNNING) {
            gameMoveHandler.onRunningState(event);
        }
    }

    @EventHandler
    private void on(PlayerDropItemEvent event) {
        if (this.getWorldType(event.getPlayer()) == WorldType.NON_PB) return;
        event.setCancelled(true);
    }

    @EventHandler
    private void on(PlayerResourcePackStatusEvent event) {
        Player player = event.getPlayer();
        Game game = this.gameManager.getCurrentGame(player);
        if (game == null) return;

        switch (event.getStatus()) {
            case ACCEPTED: {
                player.sendMessage("Началась загрузка мелодии. " + " После окончания загрузки вы сможете начать игру");
                return;
            }
            case FAILED_DOWNLOAD: {
                player.sendMessage("Ошибка загрузки мелодии."
                        + " Вам доступна игра без ресурс-пака, "
                        + "однако мы рекомендуем всё же установить пакет ресурсов для более комфортной игры");
                return;
            }
            case DECLINED: {
                player.sendMessage("Вы отказались от загрузки мелодии. "
                        + "Если вы захотите вновь загрузить ресурс-пак - убедитесь, "
                        + "что в настройках сервера у вас разрешено принятие пакетов ресурсов");
                break;
            }
            case SUCCESSFULLY_LOADED: {
                player.sendMessage("Мелодия успешно загружена, приятной игры!");
                break;
            }
        }
        game.setCurrentState(Game.State.READY);
    }

    @EventHandler
    private void on(PlayerToggleSprintEvent event) {
        Game game = gameManager.getCurrentGame(event.getPlayer());
        if (game == null) return;

        if (game.getCurrentState() == Game.State.RUNNING) {
            game.getGameMoveHandler().onRunningState(event);
        }
    }

    @EventHandler
    private void on(PlayerInteractEvent event) {
        if (this.getWorldType(event.getPlayer()) == WorldType.NON_PB) return;
        Block block = event.getClickedBlock();
        if (block == null) return;
        EditorSession editorSession = this.levelEditorsManager.getEditorSession(event.getPlayer());
        if (editorSession != null
                && block.getWorld() == editorSession.getLevel().getWorld()) return;
        if (event.getPlayer().hasPermission("parkourbeat.level.edit.anytime")) return;
        event.setUseInteractedBlock(Event.Result.DENY);
    }

    @Nullable private Level getEditOrGameLevel(@NonNull Player player) {
        Game game = this.gameManager.getCurrentGame(player);
        if (game != null) return game.getLevel();

        EditorSession editorSession = this.levelEditorsManager.getEditorSession(player);
        if (editorSession != null) return editorSession.getLevel();

        return null;
    }

    @NonNull private WorldType getWorldType(@NonNull Player player) {
        Level level = this.getCurrentPlayerLevel(player);
        if (level != null && level.getWorld() == player.getWorld()) return WorldType.PB_LEVEL;
        return this.getWorldType(player.getWorld());
    }

    @Nullable private Level getCurrentPlayerLevel(@NonNull Player player) {
        Game game = this.gameManager.getCurrentGame(player);
        if (game != null) return game.getLevel();
        EditorSession editorSession = this.levelEditorsManager.getEditorSession(player);
        if (editorSession != null) return editorSession.getLevel();
        return null;
    }

    @NonNull private WorldType getWorldType(@NonNull World world) {
        if (Settings.getLobbySpawn().getWorld() == world) {
            return WorldType.PB_LOBBY;
        }
        if (this.levelSettingDAO.isLevelWorld(world)) {
            return WorldType.PB_LEVEL;
        }
        return WorldType.NON_PB;
    }

    public enum WorldType {
        PB_LOBBY,
        PB_LEVEL,
        NON_PB
    }
}

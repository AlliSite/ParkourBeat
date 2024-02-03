package parkourbeat.listeners;

import org.bukkit.Bukkit;
import org.bukkit.event.entity.PlayerDeathEvent;
import parkourbeat.ParkourBeat;
import parkourbeat.data.Settings;
import parkourbeat.game.Game;
import parkourbeat.game.GameManager;
import org.bukkit.Location;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.spigotmc.event.player.PlayerSpawnLocationEvent;

public final class EventListener implements Listener {

    @EventHandler
    public void onSpawnLocation(PlayerSpawnLocationEvent event) {
        event.setSpawnLocation(Settings.getExitLocation());
    }

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent event) {
        if (event.isNewChunk()) {
            event.getChunk().unload(false);
        }
    }

    @EventHandler
    public void onChunkUnload(ChunkUnloadEvent event) {
        if (event.getWorld() == Settings.getExitLocation().getWorld()) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        GameManager.removeGame(event.getPlayer());
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        player.teleport(Settings.getExitLocation());
        player.setHealth(20);
        player.setFoodLevel(20);
        player.setSaturation(5.0F);
        player.setExhaustion(0.0F);
        player.setFireTicks(-40);
    }

    @EventHandler
    public void onPlayerDamage(EntityDamageEvent event) {
        if (event.getEntity().getType() != EntityType.PLAYER) {
            return;
        }
        Player player = (Player) event.getEntity();
        Game game = GameManager.getCurrentGame(player);
        if (game == null) {
            return;
        }
        if (game.getCurrentState() != Game.State.RUNNING) {
            if (event.getCause() == DamageCause.VOID) {
                event.setCancelled(true);
                game.stopGame(Game.StopReason.LOOSE);
            }
        }
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        Bukkit.getScheduler().scheduleSyncDelayedTask(ParkourBeat.getInstance(), () -> {
            event.getEntity().spigot().respawn();
            Game game = GameManager.getCurrentGame(event.getEntity());
            if (game != null) {
                game.stopGame(Game.StopReason.LOOSE);
            } else {
                event.getEntity().teleport(Settings.getExitLocation());
            }
        });
    }

    @EventHandler
    public void onFoodLevelChange(FoodLevelChangeEvent event) {
        event.setFoodLevel(20);
    }

}

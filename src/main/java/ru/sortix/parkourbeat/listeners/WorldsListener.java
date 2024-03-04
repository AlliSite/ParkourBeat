package ru.sortix.parkourbeat.listeners;

import lombok.RequiredArgsConstructor;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.WorldInitEvent;
import ru.sortix.parkourbeat.levels.LevelsManager;

@RequiredArgsConstructor
public class WorldsListener implements Listener {
    private final LevelsManager levelsManager;

    @EventHandler
    private void on(WorldInitEvent event) {
        // if (!event.getWorld().getName().startsWith("pb_level_")) return; // TODO See
        // https://github.com/Slomix/ParkourBeat/issues/9
        this.levelsManager.prepareLevelWorld(event.getWorld(), false);
    }
}

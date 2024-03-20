package ru.sortix.parkourbeat.levels;

import java.util.UUID;
import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.bukkit.Location;
import org.bukkit.World;
import ru.sortix.parkourbeat.levels.settings.LevelSettings;

@Getter
@RequiredArgsConstructor
public class Level {
    private final @NonNull LevelSettings levelSettings;
    private final @NonNull World world;
    private boolean isEditing = false;

    public void setEditing(boolean isEditing) {
        this.isEditing = isEditing;
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof Level)) return false;
        return ((Level) other).getUniqueId().equals(this.getUniqueId());
    }

    @NonNull public String getDisplayName() {
        return this.levelSettings.getGameSettings().getDisplayName();
    }

    @NonNull public UUID getUniqueId() {
        return this.levelSettings.getGameSettings().getUniqueId();
    }

    @NonNull public Location getSpawn() {
        return this.levelSettings.getWorldSettings().getSpawn();
    }
}

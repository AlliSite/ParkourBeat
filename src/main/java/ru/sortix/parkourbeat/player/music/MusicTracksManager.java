package ru.sortix.parkourbeat.player.music;

import lombok.NonNull;
import me.bomb.amusic.AMusic;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import ru.sortix.parkourbeat.ParkourBeat;
import ru.sortix.parkourbeat.inventory.type.editor.SelectSongMenu;
import ru.sortix.parkourbeat.lifecycle.PluginManager;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;
import java.util.stream.Stream;

public class MusicTracksManager implements PluginManager {
    public static final boolean LEGACY_MODE = true; // Valid non-cached tracks not present in AMusic.getPlaylists() in AMusic v0.12
    private static final String PLAYLIST_SONG_NAME = "track";

    private final @NonNull ParkourBeat plugin;
    private List<MusicTrack> legacyCachedTracks;

    public MusicTracksManager(@SuppressWarnings("unused") @NonNull ParkourBeat plugin) {
        this.plugin = plugin;
        this.reloadAllTracks();
    }

    private void reloadAllTracks() {
        this.legacyCachedTracks = LEGACY_MODE ? Collections.unmodifiableList(this.loadAllTracksLegacy()) : null;
        for (Player player : this.plugin.getServer().getOnlinePlayers()) {
            if (player.getOpenInventory().getTopInventory().getHolder() instanceof SelectSongMenu menu) {
                menu.updateItems();
            }
        }
    }

    @NonNull
    private List<MusicTrack> loadAllTracksLegacy() {
        List<MusicTrack> result = new ArrayList<>();
        Path path = new File(JavaPlugin.getPlugin(AMusic.class).getDataFolder(), "Music").toPath();
        try (Stream<Path> paths = Files.walk(path)) {
            paths.filter(Files::isRegularFile).forEach(file -> {
                String songUniqueId = file.getParent().getFileName().toString();
                result.add(new MusicTrack(songUniqueId));
            });
        } catch (Exception e) {
            this.plugin.getLogger().log(Level.SEVERE, "Unable to reload songs", e);
        }
        return result;
    }

    @NonNull
    public MusicTrack createSongByUniqueId(@NonNull String songUniqueId) {
        return new MusicTrack(songUniqueId);
    }

    @NonNull
    public List<MusicTrack> getAllTracksModern() {
        if (LEGACY_MODE) return this.legacyCachedTracks;

        List<MusicTrack> result = new ArrayList<>();
        for (String songPlaylist : AMusic.getPlaylists()) {
            List<String> soundNames = AMusic.getPlaylistSoundnames(songPlaylist);
            if (soundNames.size() != 1 || !soundNames.iterator().next().equals(PLAYLIST_SONG_NAME)) continue;
            result.add(new MusicTrack(songPlaylist));
        }
        return Collections.unmodifiableList(result);
    }

    public void playSongFromLoadedResourcepack(@NonNull Player player) {
        AMusic.setRepeatMode(player, null);
        AMusic.playSound(player, PLAYLIST_SONG_NAME);
    }

    public void stopSongFromLoadedResourcepack(@NonNull Player player) {
        AMusic.stopSound(player);
    }

    public void updateTrackFileInfo(@NonNull String trackName) {
        this.plugin.getLogger().info("Updating file of track \"" + trackName + "\"");
        AMusic.loadPack(null, trackName, true);
        for (Player player : this.getPlayersWithPack(trackName)) {
            player.sendMessage("Перезагрузка трека \"" + trackName + "\"...");
            AMusic.loadPack(player, trackName, false);
        }
        this.reloadAllTracks();
        this.plugin.getLogger().info("File of track \"" + trackName + "\" updated");
    }

    @NonNull
    private Collection<Player> getPlayersWithPack(@NonNull String packName) {
        List<Player> result = new ArrayList<>();
        for (Player player : this.plugin.getServer().getOnlinePlayers()) {
            String playerPackName = AMusic.getPackName(player);
            if (playerPackName == null || !playerPackName.equals(packName)) continue;
            result.add(player);
        }
        return result;
    }

    @Override
    public void disable() {
    }
}

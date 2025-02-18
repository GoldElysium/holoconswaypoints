package xyz.holocons.mc.waypoints;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

public class TravelerManager {

    public static final String FILENAME = "traveler.json";

    private final HashMap<UUID, Traveler> travelers;
    private final HashMap<UUID, BukkitRunnable> tasks;

    public TravelerManager() {
        this.travelers = new HashMap<>();
        this.tasks = new HashMap<>();
    }

    public void loadTravelers(PaperPlugin plugin) throws IOException {
        final var file = new File(plugin.getDataFolder(), FILENAME);

        if (!file.exists()) {
            return;
        }

        // Clear internal if internal data isn't empty
        if (!travelers.isEmpty()) {
            clearTravelers();
        }

        // Load player data
        final var reader = new GsonReader(plugin.getGson(), file);
        reader.beginObject();
        while (reader.hasNext()) {
            UUID uniqueId;
            try {
                uniqueId = UUID.fromString(reader.nextName());
            } catch (IllegalArgumentException e) {
                throw new IOException(e);
            }
            var traveler = reader.nextTraveler();
            travelers.put(uniqueId, traveler);
        }
        reader.endObject();
        reader.close();

        // Start regen timer for online players
        for (var player : Bukkit.getOnlinePlayers()) {
            getOrCreateTraveler(player).startRegenCharge(plugin);
        }
    }

    public void saveTravelers(PaperPlugin plugin) throws IOException {
        if (travelers.isEmpty()) {
            return;
        }

        final var file = new File(plugin.getDataFolder(), FILENAME);

        final var writer = new GsonWriter(plugin.getGson(), file);
        writer.beginObject();
        for (final var traveler : travelers.entrySet()) {
            writer.name(traveler.getKey().toString());
            writer.value(traveler.getValue());
        }
        writer.endObject();
        writer.close();
    }

    public void clearTravelers() {
        // Stop regen timers and tasks, then clear
        travelers.values().forEach(Traveler::stopRegenCharge);
        travelers.clear();
        tasks.values().forEach(BukkitRunnable::cancel);
        tasks.clear();
    }

    public Traveler getOrCreateTraveler(UUID uniqueId) {
        var traveler = travelers.get(uniqueId);

        if (traveler == null) {
            traveler = new Traveler(1, 1, null, null, null);
            travelers.put(uniqueId, traveler);
        }

        return traveler;
    }

    public Traveler getOrCreateTraveler(Player player) {
        return getOrCreateTraveler(player.getUniqueId());
    }

    public <T extends BukkitRunnable> T getTask(Player player, Class<T> taskCls) {
        final var task = tasks.get(player.getUniqueId());

        if (task == null || task.isCancelled()) {
            return null;
        }

        try {
            return taskCls.cast(task);
        } catch (ClassCastException e) {
            return null;
        }
    }

    public void registerTask(Player player, BukkitRunnable task) {
        final var previousTask = tasks.put(player.getUniqueId(), task);

        if (previousTask != null) {
            previousTask.cancel();
        }
    }

    public void unregisterTask(Player player) {
        final var task = tasks.remove(player.getUniqueId());

        if (task != null) {
            task.cancel();
        }
    }

    public void removeWaypoint(Waypoint waypoint) {
        travelers.values().forEach(traveler -> traveler.unregisterWaypoint(waypoint));
    }
}

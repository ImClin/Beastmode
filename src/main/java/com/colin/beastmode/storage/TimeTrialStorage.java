package com.colin.beastmode.storage;

import com.colin.beastmode.Beastmode;
import com.colin.beastmode.time.TimeTrialRecord;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Persists best time-trial results per arena and player.
 */
public class TimeTrialStorage {

    private static final String ARENAS_KEY = "arenas";

    private final Beastmode plugin;
    private final File storageFile;
    private YamlConfiguration configuration;
    private final Map<String, Map<UUID, TimeTrialRecord>> records = new HashMap<>();

    public TimeTrialStorage(Beastmode plugin) {
        this.plugin = plugin;
        this.storageFile = new File(plugin.getDataFolder(), "time-trials.yml");
        load();
    }

    private void load() {
        if (!storageFile.exists()) {
            try {
                plugin.getDataFolder().mkdirs();
                storageFile.createNewFile();
            } catch (IOException ex) {
                plugin.getLogger().log(Level.SEVERE, "Failed to create time-trials.yml", ex);
            }
        }
        configuration = YamlConfiguration.loadConfiguration(storageFile);
        records.clear();

        ConfigurationSection arenasSection = configuration.getConfigurationSection(ARENAS_KEY);
        if (arenasSection == null) {
            return;
        }

        for (String arenaKey : arenasSection.getKeys(false)) {
            ConfigurationSection arenaSection = arenasSection.getConfigurationSection(arenaKey);
            if (arenaSection == null) {
                continue;
            }
            Map<UUID, TimeTrialRecord> arenaRecords = new HashMap<>();
            for (String playerKey : arenaSection.getKeys(false)) {
                UUID playerId;
                try {
                    playerId = UUID.fromString(playerKey);
                } catch (IllegalArgumentException ex) {
                    plugin.getLogger().log(Level.WARNING, "Invalid UUID {0} in time-trial arena {1}", new Object[]{playerKey, arenaKey});
                    continue;
                }
                ConfigurationSection recordSection = arenaSection.getConfigurationSection(playerKey);
                if (recordSection == null) {
                    continue;
                }
                String name = recordSection.getString("name", "Unknown");
                long time = recordSection.getLong("time");
                long recorded = recordSection.getLong("recorded", System.currentTimeMillis());
                arenaRecords.put(playerId, new TimeTrialRecord(playerId, name, time, recorded));
            }
            if (!arenaRecords.isEmpty()) {
                records.put(arenaKey.toLowerCase(), arenaRecords);
            }
        }
    }

    public synchronized List<TimeTrialRecord> getTopRecords(String arenaName, int limit) {
        Map<UUID, TimeTrialRecord> arenaRecords = records.getOrDefault(key(arenaName), Collections.emptyMap());
        if (arenaRecords.isEmpty()) {
            return List.of();
        }
        List<TimeTrialRecord> entries = new ArrayList<>(arenaRecords.values());
        entries.sort(Comparator.naturalOrder());
        if (limit > 0 && entries.size() > limit) {
            return List.copyOf(entries.subList(0, limit));
        }
        return List.copyOf(entries);
    }

    public synchronized RecordUpdate updateRecord(String arenaName,
                                                  UUID playerId,
                                                  String playerName,
                                                  long timeMillis) {
        String key = key(arenaName);
        Map<UUID, TimeTrialRecord> arenaRecords = records.computeIfAbsent(key, ignored -> new HashMap<>());
        TimeTrialRecord existing = arenaRecords.get(playerId);

        boolean improved = existing == null || timeMillis < existing.getTimeMillis();
        if (existing != null && !improved) {
            // Update stored name if it changed but keep best time.
            if (!existing.getPlayerName().equals(playerName)) {
                arenaRecords.put(playerId, existing.withUpdatedName(playerName));
                save();
            }
            int rank = findRank(arenaRecords.values(), playerId);
            return new RecordUpdate(false, rank, existing.getTimeMillis());
        }

        TimeTrialRecord updated = new TimeTrialRecord(playerId, playerName, timeMillis, System.currentTimeMillis());
        arenaRecords.put(playerId, updated);
        save();

        int rank = findRank(arenaRecords.values(), playerId);
        return new RecordUpdate(true, rank, timeMillis);
    }

    private int findRank(Iterable<TimeTrialRecord> records, UUID targetId) {
        List<TimeTrialRecord> sorted = new ArrayList<>();
        for (TimeTrialRecord record : records) {
            sorted.add(record);
        }
        sorted.sort(Comparator.naturalOrder());
        for (int i = 0; i < sorted.size(); i++) {
            if (sorted.get(i).getPlayerId().equals(targetId)) {
                return i + 1;
            }
        }
        return -1;
    }

    public synchronized long getBestTime(String arenaName, UUID playerId) {
        Map<UUID, TimeTrialRecord> arenaRecords = records.get(key(arenaName));
        if (arenaRecords == null) {
            return -1L;
        }
        TimeTrialRecord record = arenaRecords.get(playerId);
        return record != null ? record.getTimeMillis() : -1L;
    }

    public synchronized boolean deleteRecord(String arenaName, UUID playerId) {
        if (arenaName == null || playerId == null) {
            return false;
        }
        Map<UUID, TimeTrialRecord> arenaRecords = records.get(key(arenaName));
        if (arenaRecords == null || arenaRecords.remove(playerId) == null) {
            return false;
        }
        if (arenaRecords.isEmpty()) {
            records.remove(key(arenaName));
        }
        save();
        return true;
    }

    public synchronized boolean deleteRecordByName(String arenaName, String playerName) {
        if (arenaName == null || playerName == null) {
            return false;
        }
        Map<UUID, TimeTrialRecord> arenaRecords = records.get(key(arenaName));
        if (arenaRecords == null) {
            return false;
        }
        UUID targetId = null;
        for (Map.Entry<UUID, TimeTrialRecord> entry : arenaRecords.entrySet()) {
            TimeTrialRecord record = entry.getValue();
            if (record != null && record.getPlayerName().equalsIgnoreCase(playerName)) {
                targetId = entry.getKey();
                break;
            }
        }
        return targetId != null && deleteRecord(arenaName, targetId);
    }

    private void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        ConfigurationSection arenasSection = yaml.createSection(ARENAS_KEY);
        for (Map.Entry<String, Map<UUID, TimeTrialRecord>> arenaEntry : records.entrySet()) {
            ConfigurationSection arenaSection = arenasSection.createSection(arenaEntry.getKey());
            for (TimeTrialRecord record : arenaEntry.getValue().values()) {
                ConfigurationSection recordSection = arenaSection.createSection(record.getPlayerId().toString());
                recordSection.set("name", record.getPlayerName());
                recordSection.set("time", record.getTimeMillis());
                recordSection.set("recorded", record.getRecordedAt());
            }
        }
        try {
            yaml.save(storageFile);
            configuration = yaml;
        } catch (IOException ex) {
            plugin.getLogger().log(Level.SEVERE, "Failed to save time trials", ex);
        }
    }

    private String key(String arenaName) {
        return arenaName == null ? "" : arenaName.toLowerCase();
    }

    public record RecordUpdate(boolean improved, int rank, long bestTimeMillis) {
    }
}

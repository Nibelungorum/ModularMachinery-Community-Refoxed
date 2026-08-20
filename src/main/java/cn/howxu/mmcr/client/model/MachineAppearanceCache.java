package cn.howxu.mmcr.client.model;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.machine.MachineAppearanceSpec;
import cn.howxu.mmcr.api.machine.MachineDefinitions;
import net.minecraft.resources.Identifier;
import net.neoforged.fml.loading.FMLPaths;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Client-side machine appearance snapshot cache.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class MachineAppearanceCache {
    private static final String PERSISTED_SNAPSHOT_FILE = "mmcr-machine-appearance.properties";
    private static final List<Runnable> INVALIDATION_LISTENERS = new CopyOnWriteArrayList<>();
    private static final AtomicLong REVISION = new AtomicLong();

    private static volatile Map<Identifier, MachineAppearanceSpec> snapshot = Map.of();

    private MachineAppearanceCache() {
    }

    public static Map<Identifier, MachineAppearanceSpec> snapshot() {
        return snapshot;
    }

    public static MachineAppearanceSpec specFor(Identifier machineId) {
        MachineAppearanceSpec spec = snapshot.get(machineId);
        if (spec != null) {
            return spec;
        }
        var registration = MachineDefinitions.effectiveSnapshot().get(machineId);
        return registration != null ? registration.appearance() : MachineAppearanceSpec.defaults();
    }

    public static long revision() {
        return REVISION.get();
    }

    public static boolean replaceSnapshot(Map<Identifier, MachineAppearanceSpec> replacement) {
        return replaceSnapshot(replacement, revision() + 1, true);
    }

    public static boolean replaceSnapshot(Map<Identifier, MachineAppearanceSpec> replacement, long contentVersion) {
        return replaceSnapshot(replacement, contentVersion, true);
    }

    public static void addInvalidationListener(Runnable listener) {
        if (listener == null) {
            throw new IllegalArgumentException("listener null");
        }
        INVALIDATION_LISTENERS.add(listener);
    }

    public static void loadPersistedSnapshot() {
        loadPersistedSnapshot(defaultSnapshotPath());
    }

    public static void savePersistedSnapshot() {
        Path path = defaultSnapshotPath();
        if (path != null) {
            savePersistedSnapshot(path);
        }
    }

    public static void loadPersistedSnapshot(Path path) {
        if (path == null || !Files.isRegularFile(path)) {
            return;
        }

        Properties properties = new Properties();
        try (Reader reader = Files.newBufferedReader(path)) {
            properties.load(reader);
        } catch (IOException exception) {
            MMCR.LOG.warn("Failed to load persisted machine appearance snapshot", exception);
            return;
        }

        Map<Identifier, MachineAppearanceSpec> replacement = new LinkedHashMap<>();
        for (String key : properties.stringPropertyNames()) {
            String[] values = properties.getProperty(key).split(",", -1);
            if (values.length != 3) {
                MMCR.LOG.warn("Ignoring invalid machine appearance entry '{}': expected 3 values", key);
                continue;
            }
            try {
                replacement.put(Identifier.parse(key), new MachineAppearanceSpec(
                        Identifier.parse(values[0]),
                        Identifier.parse(values[1]),
                        Identifier.parse(values[2])));
            } catch (RuntimeException exception) {
                MMCR.LOG.warn("Ignoring invalid machine appearance entry '{}'", key, exception);
            }
        }

        replaceSnapshot(replacement, revision() + 1, false);
    }

    public static void savePersistedSnapshot(Path path) {
        if (path == null) {
            return;
        }

        Properties properties = new Properties();
        snapshot.forEach((id, spec) -> properties.setProperty(id.toString(), String.join(",",
                spec.machineBasicBlock().toString(),
                spec.controllerBaseTexture().toString(),
                spec.formedPortBaseTexture().toString())));

        try {
            Path parent = path.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            try (Writer writer = Files.newBufferedWriter(path)) {
                properties.store(writer, "MMCR machine appearance snapshot");
            }
        } catch (IOException exception) {
            MMCR.LOG.warn("Failed to save persisted machine appearance snapshot", exception);
        }
    }

    private static boolean replaceSnapshot(Map<Identifier, MachineAppearanceSpec> replacement,
                                           long contentVersion, boolean persist) {
        if (replacement == null) {
            return false;
        }

        Map<Identifier, MachineAppearanceSpec> copy = new LinkedHashMap<>();
        for (Map.Entry<Identifier, MachineAppearanceSpec> entry : replacement.entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null) {
                return false;
            }
            copy.put(entry.getKey(), entry.getValue());
        }

        snapshot = Map.copyOf(copy);
        REVISION.set(contentVersion);
        notifyListeners();
        if (persist) {
            savePersistedSnapshot();
        }
        return true;
    }

    private static Path defaultSnapshotPath() {
        Path configDir = FMLPaths.CONFIGDIR.get();
        return configDir == null ? null : configDir.resolve(PERSISTED_SNAPSHOT_FILE);
    }

    private static void notifyListeners() {
        for (Runnable listener : INVALIDATION_LISTENERS) {
            try {
                listener.run();
            } catch (RuntimeException exception) {
                MMCR.LOG.warn("Machine appearance cache invalidation listener failed", exception);
            }
        }
    }
}

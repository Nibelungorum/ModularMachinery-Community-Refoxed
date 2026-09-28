package cn.howxu.mmcr.client.model;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.machine.MachineAppearanceSpec;
import cn.howxu.mmcr.api.machine.MachineDefinitions;
import net.minecraft.resources.ResourceLocation;
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

    private static volatile Map<ResourceLocation, MachineAppearanceSpec> snapshot = Map.of();

    private MachineAppearanceCache() {
    }

    public static Map<ResourceLocation, MachineAppearanceSpec> snapshot() {
        return snapshot;
    }

    public static MachineAppearanceSpec specFor(ResourceLocation machineId) {
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

    public static boolean replaceSnapshot(Map<ResourceLocation, MachineAppearanceSpec> replacement) {
        return replaceSnapshot(replacement, revision() + 1, true);
    }

    public static boolean replaceSnapshot(Map<ResourceLocation, MachineAppearanceSpec> replacement, long contentVersion) {
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

        Map<ResourceLocation, MachineAppearanceSpec> replacement = new LinkedHashMap<>();
        for (String key : properties.stringPropertyNames()) {
            String[] values = properties.getProperty(key).split(",", -1);
            if (values.length != 3 && values.length != 5) {
                MMCR.LOG.warn("Ignoring invalid machine appearance entry '{}': expected 3 or 5 values", key);
                continue;
            }
            try {
                replacement.put(ResourceLocation.parse(key), new MachineAppearanceSpec(
                        ResourceLocation.parse(values[0]),
                        values[1].isEmpty() ? null : ResourceLocation.parse(values[1]),
                        values[2].isEmpty() ? null : ResourceLocation.parse(values[2]),
                        values.length == 3 || values[3].isEmpty() ? null : ResourceLocation.parse(values[3]),
                        values.length == 3 || values[4].isEmpty() ? null : ResourceLocation.parse(values[4])));
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
                spec.controllerBaseTexture() == null ? "" : spec.controllerBaseTexture().toString(),
                spec.formedPortBaseTexture() == null ? "" : spec.formedPortBaseTexture().toString(),
                spec.controllerIdleOverlayTexture().toString(),
                spec.controllerActiveOverlayTexture().toString())));

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

    private static boolean replaceSnapshot(Map<ResourceLocation, MachineAppearanceSpec> replacement,
                                           long contentVersion, boolean persist) {
        if (replacement == null) {
            return false;
        }

        Map<ResourceLocation, MachineAppearanceSpec> copy = new LinkedHashMap<>();
        for (Map.Entry<ResourceLocation, MachineAppearanceSpec> entry : replacement.entrySet()) {
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

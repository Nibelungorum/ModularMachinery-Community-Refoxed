package cn.howxu.mmcr.internal.sync;

import cn.howxu.mmcr.api.recipe.RecipeCraftingContextPool;
import java.lang.reflect.Method;
import java.util.Map;

/**
 * Isolates optional client cache classes from common runtime snapshot tests.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class ClientRuntimeSnapshotBridge {
    private static long lastAppliedVersion = -1L;

    private ClientRuntimeSnapshotBridge() {
    }

    static synchronized boolean canApply(long version) {
        return version > lastAppliedVersion;
    }

    static synchronized void markApplied(long version) {
        lastAppliedVersion = version;
    }

    static boolean isIntegratedServer() {
        try {
            Class<?> minecraftClass = Class.forName("net.minecraft.client.Minecraft");
            Object minecraft = minecraftClass.getMethod("getInstance").invoke(null);
            if (minecraft == null) return false;
            return minecraftClass.getMethod("getSingleplayerServer").invoke(minecraft) != null;
        } catch (ClassNotFoundException ignored) {
            return false;
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Failed to detect integrated server", exception);
        }
    }

    public static synchronized void resetForConnection() {
        RecipeCraftingContextPool.onGlobalReload();
        resetClientCaches();
        lastAppliedVersion = -1L;
    }

    static synchronized void resetForTesting() {
        resetForConnection();
    }

    private static void resetClientCaches() {
        try {
            Class<?> applierClass = Class.forName("cn.howxu.mmcr.client.RuntimeContentClientApplier");
            Method reset = applierClass.getMethod("reset");
            reset.invoke(null);
        } catch (ClassNotFoundException ignored) {
            // Dedicated server/common test environments do not load client cache classes.
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Failed to reset runtime client caches", exception);
        }
    }

    static void apply(RuntimeContentSnapshot snapshot) {
        try {
            Class<?> applierClass = Class.forName("cn.howxu.mmcr.client.RuntimeContentClientApplier");
            Method apply = applierClass.getMethod("apply", Map.class, Map.class, long.class);
            apply.invoke(null, snapshot.controllerSpecs(), snapshot.appearances(), snapshot.contentVersion());
        } catch (ClassNotFoundException ignored) {
            // Dedicated server/common test environments do not load client cache classes.
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Failed to apply runtime snapshot to client caches", exception);
        }
    }

    static void validate(RuntimeContentSnapshot snapshot) {
        try {
            Class<?> applierClass = Class.forName("cn.howxu.mmcr.client.RuntimeContentClientApplier");
            Method validate = applierClass.getMethod("validate", Map.class, Map.class);
            validate.invoke(null, snapshot.controllerSpecs(), snapshot.appearances());
        } catch (ClassNotFoundException ignored) {
            // Dedicated server/common test environments do not load client cache classes.
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Failed to validate runtime snapshot client caches", exception);
        }
    }
}

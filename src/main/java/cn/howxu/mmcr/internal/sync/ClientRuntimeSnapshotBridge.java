package cn.howxu.mmcr.internal.sync;

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

    public static synchronized void resetForConnection() {
        lastAppliedVersion = -1L;
    }

    static synchronized void resetForTesting() {
        resetForConnection();
    }

    static void apply(RuntimeContentSnapshot snapshot) {
        try {
            Class<?> applierClass = Class.forName("cn.howxu.mmcr.client.RuntimeContentClientApplier");
            Method apply = applierClass.getMethod("apply", Map.class, Map.class);
            apply.invoke(null, snapshot.controllerSpecs(), snapshot.appearances());
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

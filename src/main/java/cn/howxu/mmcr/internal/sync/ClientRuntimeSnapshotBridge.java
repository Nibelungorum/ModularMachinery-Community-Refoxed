package cn.howxu.mmcr.internal.sync;

import java.lang.reflect.Method;
import java.util.Map;

/**
 * Isolates optional client cache classes from common runtime snapshot tests.
 *
 * @author howxu <dev@howxu.cn>
 */
final class ClientRuntimeSnapshotBridge {
    private ClientRuntimeSnapshotBridge() {
    }

    static void apply(Map<?, ?> controllerSpecs, Map<?, ?> appearances) {
        try {
            Class<?> applierClass = Class.forName("cn.howxu.mmcr.client.RuntimeContentClientApplier");
            Method apply = applierClass.getMethod("apply", Map.class, Map.class);
            apply.invoke(null, controllerSpecs, appearances);
        } catch (ClassNotFoundException ignored) {
            // Dedicated server/common test environments do not load client cache classes.
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Failed to apply runtime snapshot to client caches", exception);
        }
    }
}

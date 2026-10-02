package cn.howxu.mmcr.compat.appmek;

import cn.howxu.mmcr.api.capability.CapabilityDirections;
import cn.howxu.mmcr.api.capability.type.CapabilityBinding;
import cn.howxu.mmcr.test.TestBootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.net.URL;
import java.net.URLClassLoader;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** @author howxu <dev@howxu.cn> */
class AppMekBridgeTest {
    @BeforeAll
    static void bootstrap() throws Exception { TestBootstrap.bootstrap(); }

    @Test
    void missing_any_dependency_preserves_existing_bindings() {
        List<CapabilityBinding> base = List.of();
        for (boolean[] present : List.of(new boolean[]{true, true, false},
                new boolean[]{true, false, true}, new boolean[]{false, true, true})) {
            AppMekBridge bridge = AppMekBridgeBootstrap.selectForTesting(present[0], present[1], present[2]);
            assertThat(bridge.available()).isFalse();
            assertThat(bridge.appendBindings(base, CapabilityDirections.input(), false)).isSameAs(base);
            assertThat(bridge.patternRequest(new Object[0]).capabilities()).isEmpty();
        }
    }

    @Test
    void unavailable_bridge_loads_when_foreign_and_loaded_classes_are_blocked() throws Exception {
        URL classes = AppMekBridgeBootstrap.class.getProtectionDomain().getCodeSource().getLocation();
        try (URLClassLoader loader = new URLClassLoader(new URL[]{classes}, getClass().getClassLoader()) {
            @Override protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
                if (name.startsWith("me.ramidzkh.mekae2.") || name.startsWith("cn.howxu.mmcr.compat.appmek.loaded.")) {
                    throw new ClassNotFoundException(name);
                }
                if (name.startsWith("cn.howxu.mmcr.compat.appmek.")) {
                    synchronized (getClassLoadingLock(name)) {
                        Class<?> loaded = findLoadedClass(name);
                        if (loaded == null) loaded = findClass(name);
                        if (resolve) resolveClass(loaded);
                        return loaded;
                    }
                }
                return super.loadClass(name, resolve);
            }
        }) {
            Class<?> bootstrap = loader.loadClass("cn.howxu.mmcr.compat.appmek.AppMekBridgeBootstrap");
            Object selected = bootstrap.getMethod("selectForTesting", boolean.class, boolean.class, boolean.class)
                    .invoke(null, true, true, false);
            Class<?> bridge = loader.loadClass("cn.howxu.mmcr.compat.appmek.AppMekBridge");
            assertThat(bridge.getMethod("available").invoke(selected)).isEqualTo(false);
        }
    }
}

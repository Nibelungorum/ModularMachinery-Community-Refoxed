package cn.howxu.mmcr.compat.ae2lt;

import cn.howxu.mmcr.api.capability.CapabilityDirections;
import cn.howxu.mmcr.api.capability.type.CapabilityBinding;
import org.junit.jupiter.api.Test;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

/** @author howxu <dev@howxu.cn> */
class LightningBridgeTest {
    @Test void missingDependencyDoesNotResolveNativeBridge() throws Exception {
        URL classes = LightningBridgeBootstrap.class.getProtectionDomain().getCodeSource().getLocation();
        try (URLClassLoader loader = new URLClassLoader(new URL[]{classes}, getClass().getClassLoader()) {
            @Override protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
                if (name.startsWith("com.moakiee.ae2lt.") || name.startsWith("cn.howxu.mmcr.compat.ae2lt.loaded.")) throw new ClassNotFoundException(name);
                if (name.startsWith("cn.howxu.mmcr.compat.ae2lt.")) {
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
            Class<?> bootstrap = loader.loadClass("cn.howxu.mmcr.compat.ae2lt.LightningBridgeBootstrap");
            Class<?> contract = loader.loadClass("cn.howxu.mmcr.compat.ae2lt.LightningBridge");
            for (boolean[] loaded : List.of(new boolean[]{false, true}, new boolean[]{true, false}, new boolean[]{false, false})) {
                Object selected = bootstrap.getMethod("selectForTesting", boolean.class, boolean.class).invoke(null, loaded[0], loaded[1]);
                assertThat(contract.getMethod("available").invoke(selected)).isEqualTo(false);
            }
        }
        var absent = LightningBridgeBootstrap.selectForTesting(false, false);
        var base = List.<CapabilityBinding>of();
        assertThat(absent.appendBindings(base, CapabilityDirections.input())).isSameAs(base);
        assertThat(absent.patternInputs(new Object())).isEmpty();
        assertThat(absent.patternOutput(new Object(), 1L)).isEmpty();
    }
}

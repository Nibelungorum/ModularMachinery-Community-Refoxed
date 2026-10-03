package cn.howxu.mmcr.compat.fluxnetworks;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Verifies absent-mod selection without linking native Flux Networks classes.
 * @author howxu <dev@howxu.cn>
 */
class FluxNetworksBridgeTest {
    @Test
    void absentDependencySelectsAnEmptyBridge() {
        FluxNetworksBridge bridge = FluxNetworksBridgeBootstrap.selectForTesting(false);

        assertThat(bridge.available()).isFalse();
        assertThat(bridge.portKinds()).isEmpty();
        assertThat(FluxNetworksBridgeBootstrap.selectForTesting(false)).isSameAs(bridge);
    }

    @Test
    void absentSelectionWorksWithNativeAndLoadedClassesDenied() throws Exception {
        NativeDenyingClassLoader loader = new NativeDenyingClassLoader();
        Class<?> bootstrap = Class.forName(FluxNetworksBridgeBootstrap.class.getName(), true, loader);
        Class<?> contract = Class.forName(FluxNetworksBridge.class.getName(), true, loader);
        Object bridge = bootstrap.getMethod("selectForTesting", boolean.class).invoke(null, false);

        assertThat(contract.getMethod("available").invoke(bridge)).isEqualTo(false);
        assertThat((List<?>) contract.getMethod("portKinds").invoke(bridge)).isEmpty();
        assertThat(loader.denied).isEmpty();
    }

    /** Loads the neutral bridge in isolation and records attempted optional class links.
     * @author howxu <dev@howxu.cn>
     */
    private static final class NativeDenyingClassLoader extends ClassLoader {
        private final List<String> denied = new ArrayList<>();

        private NativeDenyingClassLoader() {
            super(FluxNetworksBridgeTest.class.getClassLoader());
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            if (name.startsWith("sonar.fluxnetworks.")
                    || name.startsWith("cn.howxu.mmcr.compat.fluxnetworks.loaded.")) {
                denied.add(name);
                throw new ClassNotFoundException(name);
            }
            if (!name.equals(FluxNetworksBridge.class.getName())
                    && !name.equals(FluxNetworksBridgeBootstrap.class.getName())
                    && !name.equals(UnavailableFluxNetworksBridge.class.getName())) {
                return super.loadClass(name, resolve);
            }
            synchronized (getClassLoadingLock(name)) {
                Class<?> loaded = findLoadedClass(name);
                if (loaded == null) {
                    try (InputStream source = getParent().getResourceAsStream(name.replace('.', '/') + ".class")) {
                        if (source == null) throw new ClassNotFoundException(name);
                        byte[] bytes = source.readAllBytes();
                        loaded = defineClass(name, bytes, 0, bytes.length);
                    } catch (IOException exception) {
                        throw new ClassNotFoundException(name, exception);
                    }
                }
                if (resolve) resolveClass(loaded);
                return loaded;
            }
        }
    }
}

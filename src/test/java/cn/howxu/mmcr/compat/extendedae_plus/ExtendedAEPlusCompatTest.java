package cn.howxu.mmcr.compat.extendedae_plus;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** @author howxu <dev@howxu.cn> */
class ExtendedAEPlusCompatTest {
    @Test
    void unavailableIntegrationContributesNoPorts() {
        assertThat(ExtendedAEPlusCompat.selectKindsForTesting(false)).isEmpty();
    }

    @Test
    void neutralEntryPointLoadsWithoutOptionalImplementation() throws Exception {
        Class<?> entryPoint = Class.forName(ExtendedAEPlusCompat.class.getName(), true,
                new WithoutOptionalIntegrationClassLoader());

        assertThat((List<?>) entryPoint.getMethod("selectKindsForTesting", boolean.class).invoke(null, false))
                .isEmpty();
        assertThatThrownBy(() -> entryPoint.getMethod("selectKindsForTesting", boolean.class).invoke(null, true))
                .hasRootCauseInstanceOf(ClassNotFoundException.class);
    }

    /** @author howxu <dev@howxu.cn> */
    private static final class WithoutOptionalIntegrationClassLoader extends ClassLoader {
        private WithoutOptionalIntegrationClassLoader() {
            super(ExtendedAEPlusCompatTest.class.getClassLoader());
        }

        @Override
        protected synchronized Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            if (name.startsWith("appeng.") || name.startsWith("com.extendedae_plus.")
                    || name.startsWith("com.glodblock.github.extendedae.")
                    || name.startsWith("cn.howxu.mmcr.compat.extendedae_plus.loaded.")) {
                throw new ClassNotFoundException(name);
            }
            if (!name.equals(ExtendedAEPlusCompat.class.getName())) return super.loadClass(name, resolve);

            Class<?> loaded = findLoadedClass(name);
            if (loaded == null) {
                try (InputStream input = getResourceAsStream(name.replace('.', '/') + ".class")) {
                    if (input == null) throw new ClassNotFoundException(name);
                    byte[] bytecode = input.readAllBytes();
                    loaded = defineClass(name, bytecode, 0, bytecode.length);
                } catch (IOException exception) {
                    throw new ClassNotFoundException(name, exception);
                }
            }
            if (resolve) resolveClass(loaded);
            return loaded;
        }
    }
}

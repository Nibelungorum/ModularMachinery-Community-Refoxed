package cn.howxu.mmcr.compat.fluxnetworks;

import cn.howxu.mmcr.test.TestBootstrap;
import com.electronwill.nightconfig.core.CommentedConfig;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.config.IConfigSpec;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.fml.loading.FMLLoader;
import net.neoforged.neoforge.common.ModConfigSpec;
import sonar.fluxnetworks.FluxConfig;
import sun.misc.Unsafe;

import java.lang.reflect.Field;
import java.nio.file.Path;

/**
 * Initializes the plain JUnit environment for real Flux handlers and capabilities.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class FluxNetworksTestBootstrap {
    private FluxNetworksTestBootstrap() {
    }

    /**
     * Call from {@code BeforeAll}, before constructing any Flux handler or device.
     * Each call reopens capability registration; existing loaded Flux configs are preserved.
     */
    public static synchronized void bootstrap() throws Exception {
        if (FMLLoader.getDist() == null) {
            Field dist = FMLLoader.class.getDeclaredField("dist");
            dist.setAccessible(true);
            dist.set(null, Dist.DEDICATED_SERVER);
        }
        if (FMLEnvironment.dist == null) {
            // Another test may already have captured the loader's null dist in this final field.
            Field unsafeField = Unsafe.class.getDeclaredField("theUnsafe");
            unsafeField.setAccessible(true);
            Unsafe unsafe = (Unsafe) unsafeField.get(null);
            Field dist = FMLEnvironment.class.getDeclaredField("dist");
            unsafe.putObjectVolatile(unsafe.staticFieldBase(dist), unsafe.staticFieldOffset(dist),
                    Dist.DEDICATED_SERVER);
        }
        TestBootstrap.bootstrapCapabilities();
        loadConfig("COMMON");
        loadConfig("SERVER");
    }

    private static void loadConfig(String type) throws ReflectiveOperationException {
        Field specField = FluxConfig.class.getDeclaredField(type + "_SPEC");
        specField.setAccessible(true);
        ModConfigSpec spec = (ModConfigSpec) specField.get(null);
        if (spec.isLoaded()) return;

        CommentedConfig config = CommentedConfig.inMemory();
        spec.correct(config);
        var constructor = Class.forName("net.neoforged.fml.config.LoadedConfig")
                .getDeclaredConstructor(CommentedConfig.class, Path.class, ModConfig.class);
        constructor.setAccessible(true);
        spec.acceptConfig((IConfigSpec.ILoadedConfig) constructor.newInstance(config, null, null));

        // acceptConfig loads spec values; Flux copies them into runtime fields in its own load().
        Field configField = FluxConfig.class.getDeclaredField(type + "_CONFIG");
        configField.setAccessible(true);
        Object settings = configField.get(null);
        var load = settings.getClass().getDeclaredMethod("load");
        load.setAccessible(true);
        load.invoke(settings);
    }
}

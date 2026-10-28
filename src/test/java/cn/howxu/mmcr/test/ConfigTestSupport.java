package cn.howxu.mmcr.test;

import cn.howxu.mmcr.config.ServerConfig;
import cn.howxu.mmcr.internal.runtime.MachineWorkMode;
import com.electronwill.nightconfig.core.CommentedConfig;
import net.neoforged.fml.config.IConfigSpec;

/** Provides configuration setup that simulates entering a world in tests.
 * @author howxu <dev@howxu.cn>
 */
public final class ConfigTestSupport {
    private ConfigTestSupport() {
    }

    public static WorkModeScope openMachineWorkMode(MachineWorkMode mode) throws ReflectiveOperationException {
        boolean loaded = ServerConfig.SPEC.isLoaded();
        if (!loaded) {
            var config = CommentedConfig.inMemory();
            ServerConfig.SPEC.correct(config);
            var constructor = Class.forName("net.neoforged.fml.config.LoadedConfig").getDeclaredConstructors()[0];
            constructor.setAccessible(true);
            ServerConfig.SPEC.acceptConfig((IConfigSpec.ILoadedConfig) constructor.newInstance(config, null, null));
        }
        ServerConfig.MACHINE_WORK_MODE.clearCache();
        var previous = ServerConfig.MACHINE_WORK_MODE.get();
        setMachineWorkMode(mode);
        return new WorkModeScope(previous, loaded);
    }

    /** Restores the test's configuration state, including an originally unloaded spec.
     * @author howxu <dev@howxu.cn>
     */
    public record WorkModeScope(MachineWorkMode previous, boolean loaded) implements AutoCloseable {
        @Override public void close() {
            if (loaded) setMachineWorkMode(previous);
            else ServerConfig.SPEC.acceptConfig(null);
        }
    }

    public static void setMachineWorkMode(MachineWorkMode mode) {
        ServerConfig.MACHINE_WORK_MODE.clearCache();
        ServerConfig.MACHINE_WORK_MODE.set(mode);
    }
}

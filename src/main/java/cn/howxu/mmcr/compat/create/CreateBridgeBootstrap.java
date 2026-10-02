package cn.howxu.mmcr.compat.create;

import cn.howxu.mmcr.api.compat.create.CreateFailureReasons;
import java.util.Objects;
import net.neoforged.fml.ModList;

/** Selects the optional implementation only after the mod presence check.
 * @author howxu <dev@howxu.cn>
 */
public final class CreateBridgeBootstrap {
    private static final String LOADED_BRIDGE = "cn.howxu.mmcr.compat.create.loaded.LoadedCreateBridge";
    private static volatile CreateBridge bridge;
    private static volatile CreateBridge testingBridge;

    private CreateBridgeBootstrap() {
    }

    public static void bootstrap() {
        CreateFailureReasons.register();
    }

    static CreateBridge bridge() {
        if (testingBridge != null) return testingBridge;
        if (bridge != null) return bridge;
        synchronized (CreateBridgeBootstrap.class) {
            if (bridge == null) {
                ModList mods = ModList.get();
                bridge = selectForTesting(mods != null && mods.isLoaded("create"));
            }
            return bridge;
        }
    }

    public static CreateBridge selectForTesting(boolean createLoaded) {
        if (!createLoaded) return UnavailableCreateBridge.INSTANCE;
        try {
            return (CreateBridge) Class.forName(LOADED_BRIDGE).getDeclaredConstructor().newInstance();
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Unable to load Create integration", exception);
        }
    }

    public static void installForTesting(CreateBridge replacement) {
        testingBridge = Objects.requireNonNull(replacement);
    }

    public static synchronized void resetForTesting() {
        testingBridge = null;
        bridge = null;
    }
}

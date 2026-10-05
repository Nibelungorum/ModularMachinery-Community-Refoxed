package cn.howxu.mmcr.compat.pneumaticcraft;

import java.util.Objects;
import net.neoforged.fml.ModList;

/** Selects the native bridge only after checking mod availability.
 * @author howxu <dev@howxu.cn>
 */
public final class PneumaticCraftBridgeBootstrap {
    private static final String LOADED_BRIDGE =
            "cn.howxu.mmcr.compat.pneumaticcraft.loaded.LoadedPneumaticCraftBridge";
    private static volatile PneumaticCraftBridge bridge;
    private static volatile PneumaticCraftBridge testingBridge;

    private PneumaticCraftBridgeBootstrap() {
    }

    public static void bootstrap() {
        AirFailureReasons.register();
    }

    static PneumaticCraftBridge bridge() {
        if (testingBridge != null) return testingBridge;
        if (bridge != null) return bridge;
        synchronized (PneumaticCraftBridgeBootstrap.class) {
            if (bridge == null) {
                ModList mods = ModList.get();
                bridge = selectForTesting(mods != null && mods.isLoaded(PneumaticIds.MOD_ID));
            }
            return bridge;
        }
    }

    public static PneumaticCraftBridge selectForTesting(boolean loaded) {
        if (!loaded) return UnavailablePneumaticCraftBridge.INSTANCE;
        try {
            return (PneumaticCraftBridge) Class.forName(LOADED_BRIDGE).getDeclaredConstructor().newInstance();
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Unable to load PneumaticCraft integration", exception);
        }
    }

    public static void installForTesting(PneumaticCraftBridge replacement) {
        testingBridge = Objects.requireNonNull(replacement);
    }

    public static synchronized void resetForTesting() {
        testingBridge = null;
        bridge = null;
    }
}

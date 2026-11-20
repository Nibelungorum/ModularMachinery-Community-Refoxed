package cn.howxu.mmcr.compat.athena;

import net.neoforged.fml.ModList;

/**
 * Lazily selects the optional Athena bridge without linking its implementation early.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class AthenaModelBridgeBootstrap {
    private static final String ATHENA_MOD_ID = "athena";
    private static final String LOADED_BRIDGE = "cn.howxu.mmcr.compat.athena.loaded.LoadedAthenaModelBridge";
    private static volatile AthenaModelBridge bridge;

    private AthenaModelBridgeBootstrap() {
    }

    static AthenaModelBridge bridge() {
        AthenaModelBridge selected = bridge;
        if (selected != null) return selected;

        synchronized (AthenaModelBridgeBootstrap.class) {
            if (bridge == null) bridge = load();
            return bridge;
        }
    }

    public static AthenaModelBridge selectForTesting(boolean athenaLoaded) {
        return athenaLoaded ? loadLoadedBridge() : UnavailableAthenaModelBridge.INSTANCE;
    }

    static boolean isAthenaLoaded() {
        ModList mods = ModList.get();
        return mods != null && mods.isLoaded(ATHENA_MOD_ID);
    }

    private static AthenaModelBridge load() {
        return isAthenaLoaded() ? loadLoadedBridge() : UnavailableAthenaModelBridge.INSTANCE;
    }

    private static AthenaModelBridge loadLoadedBridge() {
        try {
            return (AthenaModelBridge) Class.forName(LOADED_BRIDGE).getDeclaredConstructor().newInstance();
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Unable to load Athena integration", exception);
        }
    }
}

package cn.howxu.mmcr.compat.fluxnetworks;

import net.neoforged.fml.ModList;

/** Selects the optional implementation only after the mod presence check.
 * @author howxu <dev@howxu.cn>
 */
public final class FluxNetworksBridgeBootstrap {
    private static final String LOADED = "cn.howxu.mmcr.compat.fluxnetworks.loaded.LoadedFluxNetworksBridge";
    private static volatile FluxNetworksBridge bridge;

    private FluxNetworksBridgeBootstrap() {
    }

    static FluxNetworksBridge bridge() {
        if (bridge != null) return bridge;
        synchronized (FluxNetworksBridgeBootstrap.class) {
            if (bridge == null) {
                ModList mods = ModList.get();
                bridge = selectForTesting(mods != null && mods.isLoaded(FluxNetworksIds.MOD_ID));
            }
            return bridge;
        }
    }

    public static FluxNetworksBridge selectForTesting(boolean loaded) {
        if (!loaded) return UnavailableFluxNetworksBridge.INSTANCE;
        try {
            return (FluxNetworksBridge) Class.forName(LOADED).getDeclaredConstructor().newInstance();
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Unable to load Flux Networks integration", exception);
        }
    }
}

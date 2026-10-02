package cn.howxu.mmcr.compat.appmek;

import net.neoforged.fml.ModList;

/**
 * Selects the AppMek implementation only when all three integrations are present.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class AppMekBridgeBootstrap {
    private static volatile AppMekBridge bridge;

    private AppMekBridgeBootstrap() {}

    public static AppMekBridge get() {
        AppMekBridge selected = bridge;
        if (selected != null) return selected;
        synchronized (AppMekBridgeBootstrap.class) {
            if (bridge == null) {
                ModList mods = ModList.get();
                bridge = mods == null ? UnavailableAppMekBridge.INSTANCE
                        : selectForTesting(mods.isLoaded("ae2"), mods.isLoaded("mekanism"), mods.isLoaded("appmek"));
            }
            return bridge;
        }
    }

    public static AppMekBridge selectForTesting(boolean ae2, boolean mekanism, boolean appmek) {
        if (!ae2 || !mekanism || !appmek) return UnavailableAppMekBridge.INSTANCE;
        try {
            return (AppMekBridge) Class.forName("cn.howxu.mmcr.compat.appmek.loaded.LoadedAppMekBridge")
                    .getDeclaredConstructor().newInstance();
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Unable to load Applied Mekanistics integration", exception);
        }
    }

    public static synchronized void resetForTesting() { bridge = null; }
}

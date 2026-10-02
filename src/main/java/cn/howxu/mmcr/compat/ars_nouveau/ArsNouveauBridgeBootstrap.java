package cn.howxu.mmcr.compat.ars_nouveau;

import net.neoforged.fml.ModList;

import java.util.Objects;

/**
 * Lazily selects the Ars bridge without linking native classes when the mod is absent.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class ArsNouveauBridgeBootstrap {
    private static final String LOADED_BRIDGE = "cn.howxu.mmcr.compat.ars_nouveau.loaded.LoadedArsNouveauBridge";
    private static volatile ArsNouveauBridge bridge;
    private static volatile ArsNouveauBridge testingBridge;

    private ArsNouveauBridgeBootstrap() {
    }

    public static void bootstrap() {
        if (get().available()) SourceRequirement.installHandler(new SourceRequirementHandler());
        else SourceRequirement.installUnavailableHandler();
    }

    public static ArsNouveauBridge get() {
        ArsNouveauBridge override = testingBridge;
        if (override != null) return override;

        ArsNouveauBridge selected = bridge;
        if (selected != null) return selected;

        synchronized (ArsNouveauBridgeBootstrap.class) {
            if (bridge == null) bridge = load();
            return bridge;
        }
    }

    public static ArsNouveauBridge selectForTesting(boolean loaded) {
        return loaded ? loadLoadedBridge() : UnavailableArsNouveauBridge.INSTANCE;
    }

    public static void installForTesting(ArsNouveauBridge testingBridge) {
        ArsNouveauBridgeBootstrap.testingBridge = Objects.requireNonNull(testingBridge);
    }

    public static synchronized void resetForTesting() {
        testingBridge = null;
        bridge = null;
    }

    private static ArsNouveauBridge load() {
        ModList mods = ModList.get();
        return mods != null && mods.isLoaded("ars_nouveau") ? loadLoadedBridge() : UnavailableArsNouveauBridge.INSTANCE;
    }

    private static ArsNouveauBridge loadLoadedBridge() {
        try {
            return (ArsNouveauBridge) Class.forName(LOADED_BRIDGE).getDeclaredConstructor().newInstance();
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Unable to load Ars Nouveau integration", exception);
        }
    }
}

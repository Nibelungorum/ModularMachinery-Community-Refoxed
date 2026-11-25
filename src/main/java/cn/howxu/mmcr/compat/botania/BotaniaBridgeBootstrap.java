package cn.howxu.mmcr.compat.botania;

import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;

import java.lang.annotation.ElementType;
import java.util.Objects;

/** Selects the native bridge only for official Botania. @author howxu <dev@howxu.cn> */
public final class BotaniaBridgeBootstrap {
    private static final String BOTANIA_ID = "botania";
    private static final String OFFICIAL_ENTRYPOINT = "vazkii.botania.neoforge.NeoForgeCommonInitializer";
    private static final String LOADED_BRIDGE = "cn.howxu.mmcr.compat.botania.loaded.LoadedBotaniaBridge";
    private static volatile BotaniaBridge bridge;
    private static volatile BotaniaBridge testingBridge;

    private BotaniaBridgeBootstrap() {}

    public static void bootstrap() {
        ManaFailureReasons.register();
        BotaniaRecipeTypes.register();
        if (get().available()) ManaRequirement.installHandler(new ManaRequirementHandler());
        else ManaRequirement.installUnavailableHandler();
    }

    public static BotaniaBridge get() {
        BotaniaBridge override = testingBridge;
        if (override != null) return override;
        BotaniaBridge selected = bridge;
        if (selected != null) return selected;
        synchronized (BotaniaBridgeBootstrap.class) {
            if (bridge == null) bridge = load();
            return bridge;
        }
    }

    public static BotaniaBridge selectForTesting(boolean loaded) {
        return loaded ? loadLoadedBridge() : UnavailableBotaniaBridge.INSTANCE;
    }

    public static void installForTesting(BotaniaBridge override) {
        testingBridge = Objects.requireNonNull(override);
    }

    public static synchronized void resetForTesting() {
        testingBridge = null;
        bridge = null;
    }

    private static BotaniaBridge load() {
        ModList mods = ModList.get();
        if (mods == null || !mods.isLoaded(BOTANIA_ID)) return UnavailableBotaniaBridge.INSTANCE;

        // Botania Revived shares the mod ID but not the official API packages.
        var file = mods.getModFileById(BOTANIA_ID);
        boolean official = file != null && file.getFile().getScanResult().getAnnotatedBy(Mod.class, ElementType.TYPE)
                .anyMatch(entry -> BOTANIA_ID.equals(entry.annotationData().get("value"))
                        && OFFICIAL_ENTRYPOINT.equals(entry.clazz().getClassName()));
        return official ? loadLoadedBridge() : UnavailableBotaniaBridge.INSTANCE;
    }

    private static BotaniaBridge loadLoadedBridge() {
        try {
            return (BotaniaBridge) Class.forName(LOADED_BRIDGE).getDeclaredConstructor().newInstance();
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Unable to load Botania integration", exception);
        }
    }
}

package cn.howxu.mmcr.compat.botania;

import net.neoforged.fml.ModList;

import java.util.Objects;

/** Selects the native bridge only after the mod-presence guard. @author howxu <dev@howxu.cn> */
public final class BotaniaBridgeBootstrap {
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
        return mods != null && mods.isLoaded("botania") ? loadLoadedBridge() : UnavailableBotaniaBridge.INSTANCE;
    }

    private static BotaniaBridge loadLoadedBridge() {
        try {
            return (BotaniaBridge) Class.forName(LOADED_BRIDGE).getDeclaredConstructor().newInstance();
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Unable to load Botania integration", exception);
        }
    }
}

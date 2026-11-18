package cn.howxu.mmcr.compat.ae2lt;

import cn.howxu.mmcr.compat.appliedenergistics2.loaded.adapter.AE2ResourceFamily;
import cn.howxu.mmcr.compat.appliedenergistics2.loaded.adapter.AE2ResourceFamilies;
import net.neoforged.fml.ModList;

import java.util.List;
import java.util.Objects;

/** Selects the native implementation only when both optional mods are present.
 * @author howxu <dev@howxu.cn>
 */
public final class LightningBridgeBootstrap {
    private static final String LOADED_BRIDGE = "cn.howxu.mmcr.compat.ae2lt.loaded.LoadedLightningBridge";
    private static volatile LightningBridge bridge;
    private static volatile LightningBridge testingBridge;

    private LightningBridgeBootstrap() {
    }

    public static void bootstrap() {
        LightningFailureReasons.register();
    }

    static LightningBridge bridge() {
        LightningBridge override = testingBridge;
        if (override != null) return override;
        LightningBridge selected = bridge;
        if (selected != null) return selected;
        synchronized (LightningBridgeBootstrap.class) {
            if (bridge == null) {
                ModList mods = ModList.get();
                bridge = selectForTesting(mods != null && mods.isLoaded("ae2"),
                        mods != null && mods.isLoaded("ae2lt"));
            }
            return bridge;
        }
    }

    public static LightningBridge selectForTesting(boolean ae2Loaded, boolean ae2ltLoaded) {
        if (!ae2Loaded || !ae2ltLoaded) return Unavailable.INSTANCE;
        try {
            return (LightningBridge) Class.forName(LOADED_BRIDGE).getDeclaredConstructor().newInstance();
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Unable to load AE2LT integration", exception);
        }
    }

    public static LightningBridge selectForTesting(boolean lightningLoaded) {
        return selectForTesting(lightningLoaded, lightningLoaded);
    }

    public static void installForTesting(LightningBridge testingBridge) {
        LightningBridgeBootstrap.testingBridge = Objects.requireNonNull(testingBridge, "testingBridge");
        AE2ResourceFamilies.resetForTesting();
    }

    public static synchronized void resetForTesting() {
        testingBridge = null;
        bridge = null;
        AE2ResourceFamilies.resetForTesting();
    }

    /** Empty bridge never resolves the native implementation.
     * @author howxu <dev@howxu.cn>
     */
    private enum Unavailable implements LightningBridge {
        INSTANCE;

        @Override
        public boolean available() {
            return false;
        }

        @Override
        public void registerCapabilityDefinitions() {
        }

        @Override
        public List<AE2ResourceFamily<?>> resourceFamilies() {
            return List.of();
        }
    }
}

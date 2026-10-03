package cn.howxu.mmcr.compat.extendedae_plus;

import cn.howxu.mmcr.internal.port.IOPortKind;
import java.util.List;
import net.neoforged.fml.ModList;

/** @author howxu <dev@howxu.cn> */
public final class ExtendedAEPlusCompat {
    private static final String LOADED_KIND =
            "cn.howxu.mmcr.compat.extendedae_plus.loaded.MirrorPatternInterfaceKind";
    private static volatile List<IOPortKind> kinds;

    private ExtendedAEPlusCompat() {
    }

    public static List<IOPortKind> portKinds() {
        List<IOPortKind> selected = kinds;
        if (selected != null) return selected;
        synchronized (ExtendedAEPlusCompat.class) {
            if (kinds == null) {
                ModList mods = ModList.get();
                kinds = selectKindsForTesting(mods != null && mods.isLoaded("ae2")
                        && mods.isLoaded("extendedae") && mods.isLoaded("extendedae_plus"));
            }
            return kinds;
        }
    }

    public static List<IOPortKind> selectKindsForTesting(boolean loaded) {
        if (!loaded) return List.of();
        try {
            return List.of((IOPortKind) Class.forName(LOADED_KIND).getField("INSTANCE").get(null));
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Unable to load ExtendedAE Plus integration", exception);
        }
    }
}

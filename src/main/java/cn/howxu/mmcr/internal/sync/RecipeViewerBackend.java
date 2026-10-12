package cn.howxu.mmcr.internal.sync;

import net.neoforged.fml.ModList;

/**
 * Selects the active recipe viewer without linking either optional viewer's classes.
 *
 * @author howxu <dev@howxu.cn>
 */
public enum RecipeViewerBackend {
    NONE,
    JEI,
    EMI;

    public static RecipeViewerBackend select(boolean jei, boolean emi) {
        return emi ? EMI : jei ? JEI : NONE;
    }

    public static RecipeViewerBackend current() {
        ModList modList = ModList.get();
        return modList == null ? NONE : select(modList.isLoaded("jei"), modList.isLoaded("emi"));
    }
}

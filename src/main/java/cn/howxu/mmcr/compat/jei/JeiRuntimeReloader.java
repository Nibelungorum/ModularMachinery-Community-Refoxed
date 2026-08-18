package cn.howxu.mmcr.compat.jei;

import cn.howxu.mmcr.internal.sync.RuntimeContentSnapshot;
import net.neoforged.fml.ModList;

/**
 * Runtime JEI reload bridge. Safe to call before JEI has initialized or when JEI is absent.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class JeiRuntimeReloader {

    private JeiRuntimeReloader() {
    }

    public static void reloadIfAvailable(RuntimeContentSnapshot snapshot) {
        if (!ModList.get().isLoaded("jei")) return;
        // Task 5 supplies the client-side content install path that JEI can reload from.
    }
}

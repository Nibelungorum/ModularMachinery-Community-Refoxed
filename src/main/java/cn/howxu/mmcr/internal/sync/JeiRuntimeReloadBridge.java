package cn.howxu.mmcr.internal.sync;

import cn.howxu.mmcr.MMCR;
import net.neoforged.fml.ModList;

import java.lang.reflect.Method;

/**
 * Keeps the common runtime payload free of direct references to optional JEI classes.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class JeiRuntimeReloadBridge {
    private static final String JEI_MOD_ID = "jei";
    private static final String RELOADER_CLASS = "cn.howxu.mmcr.compat.jei.JeiRuntimeReloader";

    private JeiRuntimeReloadBridge() {
    }

    public static void reloadIfAvailable(RuntimeContentSnapshot snapshot) {
        ModList modList = ModList.get();
        if (modList == null || !modList.isLoaded(JEI_MOD_ID)) return;
        try {
            Class<?> reloaderClass = Class.forName(RELOADER_CLASS);
            Method reload = reloaderClass.getMethod("reloadIfAvailable", RuntimeContentSnapshot.class);
            reload.invoke(null, snapshot);
        } catch (ClassNotFoundException ignored) {
            // JEI is optional; class can be absent on clients without the integration loaded.
        } catch (ReflectiveOperationException exception) {
            MMCR.LOG.warn("Failed to reload JEI runtime content", exception);
        }
    }
}

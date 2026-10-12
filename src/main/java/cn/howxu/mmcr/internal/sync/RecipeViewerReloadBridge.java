package cn.howxu.mmcr.internal.sync;

import cn.howxu.mmcr.MMCR;

import java.lang.reflect.InvocationTargetException;

/** Common-side dispatch without linking optional viewer or client classes.
 * @author howxu <dev@howxu.cn>
 */
public final class RecipeViewerReloadBridge {
    private RecipeViewerReloadBridge() {}

    public static void reloadIfAvailable(RuntimeContentSnapshot snapshot) {
        String implementation = implementation();
        if (implementation != null) invoke(implementation, "reloadIfAvailable", new Class<?>[]{RuntimeContentSnapshot.class}, snapshot);
    }

    /** Consumes the information version already published by RecipeViewerRegistration. */
    public static void informationChanged() {
        String implementation = implementation();
        if (implementation != null) invoke(implementation, "informationChanged", new Class<?>[0]);
    }

    public static void resetForConnection() {
        String implementation = implementation();
        if (implementation == null) return;
        invoke("cn.howxu.mmcr.internal.client.RecipeViewerRegistration", "resetForConnection", new Class<?>[0]);
        invoke(implementation, "resetForConnection", new Class<?>[0]);
    }

    private static String implementation() {
        return switch (RecipeViewerBackend.current()) {
            case NONE -> null;
            case EMI -> "cn.howxu.mmcr.compat.emi.EmiRuntimeReloader";
            case JEI -> "cn.howxu.mmcr.compat.jei.JeiRuntimeReloader";
        };
    }

    private static void invoke(String className, String method, Class<?>[] parameters, Object... arguments) {
        try {
            Class.forName(className).getMethod(method, parameters).invoke(null, arguments);
        } catch (InvocationTargetException exception) {
            MMCR.LOG.error("Recipe viewer operation {} failed in {}", method, className, exception.getCause());
        } catch (ReflectiveOperationException | LinkageError exception) {
            MMCR.LOG.error("Cannot invoke recipe viewer operation {} in {}", method, className, exception);
        }
    }
}

package cn.howxu.mmcr.internal.registration;

import net.neoforged.fml.loading.FMLLoader;

import java.lang.reflect.InvocationTargetException;

/** Invokes development-only sources that are excluded from production jars.
 * @author howxu <dev@howxu.cn>
 */
public final class OptionalSourceRegistration {
    private OptionalSourceRegistration() {
    }

    @SuppressWarnings("unchecked")
    public static <T> T invokeDevelopmentSource(String className, String methodName,
                                                 Class<?>[] parameterTypes, Object... arguments) {
        if (FMLLoader.getCurrent().isProduction()) return null;
        return invokeOptionalSource(className, methodName, parameterTypes, arguments);
    }

    /** Invokes a source that may be absent because one of its optional dependencies is unavailable.
     * @author howxu <dev@howxu.cn>
     */
    @SuppressWarnings("unchecked")
    public static <T> T invokeOptionalSource(String className, String methodName,
                                              Class<?>[] parameterTypes, Object... arguments) {
        try {
            return (T) Class.forName(className).getMethod(methodName, parameterTypes).invoke(null, arguments);
        } catch (ClassNotFoundException ignored) {
            // Optional integration sources may be absent from the current classpath.
            return null;
        } catch (ReflectiveOperationException e) {
            Throwable cause = e instanceof InvocationTargetException invocation
                    ? invocation.getCause() : e;
            throw new IllegalStateException("Unable to invoke optional source " + className + "." + methodName,
                    cause);
        }
    }
}

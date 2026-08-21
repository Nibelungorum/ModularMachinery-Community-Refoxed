package cn.howxu.mmcr.internal.registration;

import cn.howxu.mmcr.api.publicapi.event.MMCRMachineDefinationsEvent;
import cn.howxu.mmcr.api.publicapi.event.MMCRMachineRecipesEvent;
import cn.howxu.mmcr.api.publicapi.event.MMCRMachineStructuresEvent;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;

/** Owns optional GameTest source-set loading and registration.
 * @author howxu <dev@howxu.cn>
 */
public final class GameTestRegistration {
    private static final String GAME_TEST_REGISTRY = "cn.howxu.mmcr.GameTestRegistry";

    private GameTestRegistration() {
    }

    public static void registerStartupSources(MMCRMachineDefinationsEvent definitions,
                                              MMCRMachineStructuresEvent structures,
                                              MMCRMachineRecipesEvent recipes) {
        if (definitions != null) {
            invokeOptionalSource(GAME_TEST_REGISTRY, "registerMachineDefinitions",
                    new Class<?>[]{MMCRMachineDefinationsEvent.class}, definitions);
        }
        if (structures != null) {
            invokeOptionalSource(GAME_TEST_REGISTRY, "registerMachineStructures",
                    new Class<?>[]{MMCRMachineStructuresEvent.class}, structures);
        }
        if (recipes != null) {
            invokeOptionalSource(GAME_TEST_REGISTRY, "registerRecipes",
                    new Class<?>[]{MMCRMachineRecipesEvent.class}, recipes);
        }
    }

    public static void registerTests(RegisterGameTestsEvent event) {
        invokeOptionalSource(GAME_TEST_REGISTRY, "registerAll",
                new Class<?>[]{RegisterGameTestsEvent.class}, event);
    }

    public static void invokeOptionalSourceForTesting(String className, String methodName,
                                                       Class<?>[] parameterTypes, Object... arguments) {
        invokeOptionalSource(className, methodName, parameterTypes, arguments);
    }

    private static void invokeOptionalSource(String className, String methodName,
                                             Class<?>[] parameterTypes, Object... arguments) {
        try {
            Class.forName(className).getMethod(methodName, parameterTypes).invoke(null, arguments);
        } catch (ClassNotFoundException ignored) {
            // GameTest classes are only present on the GameTest classpath.
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Unable to register optional GameTest source " + className, e);
        }
    }
}

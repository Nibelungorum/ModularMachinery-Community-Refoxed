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
    private static final String NETWORK_INTERFACE_GAME_TESTS = "cn.howxu.mmcr.network.NetworkInterfaceGameTests";

    private GameTestRegistration() {
    }

    /** Forwards the three startup declarations to the optional GameTest source set. */
    public static void registerStartupSources(MMCRMachineDefinationsEvent definitions,
                                              MMCRMachineStructuresEvent structures,
                                              MMCRMachineRecipesEvent recipes) {
        registerStartupSources(GAME_TEST_REGISTRY, definitions, structures, recipes);
    }

    static void registerStartupSources(String sourceClass,
                                       MMCRMachineDefinationsEvent definitions,
                                       MMCRMachineStructuresEvent structures,
                                       MMCRMachineRecipesEvent recipes) {
        if (definitions != null) {
            invokeOptionalSource(sourceClass, "registerMachineDefinitions",
                    new Class<?>[]{MMCRMachineDefinationsEvent.class}, definitions);
        }
        if (structures != null) {
            invokeOptionalSource(sourceClass, "registerMachineStructures",
                    new Class<?>[]{MMCRMachineStructuresEvent.class}, structures);
        }
        if (recipes != null) {
            invokeOptionalSource(sourceClass, "registerRecipes",
                    new Class<?>[]{MMCRMachineRecipesEvent.class}, recipes);
        }
    }

    /** Registers GameTest instances when the optional GameTest source set is present. */
    public static void registerTests(RegisterGameTestsEvent event) {
        registerTests(GAME_TEST_REGISTRY, event);
    }

    static void registerTests(String sourceClass, RegisterGameTestsEvent event) {
        invokeOptionalSource(sourceClass, "registerAll",
                new Class<?>[]{RegisterGameTestsEvent.class}, event);
        invokeOptionalSource(NETWORK_INTERFACE_GAME_TESTS, "registerAll",
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

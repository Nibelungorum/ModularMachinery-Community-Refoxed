package cn.howxu.mmcr.internal.registration;

import cn.howxu.mmcr.api.registration.MachineDefinitionRegistration;
import cn.howxu.mmcr.api.registration.MachineRecipeRegistration;
import cn.howxu.mmcr.api.registration.StructureRegistration;
import cn.howxu.mmcr.publicapi.event.RegisterMachineDefinitionsEvent;
import cn.howxu.mmcr.publicapi.event.RegisterMachineStructuresEvent;
import cn.howxu.mmcr.publicapi.event.RegisterMachineRecipesEvent;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;

/** Owns optional GameTest source-set loading and registration.
 * @author howxu <dev@howxu.cn>
 */
public final class GameTestRegistration {
    private static final String GAME_TEST_REGISTRY = "cn.howxu.mmcr.GameTestRegistry";
    private static final String NETWORK_INTERFACE_GAME_TESTS = "cn.howxu.mmcr.network.NetworkInterfaceGameTests";
    private static final String CREATE_STRESS_GAME_TESTS = "cn.howxu.mmcr.compat.create.loaded.StressInterfaceGameTest";

    private GameTestRegistration() {
    }

    /** Forwards the production contracts to the optional GameTest source set. */
    public static void registerStartupSources(RegisterMachineDefinitionsEvent definitions,
                                             RegisterMachineStructuresEvent structures,
                                             RegisterMachineRecipesEvent recipes) {
        if (definitions != null) invokeOptionalSource(GAME_TEST_REGISTRY, "registerMachineDefinitions",
                new Class<?>[]{RegisterMachineDefinitionsEvent.class}, definitions);
        if (structures != null) invokeOptionalSource(GAME_TEST_REGISTRY, "registerMachineStructures",
                new Class<?>[]{RegisterMachineStructuresEvent.class}, structures);
        if (recipes != null) invokeOptionalSource(GAME_TEST_REGISTRY, "registerRecipes",
                new Class<?>[]{RegisterMachineRecipesEvent.class}, recipes);
    }

    /** Forwards the three startup declarations to the optional GameTest source set. */
    public static void registerStartupSources(MachineDefinitionRegistration definitions,
                                              StructureRegistration structures,
                                              MachineRecipeRegistration recipes) {
        registerStartupSources(GAME_TEST_REGISTRY, definitions, structures, recipes);
    }

    static void registerStartupSources(String sourceClass,
                                       MachineDefinitionRegistration definitions,
                                       StructureRegistration structures,
                                       MachineRecipeRegistration recipes) {
        if (definitions != null) {
            invokeOptionalSource(sourceClass, "registerMachineDefinitions",
                    new Class<?>[]{MachineDefinitionRegistration.class}, definitions);
        }
        if (structures != null) {
            invokeOptionalSource(sourceClass, "registerMachineStructures",
                    new Class<?>[]{StructureRegistration.class}, structures);
        }
        if (recipes != null) {
            invokeOptionalSource(sourceClass, "registerRecipes",
                    new Class<?>[]{MachineRecipeRegistration.class}, recipes);
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
        if (ModList.get() != null && ModList.get().isLoaded("create")) {
            invokeOptionalSource(CREATE_STRESS_GAME_TESTS, "registerAll",
                    new Class<?>[]{RegisterGameTestsEvent.class}, event);
        }
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

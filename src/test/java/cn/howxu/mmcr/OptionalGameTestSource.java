package cn.howxu.mmcr;

import cn.howxu.mmcr.api.registration.MachineDefinitionRegistration;
import cn.howxu.mmcr.api.registration.MachineRecipeRegistration;
import cn.howxu.mmcr.api.registration.StructureRegistration;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;

/** Test fixture for the shared optional source invocation path.
 * @author howxu <dev@howxu.cn>
 */
public final class OptionalGameTestSource {
    private static boolean invoked;
    private static boolean structuresInvoked;
    private static boolean recipesInvoked;
    private static boolean testsInvoked;

    private OptionalGameTestSource() {
    }

    public static void accept(MachineDefinitionRegistration event) {
        invoked = true;
    }

    public static boolean invoked() {
        return invoked;
    }

    public static void acceptStructures(StructureRegistration event) {
        structuresInvoked = true;
    }

    public static void acceptRecipes(MachineRecipeRegistration event) {
        recipesInvoked = true;
    }

    public static void registerMachineDefinitions(MachineDefinitionRegistration event) {
        accept(event);
    }

    public static void registerMachineStructures(StructureRegistration event) {
        acceptStructures(event);
    }

    public static void registerRecipes(MachineRecipeRegistration event) {
        acceptRecipes(event);
    }

    public static void registerAll(RegisterGameTestsEvent event) {
        testsInvoked = true;
        event.register(FixtureGameTest.class);
    }

    public static boolean structuresInvoked() {
        return structuresInvoked;
    }

    public static boolean recipesInvoked() {
        return recipesInvoked;
    }

    public static boolean testsInvoked() {
        return testsInvoked;
    }

    public static void reset() {
        invoked = false;
        structuresInvoked = false;
        recipesInvoked = false;
        testsInvoked = false;
    }

    public static final class FixtureGameTest {
        @GameTest(template = "empty", timeoutTicks = 1)
        public static void optionalSourceTest(GameTestHelper helper) {
            helper.succeed();
        }
    }
}

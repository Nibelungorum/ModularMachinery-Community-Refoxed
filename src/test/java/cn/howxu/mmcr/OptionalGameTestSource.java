package cn.howxu.mmcr;

import cn.howxu.mmcr.api.publicapi.event.MMCRMachineDefinationsEvent;
import cn.howxu.mmcr.api.publicapi.event.MMCRMachineRecipesEvent;
import cn.howxu.mmcr.api.publicapi.event.MMCRMachineStructuresEvent;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.Holder;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestInstance;
import net.minecraft.gametest.framework.TestData;
import net.minecraft.gametest.framework.TestEnvironmentDefinition;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Rotation;
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

    public static void accept(MMCRMachineDefinationsEvent event) {
        invoked = true;
    }

    public static boolean invoked() {
        return invoked;
    }

    public static void acceptStructures(MMCRMachineStructuresEvent event) {
        structuresInvoked = true;
    }

    public static void acceptRecipes(MMCRMachineRecipesEvent event) {
        recipesInvoked = true;
    }

    public static void registerMachineDefinitions(MMCRMachineDefinationsEvent event) {
        accept(event);
    }

    public static void registerMachineStructures(MMCRMachineStructuresEvent event) {
        acceptStructures(event);
    }

    public static void registerRecipes(MMCRMachineRecipesEvent event) {
        acceptRecipes(event);
    }

    public static void registerAll(net.neoforged.neoforge.event.RegisterGameTestsEvent event) {
        testsInvoked = true;
        Holder<TestEnvironmentDefinition<?>> environment = Holder.direct(new TestEnvironmentDefinition.AllOf());
        TestData<Holder<TestEnvironmentDefinition<?>>> data = new TestData<>(environment,
                Identifier.fromNamespaceAndPath("minecraft", "empty"), 1, 0, true,
                Rotation.NONE, false, 1, 1, false, 0);
        event.registerTest(Identifier.fromNamespaceAndPath("mmcr", "optional_source_test"),
                new FixtureGameTest(data));
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

    private static final class FixtureGameTest extends GameTestInstance {
        private FixtureGameTest(TestData<Holder<TestEnvironmentDefinition<?>>> data) {
            super(data);
        }

        @Override
        public void run(GameTestHelper helper) {
            helper.succeed();
        }

        @Override
        @SuppressWarnings({"rawtypes", "unchecked"})
        public MapCodec<? extends GameTestInstance> codec() {
            return (MapCodec) MapCodec.unit(this);
        }

        @Override
        protected MutableComponent typeDescription() {
            return Component.literal("Optional source fixture");
        }
    }
}

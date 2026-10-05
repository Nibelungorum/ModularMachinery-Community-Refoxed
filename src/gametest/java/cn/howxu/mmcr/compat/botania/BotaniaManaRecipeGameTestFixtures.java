package cn.howxu.mmcr.compat.botania;

import cn.howxu.mmcr.api.capability.CapabilitySnapshot;
import cn.howxu.mmcr.api.machine.MachineRegistry;
import cn.howxu.mmcr.api.recipe.MachineRecipe;
import cn.howxu.mmcr.api.recipe.RecipeRegistry;
import cn.howxu.mmcr.compat.botania.loaded.ManaPortBlockEntity;
import cn.howxu.mmcr.internal.block.MachineControllerBlock;
import cn.howxu.mmcr.internal.recipe.FactoryRecipeThread;
import cn.howxu.mmcr.internal.runtime.CraftingRuntime;
import cn.howxu.mmcr.internal.runtime.FactoryRuntime;
import cn.howxu.mmcr.internal.runtime.MachineWorkMode;
import cn.howxu.mmcr.internal.tile.MachineControllerBlockEntity;
import cn.howxu.mmcr.internal.tile.MachineControllerRuntime;
import cn.howxu.mmcr.internal.tile.ParallelControllerBlockEntity;
import cn.howxu.mmcr.registry.ModBlocks;
import cn.howxu.mmcr.util.IOType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;

import java.lang.reflect.Field;
import java.util.List;

/** Controller-backed recipe fixtures independent of the native transport suite.
 * @author howxu <dev@howxu.cn>
 */
public final class BotaniaManaRecipeGameTestFixtures {
    public static final BlockPos CONTROLLER_POS = new BlockPos(2, 1, 1);

    private BotaniaManaRecipeGameTestFixtures() {}

    public static MachineFixture machine(GameTestHelper helper) {
        return machine(helper, false);
    }

    public static MachineFixture synchronousMachine(GameTestHelper helper) {
        return machine(helper, true);
    }

    private static MachineFixture machine(GameTestHelper helper, boolean synchronous) {
        ManaPortBlockEntity firstInput = BotaniaManaGameTestFixtures.port(helper, CONTROLLER_POS.west(), IOType.INPUT);
        ManaPortBlockEntity secondInput = BotaniaManaGameTestFixtures.port(helper, CONTROLLER_POS.west(2), IOType.INPUT);
        ManaPortBlockEntity firstOutput = BotaniaManaGameTestFixtures.port(helper, CONTROLLER_POS.east(), IOType.OUTPUT);
        ManaPortBlockEntity secondOutput = BotaniaManaGameTestFixtures.port(helper, CONTROLLER_POS.east(2), IOType.OUTPUT);
        helper.setBlock(CONTROLLER_POS.above(), ModBlocks.BLOCKS.get("parallel_controller_normal").get().defaultBlockState());
        ParallelControllerBlockEntity parallel = helper.getBlockEntity(CONTROLLER_POS.above());
        parallel.setCurrentParallelism(3);
        helper.setBlock(CONTROLLER_POS, ModBlocks.controllerFor(BotaniaManaRecipeGameTest.MACHINE_ID).get()
                .defaultBlockState().setValue(MachineControllerBlock.FACING, Direction.SOUTH));
        MachineControllerBlockEntity controller = helper.getBlockEntity(CONTROLLER_POS);
        if (synchronous) {
            controller.setRemoved();
            controller = new SynchronousController(controller.getBlockPos(), controller.getBlockState());
            controller.setLevel(helper.getLevel());
            helper.getLevel().setBlockEntity(controller);
            controller.onLoad();
        }
        controller.setMachine(MachineRegistry.getMachine(BotaniaManaRecipeGameTest.MACHINE_ID));
        form(helper, controller, List.of(firstInput, secondInput, firstOutput, secondOutput));
        return new MachineFixture(controller, firstInput, secondInput, firstOutput, secondOutput);
    }

    public static void form(GameTestHelper helper, MachineControllerBlockEntity controller,
                            List<ManaPortBlockEntity> ports) {
        controller.requestImmediateStructureCheck();
        controller.tickStructure(helper.getLevel(), controller.getBlockPos());
        helper.assertTrue(controller.currentStructureSnapshot().formed()
                        && ports.stream().allMatch(port -> port.linkedControllerPositions().contains(controller.getBlockPos()))
                        && controller.componentRuntime().capabilities().stream()
                        .filter(capability -> capability.type().equals(BotaniaManaIds.TYPE)).count() == 4,
                "Startup-registered controller forms and discovers all four real mana stores");
    }

    public static MachineRecipe recipe(GameTestHelper helper, ResourceLocation id) {
        MachineRecipe recipe = RecipeRegistry.getRecipe(id);
        helper.assertTrue(recipe != null && recipe.recipePoolId().equals(BotaniaManaRecipeGameTest.MACHINE_ID),
                "Recipe fixture belongs to the startup-registered controller pool: " + id);
        return recipe;
    }

    public static MachineControllerRuntime runtime(MachineControllerBlockEntity controller) {
        return field(controller, MachineControllerBlockEntity.class, "runtime", MachineControllerRuntime.class);
    }

    public static FactoryRecipeThread baseLane(FactoryRuntime factory) {
        List<?> lanes = field(factory, FactoryRuntime.class, "lanes", List.class);
        return lanes.stream().map(FactoryRecipeThread.class::cast).filter(FactoryRecipeThread::isBaseThread)
                .findFirst().orElseThrow(() -> new AssertionError("Controller has a real base recipe lane"));
    }

    private static <T> T field(Object owner, Class<?> declaringClass, String name, Class<T> type) {
        try {
            Field field = declaringClass.getDeclaredField(name);
            field.setAccessible(true);
            return type.cast(field.get(owner));
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError("Unable to inspect controller lifecycle field " + name, exception);
        }
    }

    public static void complete(GameTestHelper helper, CraftingRuntime runtime, MachineFixture fixture) {
        long inputBefore = fixture.inputAmount();
        long outputBefore = fixture.outputAmount();
        int remaining = runtime.totalTick() - runtime.tickCount();
        for (int step = 0; step < remaining && !runtime.finishPending(); step++) {
            runtime.tick();
            helper.assertTrue(fixture.inputAmount() == inputBefore && fixture.outputAmount() == outputBefore,
                    "Progress preserves start-only consumption and completion-only output");
        }
        helper.assertTrue(runtime.active() && runtime.finishPending(), "Active recipe reaches its finish boundary");
    }

    public static void makeFinishEligible(GameTestHelper helper, CraftingRuntime runtime) {
        // Exercise the retry entrypoint without changing world time or waiting an exact number of ticks.
        runtime.activeRecipe().markFinishBlocked((int) helper.getLevel().getGameTime() - 20);
    }

    /** Test-local work mode for immediately driven recipe lifecycles; no server config override.
     * @author howxu <dev@howxu.cn>
     */
    private static final class SynchronousController extends MachineControllerBlockEntity {
        private SynchronousController(BlockPos pos, BlockState state) { super(pos, state); }

        @Override public MachineWorkMode activeWorkMode() { return MachineWorkMode.SYNC; }
    }

    /** Four discovered ports belonging to one real controller.
     * @author howxu <dev@howxu.cn>
     */
    public record MachineFixture(MachineControllerBlockEntity controller, ManaPortBlockEntity firstInput,
                                 ManaPortBlockEntity secondInput, ManaPortBlockEntity firstOutput,
                                 ManaPortBlockEntity secondOutput) {
        public CraftingRuntime crafting() { return runtime(controller).craftingRuntime(); }
        public FactoryRuntime factory() { return runtime(controller).factoryRuntime(); }
        public CapabilitySnapshot snapshot() { return new CapabilitySnapshot(controller.componentRuntime().capabilities()); }
        public List<ManaPortBlockEntity> ports() { return List.of(firstInput, secondInput, firstOutput, secondOutput); }
        public long inputAmount() { return (long) firstInput.storage().amount() + secondInput.storage().amount(); }
        public long outputAmount() { return (long) firstOutput.storage().amount() + secondOutput.storage().amount(); }
    }
}

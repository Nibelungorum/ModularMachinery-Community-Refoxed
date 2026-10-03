package cn.howxu.mmcr.compat.create.loaded;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.capability.CapabilityHost;
import cn.howxu.mmcr.api.capability.status.FailureReason;
import cn.howxu.mmcr.api.compat.create.CreateFailureReasons;
import cn.howxu.mmcr.api.compat.create.StressFacet;
import cn.howxu.mmcr.api.machine.BlockArray;
import cn.howxu.mmcr.api.machine.BlockPredicate;
import cn.howxu.mmcr.api.machine.DynamicMachine;
import cn.howxu.mmcr.api.machine.MachineRegistry;
import cn.howxu.mmcr.api.registration.StructureRegistration;
import cn.howxu.mmcr.api.recipe.MachineRecipeBuilder;
import cn.howxu.mmcr.api.recipe.RecipeRegistry;
import cn.howxu.mmcr.compat.create.StressSession;
import cn.howxu.mmcr.internal.block.MachineControllerBlock;
import cn.howxu.mmcr.internal.registration.MachineRecipeConverter;
import cn.howxu.mmcr.internal.tile.ItemBusBlockEntity;
import cn.howxu.mmcr.internal.tile.MachineControllerBlockEntity;
import cn.howxu.mmcr.internal.port.MachinePort;
import cn.howxu.mmcr.registry.ModBlocks;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.kinetics.KineticNetwork;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.content.kinetics.base.RotatedPillarKineticBlock;
import com.simibubi.create.content.kinetics.chainDrive.ChainDriveBlock;
import com.simibubi.create.content.kinetics.motor.CreativeMotorBlock;
import com.simibubi.create.content.kinetics.motor.CreativeMotorBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.ScrollValueBehaviour;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestGenerator;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestSequence;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DirectionalBlock;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/** Compact integration scenarios using Create's real propagation and network bookkeeping.
 * @author howxu <dev@howxu.cn>
 */
public final class StressInterfaceGameTest {
    private static final BlockPos PORT = new BlockPos(2, 2, 1);
    private static final BlockPos SHAFT = new BlockPos(2, 2, 2);
    private static final BlockPos MOTOR = new BlockPos(2, 2, 3);
    private static final BlockPos CONTROLLER = new BlockPos(2, 1, 1);
    private static final BlockPos ITEMS = new BlockPos(1, 1, 1);
    private static final BlockPos PRODUCTS = new BlockPos(2, 0, 1);

    private StressInterfaceGameTest() {}

    public static void registerAll(RegisterGameTestsEvent event) {
        event.register(StressInterfaceGameTest.class);
    }

    @GameTestGenerator
    public static Collection<TestFunction> generateTests() {
        return List.of(
                test("create_stress_propagation_sessions", 300, StressInterfaceGameTest::propagationAndSessions),
                test("create_stress_controller_recovery", 600, StressInterfaceGameTest::controllerRecovery),
                test("create_stress_output_takeover", 400, StressInterfaceGameTest::outputTakeover),
                test("create_stress_output_grace", 300, StressInterfaceGameTest::outputGrace),
                test("create_stress_grace_pause", 300, StressInterfaceGameTest::gracePause),
                test("create_stress_continuous_restart", 300, StressInterfaceGameTest::continuousRestart),
                test("create_stress_persistence_removal", 300, StressInterfaceGameTest::persistenceAndRemoval));
    }

    private static TestFunction test(String name, int timeout, Consumer<GameTestHelper> scenario) {
        return new TestFunction(MMCR.MODID, MMCR.id(name).toString(), "mmcr:empty", timeout, 0, true, scenario);
    }

    private static void propagationAndSessions(GameTestHelper helper) {
        AtomicReference<GearRig> rig = new AtomicReference<>(gearRig(helper, false, null));
        GameTestSequence sequence = helper.startSequence();
        verifyPropagationAndSessions(helper, sequence, rig);
        sequence.thenExecute(() -> {
            List<BlockState> connectedStates = rig.get().positions().stream()
                    .map(pos -> helper.getLevel().getBlockState(helper.absolutePos(pos))).toList();
            for (BlockPos pos : rig.get().positions()) helper.setBlock(pos, Blocks.AIR);
            rig.set(gearRig(helper, true, connectedStates));
        });
        verifyPropagationAndSessions(helper, sequence, rig);
        sequence.thenSucceed();
    }

    private static void verifyPropagationAndSessions(GameTestHelper helper, GameTestSequence sequence,
                                                     AtomicReference<GearRig> rig) {
        AtomicReference<KineticNetwork> network = new AtomicReference<>();
        AtomicReference<Double> baseline = new AtomicReference<>();
        StressSession first = new StressSession();
        StressSession second = new StressSession();
        sequence.thenWaitUntil(() -> {
            GearRig current = rig.get();
            MachineControllerBlockEntity controller = helper.getBlockEntity(BlockPos.ZERO);
            helper.assertTrue(controller.structureSnapshot().formed(),
                    "Native session fixture waits for its real controller association to form");
            helper.assertTrue(controller.runtimeSnapshot().linkedPortPositions().containsAll(List.of(
                            current.slow().getBlockPos(), current.output().getBlockPos(), current.fast().getBlockPos())),
                    "Real controller discovers all three native interfaces before standalone session commits");
            StressFacet slow = facet(current.slow());
            StressFacet fast = facet(current.fast());
            helper.assertTrue(slow.state().connected() && fast.state().connected()
                            && facet(current.output()).networkIdentity() == slow.networkIdentity()
                            && fast.networkIdentity() == slow.networkIdentity()
                            && current.shaft().getSpeed() == current.motor().getGeneratedSpeed()
                            && Math.abs(current.slow().getSpeed()) == 16F
                            && Math.abs(current.fast().getSpeed()) == 32F,
                    "Shaft, vanilla chain drive, both native ports and 2:1 cogs propagate on one real network");
            helper.assertTrue(current.chain().getBlockState().getValue(ChainDriveBlock.PART) != ChainDriveBlock.Part.NONE,
                    "Vanilla chain drives bridge the separate shaft axes, including rotated layout");
        }).thenExecute(() -> {
            GearRig current = rig.get();
            network.set((KineticNetwork) facet(current.slow()).networkIdentity());
            baseline.set((double) network.get().calculateStress());
            apply(helper, current.slow(), first, 1, 2D, 0D);
            apply(helper, current.fast(), first, 2, 2D, 0D);
            double slowLoad = facet(current.slow()).state().actualContribution();
            double fastLoad = facet(current.fast()).state().actualContribution();
            close(helper, fastLoad, 2D * slowLoad, "Equal base load uses each interface's local geared RPM");
            apply(helper, current.fast(), second, 2, 3D, 0D);
            double expected = baseline.get() + 2D * Math.abs(current.slow().getSpeed())
                    + 5D * Math.abs(current.fast().getSpeed());
            close(helper, network.get().calculateStress(), expected, "Native network sums independent session loads");
            for (int repeat = 0; repeat < 3; repeat++) {
                apply(helper, current.slow(), first, 1, 2D, 0D);
                apply(helper, current.fast(), first, 2, 2D, 0D);
                apply(helper, current.fast(), second, 2, 3D, 0D);
            }
            close(helper, network.get().calculateStress(), expected, "Repeated owner/index updates do not accumulate SU");
            first.releaseAll();
            close(helper, network.get().calculateStress(), baseline.get() + 3D * Math.abs(current.fast().getSpeed()),
                    "Releasing one same-network session preserves the other session");
            second.releaseAll();
            close(helper, network.get().calculateStress(), baseline.get(), "Last session release restores native baseline");
        }).thenExecute(() -> helper.setBlock(rig.get().positions().get(4), Blocks.AIR))
                .thenWaitUntil(() -> helper.assertTrue(Math.abs(rig.get().slow().getSpeed()) == 16F
                                && rig.get().output().getSpeed() == 0F && rig.get().fast().getSpeed() == 0F,
                        "Side-adjacent interfaces cannot bypass a removed vanilla chain bridge"))
                .thenExecute(() -> helper.setBlock(rig.get().positions().get(4), axis(AllBlocks.ENCASED_CHAIN_DRIVE.get(),
                        rig.get().slow().getBlockState().getValue(RotatedPillarKineticBlock.AXIS))))
                .thenWaitUntil(() -> helper.assertTrue(facet(rig.get().output()).networkIdentity() == facet(rig.get().slow()).networkIdentity()
                                && Math.abs(rig.get().fast().getSpeed()) == 32F,
                        "Restoring the vanilla chain bridge reconnects the isolated shaft branch"));
    }

    private static void controllerRecovery(GameTestHelper helper) {
        helper.setBlock(PORT, portState(StressInterfaceKind.INPUT));
        helper.setBlock(SHAFT, axis(AllBlocks.SHAFT.get(), Direction.Axis.Z));
        CreativeMotorBlockEntity motor = motor(helper, MOTOR, Direction.NORTH, 4);
        // A real vanilla load remains on the network while the recipe is idle.
        helper.setBlock(new BlockPos(2, 2, 0), AllBlocks.ENCASED_FAN.get().defaultBlockState()
                .setValue(DirectionalBlock.FACING, Direction.NORTH));
        KineticBlockEntity externalLoad = helper.getBlockEntity(new BlockPos(2, 2, 0));
        ControllerRig machine = controller(helper, "create_stress_recovery_machine", StressInterfaceKind.INPUT);
        ResourceLocation recipeId = MMCR.id("create_stress_recovery_recipe");
        if (!RecipeRegistry.containsStatic(recipeId)) {
            RecipeRegistry.registerStatic(MachineRecipeConverter.toRecipe(MachineRecipeBuilder.recipe(recipeId).recipePool(machine.id())
                    .duration(40).inputItem(Items.COAL, 1).inputStress(2D, 16D)
                    .outputItem(new ItemStack(Items.DIAMOND)).build(),
                    new StructureRegistration.Snapshot(Map.of(), Map.of(), Map.of(), Map.of())));
        }
        machine.items().nativeItemHandler().setStackInSlot(0, new ItemStack(Items.COAL));
        StressInputBlockEntity input = helper.getBlockEntity(PORT);
        AtomicLong epoch = new AtomicLong();
        AtomicReference<KineticNetwork> network = new AtomicReference<>();
        AtomicReference<Float> capacity = new AtomicReference<>();
        helper.startSequence().thenWaitUntil(() -> {
            helper.assertTrue(machine.controller().structureSnapshot().formed() && facet(input).state().connected()
                            && externalLoad.hasSource() && externalLoad.getSpeed() == input.getSpeed()
                            && machine.controller().runtimeSnapshot().crafting().recipeId() == null,
                    "Real controller forms and rejects its candidate at insufficient local RPM");
            failure(helper, machine.controller(), CreateFailureReasons.INSUFFICIENT_RPM);
        }).thenExecuteAfter(1, () -> {
            epoch.set(machine.controller().resourceAvailabilityEpoch());
            motor.getBehaviour(ScrollValueBehaviour.TYPE).setValue(32);
            helper.assertTrue(machine.controller().resourceAvailabilityEpoch() > epoch.get(),
                    "Native speed propagation itself notifies the bound controller before recipe commit");
        }).thenWaitUntil(() -> {
            helper.assertTrue(machine.controller().resourceAvailabilityEpoch() > epoch.get()
                            && machine.controller().runtimeSnapshot().crafting().tick() > 0,
                    "Real speed transition notifies the controller and reconsiders the blocked candidate");
            close(helper, facet(input).state().baseContribution(), 2D, "Started recipe holds one input contribution");
        }).thenExecute(() -> {
            network.set((KineticNetwork) facet(input).networkIdentity());
            capacity.set(network.get().calculateCapacity());
            // Use native capacity updates instead of changing global Create configuration.
            network.get().updateCapacityFor(motor, 0F);
        }).thenWaitUntil(() -> {
            helper.assertTrue(input.isOverStressed() && input.getSpeed() == 0F,
                    "Native network overload stops actual rotation");
            failure(helper, machine.controller(), CreateFailureReasons.INSUFFICIENT_STRESS);
        }).thenExecute(() -> {
            int progress = machine.controller().runtimeSnapshot().crafting().tick();
            machine.controller().serverTick();
            helper.assertTrue(machine.controller().runtimeSnapshot().crafting().tick() == progress,
                    "A blocked controller action cannot advance recipe progress");
            close(helper, facet(input).state().baseContribution(), 2D, "Power wait retains input without duplicate load");
        }).thenExecuteAfter(1, () -> {
            epoch.set(machine.controller().resourceAvailabilityEpoch());
            network.get().updateCapacityFor(motor, motor.calculateAddedStressCapacity());
            helper.assertTrue(machine.controller().resourceAvailabilityEpoch() > epoch.get(),
                    "Native overstress recovery itself notifies the bound controller");
        }).thenWaitUntil(() -> {
            helper.assertTrue(machine.controller().resourceAvailabilityEpoch() > epoch.get() && !input.isOverStressed()
                            && products(machine) == 1,
                    "Capacity recovery notifies the real controller and completes the interrupted recipe");
            close(helper, facet(input).state().baseContribution(), 0D, "Completion releases native input load");
            close(helper, network.get().calculateCapacity(), capacity.get(), "Recovery restores the original live network");
        }).thenExecute(() -> {
            network.get().updateCapacityFor(motor, 0F);
            machine.items().nativeItemHandler().setStackInSlot(0, new ItemStack(Items.COAL));
        }).thenWaitUntil(() -> {
            helper.assertTrue(input.isOverStressed() && machine.controller().runtimeSnapshot().crafting().recipeId() == null,
                    "An idle controller rejects a new candidate on the overloaded native network");
            failure(helper, machine.controller(), CreateFailureReasons.INSUFFICIENT_STRESS);
        }).thenExecuteAfter(1, () -> {
            epoch.set(machine.controller().resourceAvailabilityEpoch());
            network.get().updateCapacityFor(motor, motor.calculateAddedStressCapacity());
            helper.assertTrue(machine.controller().resourceAvailabilityEpoch() > epoch.get(),
                    "Idle native recovery notifies the controller before candidate input commit");
        }).thenWaitUntil(() -> helper.assertTrue(machine.controller().resourceAvailabilityEpoch() > epoch.get()
                        && products(machine) == 2 && facet(input).state().baseContribution() == 0D,
                "Overstress recovery reconsiders the idle candidate and releases the second completed load"))
                .thenSucceed();
    }

    private static void outputTakeover(GameTestHelper helper) {
        helper.setBlock(PORT, portState(StressInterfaceKind.OUTPUT));
        helper.setBlock(SHAFT, axis(AllBlocks.SHAFT.get(), Direction.Axis.Z));
        helper.setBlock(MOTOR, AllBlocks.ENCASED_FAN.get().defaultBlockState()
                .setValue(DirectionalBlock.FACING, Direction.SOUTH));
        ControllerRig machine = controller(helper, "create_stress_generator_machine", StressInterfaceKind.OUTPUT);
        ResourceLocation recipeId = MMCR.id("create_stress_generator_recipe");
        if (!RecipeRegistry.containsStatic(recipeId)) {
            RecipeRegistry.registerStatic(MachineRecipeConverter.toRecipe(MachineRecipeBuilder.recipe(recipeId).recipePool(machine.id())
                    .duration(40).inputItem(Items.COAL, 1).outputStress(8D, -16D)
                    .outputItem(new ItemStack(Items.DIAMOND)).build(),
                    new StructureRegistration.Snapshot(Map.of(), Map.of(), Map.of(), Map.of())));
        }
        StressOutputBlockEntity output = helper.getBlockEntity(PORT);
        KineticBlockEntity shaft = helper.getBlockEntity(SHAFT);
        KineticBlockEntity fan = helper.getBlockEntity(MOTOR);
        AtomicReference<CreativeMotorBlockEntity> faster = new AtomicReference<>();
        BlockPos fasterPos = new BlockPos(2, 2, 0);
        BlockPos pausePos = new BlockPos(3, 1, 1);
        helper.startSequence().thenWaitUntil(() -> helper.assertTrue(machine.controller().structureSnapshot().formed(),
                        "Generator controller discovers its native output port"))
                .thenExecute(() -> {
                    helper.assertTrue(output.getGeneratedSpeed() == 0F && shaft.getSpeed() == 0F,
                            "Recipe output is inactive before successful inputs");
                    machine.items().nativeItemHandler().setStackInSlot(0, new ItemStack(Items.COAL));
                }).thenWaitUntil(() -> {
                    helper.assertTrue(facet(output).state().connected() && output.getGeneratedSpeed() == -16F
                                    && shaft.getSpeed() == -16F && fan.getSpeed() == -16F,
                            "Successful recipe tick generates signed RPM and drives real shaft/fan");
                    KineticNetwork network = (KineticNetwork) facet(output).networkIdentity();
                    close(helper, network.getActualCapacityOf(output), 8D * 16D,
                            "Native generator capacity is base SU times absolute generated RPM");
                }).thenExecute(() -> faster.set(motor(helper, fasterPos, Direction.SOUTH, -64)))
                .thenWaitUntil(() -> {
                    helper.assertTrue(output.hasSource() && output.getSpeed() == -64F && shaft.getSpeed() == -64F
                                    && output.getGeneratedSpeed() == -16F,
                            "Same-sign faster foreign generator takes over actual network RPM");
                    KineticNetwork network = (KineticNetwork) facet(output).networkIdentity();
                    close(helper, network.getActualCapacityOf(output), 8D * 16D,
                            "Overpowered output capacity still uses its own generated RPM");
                    close(helper, network.calculateCapacity() - network.getActualCapacityOf(faster.get()), 8D * 16D,
                            "Native aggregate contains the output contribution exactly once");
                }).thenExecute(() -> {
                    helper.setBlock(pausePos, Blocks.LEVER.defaultBlockState().setValue(LeverBlock.FACE, AttachFace.WALL)
                            .setValue(LeverBlock.FACING, Direction.EAST).setValue(LeverBlock.POWERED, true));
                    helper.assertTrue(helper.getLevel().getDirectSignalTo(helper.absolutePos(CONTROLLER)) > 0,
                            "The pause fixture supplies the controller's required direct redstone signal");
                })
                .thenWaitUntil(() -> {
                    helper.assertTrue(machine.controller().isRedstonePaused() && output.getGeneratedSpeed() == 0F
                                    && shaft.getSpeed() == -64F,
                            "Real controller pause revokes output without stopping the foreign source");
                    onlyForeignCapacity(helper, output, faster.get());
                }).thenExecute(() -> helper.setBlock(pausePos, Blocks.AIR))
                .thenWaitUntil(() -> helper.assertTrue(!machine.controller().isRedstonePaused()
                                && output.getGeneratedSpeed() == -16F && facet(output).state().baseContribution() == 8D,
                        "Resume reacquires output only after a runnable recipe tick"))
                .thenWaitUntil(() -> {
                    helper.assertTrue(products(machine) == 1 && output.getGeneratedSpeed() == 0F,
                            "Recipe completion revokes output RPM and capacity");
                    onlyForeignCapacity(helper, output, faster.get());
                }).thenExecute(() -> helper.setBlock(fasterPos, Blocks.AIR))
                .thenWaitUntil(() -> helper.assertTrue(shaft.getSpeed() == 0F && fan.getSpeed() == 0F
                                && output.getGeneratedSpeed() == 0F,
                        "Removing the foreign source cannot resurrect a completed recipe generator"))
                .thenSucceed();
    }

    private static void outputGrace(GameTestHelper helper) {
        helper.setBlock(PORT, portState(StressInterfaceKind.OUTPUT));
        helper.setBlock(SHAFT, axis(AllBlocks.SHAFT.get(), Direction.Axis.Z));
        helper.setBlock(MOTOR, AllBlocks.ENCASED_FAN.get().defaultBlockState()
                .setValue(DirectionalBlock.FACING, Direction.SOUTH));
        StressOutputBlockEntity output = helper.getBlockEntity(PORT);
        KineticBlockEntity shaft = helper.getBlockEntity(SHAFT);
        KineticBlockEntity fan = helper.getBlockEntity(MOTOR);
        bind(helper, output);
        StressSession session = new StressSession();
        helper.startSequence().thenWaitUntil(() -> helper.assertTrue(fixtureAssociated(helper, output),
                        "Grace fixture has a real formed controller association"))
                .thenExecute(() -> {
                    apply(helper, output, session, 1, 4, -16);
                    session.onRecipeFinished();
                    helper.assertTrue(output.getGeneratedSpeed() == -16F && shaft.getSpeed() == -16F
                                    && fan.getSpeed() == -16F && facet(output).ownedBaseStress(session, 1) == 0D,
                            "Normal finish releases logical ownership without stopping the native source");
                }).thenExecuteAfter(3, () -> {
                    helper.assertTrue(output.getGeneratedSpeed() == -16F && fan.getSpeed() == -16F,
                            "Native dependants keep turning during the output grace period");
                    apply(helper, output, session, 7, 8, -32);
                    helper.assertTrue(output.getGeneratedSpeed() == -32F && shaft.getSpeed() == -32F && fan.getSpeed() == -32F,
                            "A different requirement and RPM update the coasting native source directly");
                    session.onRecipeFinished();
                }).thenWaitUntil(() -> helper.assertTrue(output.getGeneratedSpeed() == 0F && shaft.getSpeed() == 0F
                                && fan.getSpeed() == 0F && facet(output).state().baseContribution() == 0D,
                        "Unrenewed grace expires and removes native generation and capacity"))
                .thenExecute(() -> {
                    apply(helper, output, session, 2, 4, -16);
                    session.onRecipeFinished();
                    session.releaseAll();
                    helper.assertTrue(output.getGeneratedSpeed() == 0F && shaft.getSpeed() == 0F && fan.getSpeed() == 0F,
                            "Explicit cleanup revokes coasting immediately without waiting for its deadline");
                }).thenSucceed();
    }

    private static void gracePause(GameTestHelper helper) {
        helper.setBlock(PORT, portState(StressInterfaceKind.OUTPUT));
        helper.setBlock(SHAFT, axis(AllBlocks.SHAFT.get(), Direction.Axis.Z));
        helper.setBlock(MOTOR, AllBlocks.ENCASED_FAN.get().defaultBlockState()
                .setValue(DirectionalBlock.FACING, Direction.SOUTH));
        ControllerRig machine = controller(helper, "create_stress_grace_pause_machine", StressInterfaceKind.OUTPUT);
        ResourceLocation recipeId = MMCR.id("create_stress_grace_pause_recipe");
        if (!RecipeRegistry.containsStatic(recipeId)) {
            RecipeRegistry.registerStatic(MachineRecipeConverter.toRecipe(MachineRecipeBuilder.recipe(recipeId)
                    .recipePool(machine.id()).duration(6).inputItem(Items.COAL, 1).outputStress(8D, -16D)
                    .outputItem(new ItemStack(Items.DIAMOND)).build(),
                    new StructureRegistration.Snapshot(Map.of(), Map.of(), Map.of(), Map.of())));
        }
        StressOutputBlockEntity output = helper.getBlockEntity(PORT);
        KineticBlockEntity fan = helper.getBlockEntity(MOTOR);
        BlockPos pausePos = new BlockPos(3, 1, 1);
        AtomicBoolean lateStop = new AtomicBoolean();
        helper.startSequence().thenWaitUntil(() -> helper.assertTrue(machine.controller().structureSnapshot().formed(),
                        "Pause fixture forms before receiving its single input batch"))
                .thenExecute(() -> machine.items().nativeItemHandler().setStackInSlot(0, new ItemStack(Items.COAL)))
                .thenWaitUntil(() -> helper.assertTrue(products(machine) == 1 && output.getGeneratedSpeed() == -16F,
                        "Completed idle recipe leaves its native generator coasting"))
                .thenExecute(() -> helper.setBlock(pausePos, Blocks.LEVER.defaultBlockState()
                        .setValue(LeverBlock.FACE, AttachFace.WALL).setValue(LeverBlock.FACING, Direction.EAST)
                        .setValue(LeverBlock.POWERED, true)))
                .thenWaitUntil(() -> {
                    boolean paused = machine.controller().isRedstonePaused();
                    if (paused && (output.getGeneratedSpeed() != 0F || fan.getSpeed() != 0F)) lateStop.set(true);
                    helper.assertTrue(paused, "Idle controller detects its redstone pause");
                    helper.assertTrue(!lateStop.get(), "The first observed pause immediately revokes completed-recipe grace");
                }).thenSucceed();
    }

    private static void continuousRestart(GameTestHelper helper) {
        helper.setBlock(PORT, portState(StressInterfaceKind.OUTPUT));
        helper.setBlock(SHAFT, axis(AllBlocks.SHAFT.get(), Direction.Axis.Z));
        helper.setBlock(MOTOR, AllBlocks.ENCASED_FAN.get().defaultBlockState()
                .setValue(DirectionalBlock.FACING, Direction.SOUTH));
        ControllerRig machine = controller(helper, "create_stress_continuous_machine", StressInterfaceKind.OUTPUT);
        ResourceLocation recipeId = MMCR.id("create_stress_continuous_recipe");
        if (!RecipeRegistry.containsStatic(recipeId)) {
            RecipeRegistry.registerStatic(MachineRecipeConverter.toRecipe(MachineRecipeBuilder.recipe(recipeId)
                    .recipePool(machine.id()).duration(6).inputItem(Items.COAL, 1).outputStress(8D, -16D)
                    .outputItem(new ItemStack(Items.DIAMOND)).build(),
                    new StructureRegistration.Snapshot(Map.of(), Map.of(), Map.of(), Map.of())));
        }
        StressOutputBlockEntity output = helper.getBlockEntity(PORT);
        KineticBlockEntity shaft = helper.getBlockEntity(SHAFT);
        KineticBlockEntity fan = helper.getBlockEntity(MOTOR);
        AtomicBoolean interrupted = new AtomicBoolean();
        AtomicReference<Object> network = new AtomicReference<>();
        helper.startSequence().thenWaitUntil(() -> helper.assertTrue(machine.controller().structureSnapshot().formed(),
                        "Continuous generator forms before receiving its input batches"))
                .thenExecute(() -> machine.items().nativeItemHandler().setStackInSlot(0, new ItemStack(Items.COAL, 4)))
                .thenWaitUntil(() -> helper.assertTrue(output.getGeneratedSpeed() == -16F && fan.getSpeed() == -16F,
                        "First funded tick activates the native generator"))
                .thenExecute(() -> network.set(facet(output).networkIdentity()))
                .thenWaitUntil(() -> {
                    if (output.getGeneratedSpeed() != -16F || shaft.getSpeed() != -16F || fan.getSpeed() != -16F
                            || facet(output).networkIdentity() != network.get()) interrupted.set(true);
                    helper.assertTrue(products(machine) >= 3, "Three completed recipes exercise asynchronous restart gaps");
                    helper.assertTrue(!interrupted.get(), "Grace absorbs recipe boundaries without any native interruption");
                }).thenWaitUntil(() -> helper.assertTrue(products(machine) == 4 && output.getGeneratedSpeed() == 0F
                                && shaft.getSpeed() == 0F && fan.getSpeed() == 0F,
                        "The last batch finishes and unrenewed output stops after its grace period"))
                .thenSucceed();
    }

    private static void persistenceAndRemoval(GameTestHelper helper) {
        BlockPos inputPos = PORT.south();
        BlockPos shaftPos = SHAFT.south();
        BlockPos motorPos = MOTOR.south();
        BlockPos outputPos = PORT;
        BlockPos reserveMotorPos = outputPos.north();
        helper.setBlock(inputPos, portState(StressInterfaceKind.INPUT));
        helper.setBlock(outputPos, portState(StressInterfaceKind.OUTPUT));
        helper.setBlock(shaftPos, axis(AllBlocks.SHAFT.get(), Direction.Axis.Z));
        motor(helper, motorPos, Direction.NORTH, -64);
        CreativeMotorBlockEntity reserveMotor = motor(helper, reserveMotorPos, Direction.SOUTH, 32);
        StressInputBlockEntity input = helper.getBlockEntity(inputPos);
        StressOutputBlockEntity output = helper.getBlockEntity(outputPos);
        bind(helper, input, output);
        StressSession session = new StressSession();
        AtomicReference<List<SavedKinetic>> saved = new AtomicReference<>();
        AtomicReference<KineticNetwork> oldNetwork = new AtomicReference<>();
        AtomicReference<KineticNetwork> restoredNetwork = new AtomicReference<>();
        AtomicReference<CreativeMotorBlockEntity> restoredMotor = new AtomicReference<>();
        AtomicReference<CreativeMotorBlockEntity> restoredReserveMotor = new AtomicReference<>();
        AtomicReference<StressInputBlockEntity> restoredInput = new AtomicReference<>();
        AtomicReference<StressOutputBlockEntity> restoredOutput = new AtomicReference<>();
        AtomicReference<Double> baselineStress = new AtomicReference<>();
        AtomicReference<Double> baselineCapacity = new AtomicReference<>();
        AtomicReference<Double> oldLoad = new AtomicReference<>();
        AtomicReference<Double> oldCapacity = new AtomicReference<>();
        helper.startSequence().thenWaitUntil(() -> helper.assertTrue(facet(input).state().connected()
                        && fixtureAssociated(helper, input) && fixtureAssociated(helper, output)
                        && facet(output).networkIdentity() == facet(input).networkIdentity()
                        && input.getSpeed() == 64F && output.getSpeed() == 64F
                        && reserveMotor.hasSource() && reserveMotor.getSpeed() == 64F,
                "Persistence fixture joins the native source before contributing"))
                .thenExecute(() -> {
                    oldNetwork.set((KineticNetwork) facet(input).networkIdentity());
                    baselineStress.set((double) oldNetwork.get().calculateStress());
                    baselineCapacity.set((double) oldNetwork.get().calculateCapacity());
                    apply(helper, input, session, 0, 2D, 0D);
                    apply(helper, output, session, 1, 5D, 16D);
                }).thenWaitUntil(() -> {
                    helper.assertTrue(output.hasSource() && output.getSpeed() == 64F && output.getGeneratedSpeed() == 16F,
                            "Persistence snapshot includes an output overpowered by a faster real motor");
                    close(helper, oldNetwork.get().getActualCapacityOf(output), 5D * 16D,
                            "Saved output capacity uses own generated RPM, not external network RPM");
                }).thenExecute(() -> {
                    oldNetwork.get().updateNetwork();
                    oldNetwork.get().sync();
                    oldLoad.set((double) oldNetwork.get().getActualStressOf(input));
                    oldCapacity.set((double) oldNetwork.get().getActualCapacityOf(output));
                    // Save every member before cleanup, as a full network save does before unload.
                    saved.set(List.of(saveNative(helper, motorPos), saveNative(helper, shaftPos),
                            saveNative(helper, inputPos), saveNative(helper, outputPos), saveNative(helper, reserveMotorPos)));
                    for (SavedKinetic member : List.of(saved.get().get(0), saved.get().get(1), saved.get().get(4))) {
                        CompoundTag networkTag = member.tag().getCompound("Network");
                        close(helper, networkTag.getFloat("Stress"), baselineStress.get() + oldLoad.get(),
                                "Ordinary motor/shaft disk tags retain the full active input load");
                        close(helper, networkTag.getFloat("Capacity"), baselineCapacity.get() + oldCapacity.get(),
                                "Ordinary motor/shaft disk tags retain overpowered output capacity");
                    }
                    session.releaseAll();
                    helper.setBlock(reserveMotorPos, Blocks.AIR);
                    helper.setBlock(outputPos, Blocks.AIR);
                    helper.setBlock(inputPos, Blocks.AIR);
                    helper.setBlock(shaftPos, Blocks.AIR);
                    helper.setBlock(motorPos, Blocks.AIR);
                    helper.assertTrue(oldNetwork.get().members.isEmpty(), "Removing all saved members empties the old native network");

                    CreativeMotorBlockEntity source = (CreativeMotorBlockEntity) restoreNative(helper, saved.get().get(0));
                    restoredMotor.set(source);
                    KineticNetwork fresh = source.getOrCreateNetwork();
                    restoredNetwork.set(fresh);
                    helper.assertTrue(fresh != oldNetwork.get() && !fresh.initialized && fresh.members.isEmpty(),
                            "Ordinary source restores its saved ID into a new, uninitialized native network");
                    // Invoke the real native initialization before tick's attachment can take the ordinary add path.
                    source.initialize();
                    helper.assertTrue(fresh.initialized && fresh.members.size() == 1,
                            "Motor initialize reaches native initFromTE and addSilently");
                    close(helper, fresh.calculateStress(), baselineStress.get() + oldLoad.get(),
                            "initFromTE preserves the absent interface's old load in unloaded totals");
                    close(helper, fresh.calculateCapacity(), baselineCapacity.get() + oldCapacity.get(),
                            "initFromTE preserves the absent output's own-RPM capacity in unloaded totals");
                    KineticBlockEntity shaft = restoreNative(helper, saved.get().get(1));
                    shaft.initialize();
                    helper.assertTrue(shaft.getOrCreateNetwork() == fresh && fresh.members.size() == 2
                                    && fresh.getSize() == saved.get().get(0).tag().getCompound("Network").getInt("Size"),
                            "Ordinary shaft silently rejoins while interfaces and reserve source remain unloaded");
                    close(helper, fresh.calculateStress(), baselineStress.get() + oldLoad.get(),
                            "Restoring ordinary members cannot silently discard old recipe SU");
                    close(helper, fresh.calculateCapacity(), baselineCapacity.get() + oldCapacity.get(),
                            "Restoring ordinary members cannot silently discard old recipe source capacity");
                    restoredInput.set((StressInputBlockEntity) restoreNative(helper, saved.get().get(2)));
                    restoredInput.get().onMachineFormed(helper.absolutePos(BlockPos.ZERO));
                    restoredInput.get().initialize();
                }).thenWaitUntil(() -> {
                    helper.assertTrue(facet(restoredInput.get()).networkIdentity() == restoredNetwork.get()
                                    && restoredInput.get().getSpeed() == 64F,
                            "Restored input rejoins the network seeded by the saved ordinary source");
                    close(helper, facet(restoredInput.get()).state().baseContribution(), 0D,
                            "No recipe owner is restored into the input ledger");
                    close(helper, restoredNetwork.get().calculateStress(), baselineStress.get(),
                            "Input restoration exactly settles its old unloaded base-stress times local RPM");
                    close(helper, restoredNetwork.get().calculateCapacity(), baselineCapacity.get() + oldCapacity.get(),
                            "Input settlement preserves the not-yet-restored output's unloaded capacity");
                    helper.assertTrue(restoredNetwork.get().getSize() == restoredNetwork.get().members.size() + 2,
                            "Only the pending output and ordinary reserve source remain counted as unloaded");
                }).thenExecute(() -> {
                    restoredOutput.set((StressOutputBlockEntity) restoreNative(helper, saved.get().get(3)));
                    restoredOutput.get().onMachineFormed(helper.absolutePos(BlockPos.ZERO));
                    restoredOutput.get().initialize();
                }).thenWaitUntil(() -> {
                    cleanReattachment(helper, restoredInput.get(), restoredOutput.get(),
                            baselineStress.get(), baselineCapacity.get());
                    helper.assertTrue(facet(restoredOutput.get()).networkIdentity() == restoredNetwork.get()
                                    && restoredNetwork.get().getSize() == restoredNetwork.get().members.size() + 1,
                            "Output settles own-RPM capacity exactly, preserving the still-unloaded ordinary reserve source");
                }).thenExecute(() -> {
                    // A surviving unloaded source prevents an excessive output deduction from being hidden by clamping to zero.
                    restoredReserveMotor.set((CreativeMotorBlockEntity) restoreNative(helper, saved.get().get(4)));
                    restoredReserveMotor.get().initialize();
                }).thenWaitUntil(() -> {
                    helper.assertTrue(restoredReserveMotor.get().getOrCreateNetwork() == restoredNetwork.get()
                                    && restoredReserveMotor.get().getSpeed() == 64F
                                    && restoredNetwork.get().getSize() == restoredNetwork.get().members.size(),
                            "Last ordinary source silently restores with no unloaded members or double deductions");
                    cleanReattachment(helper, restoredInput.get(), restoredOutput.get(),
                            baselineStress.get(), baselineCapacity.get());
                }).thenExecute(() -> {
                    for (int repeat = 0; repeat < 2; repeat++) {
                        restoredInput.get().initialize();
                        restoredOutput.get().initialize();
                    }
                    cleanReattachment(helper, restoredInput.get(), restoredOutput.get(),
                            baselineStress.get(), baselineCapacity.get());
                    helper.assertTrue(restoredNetwork.get().getSize() == restoredNetwork.get().members.size(),
                            "Repeated native initialization cannot settle an unloaded contribution or member twice");
                    restoredNetwork.get().updateCapacityFor(restoredMotor.get(), 1F);
                    restoredNetwork.get().updateCapacityFor(restoredReserveMotor.get(), 0F);
                })
                .thenWaitUntil(() -> {
                    // The former input load (128 SU) exceeds this live capacity (64 SU).
                    cleanReattachment(helper, restoredInput.get(), restoredOutput.get(), baselineStress.get(), 64D);
                    helper.assertTrue(!restoredMotor.get().isOverStressed() && !restoredInput.get().isOverStressed()
                                    && !restoredOutput.get().isOverStressed()
                                    && ((KineticBlockEntity) helper.getBlockEntity(shaftPos)).getSpeed() == 64F,
                            "A smaller real source budget still rotates: no unloaded ghost load or ghost source masks overload");
                })
                .thenExecute(() -> {
                    apply(helper, restoredInput.get(), session, 0, 0.5D, 0D);
                    apply(helper, restoredOutput.get(), session, 1, 5D, 16D);
                    CompoundTag inputBeforeUnload = restoredInput.get().saveWithoutMetadata(helper.getLevel().registryAccess());
                    CompoundTag outputBeforeUnload = restoredOutput.get().saveWithoutMetadata(helper.getLevel().registryAccess());
                    restoredInput.get().onChunkUnloaded();
                    restoredOutput.get().onChunkUnloaded();
                    helper.assertTrue(restoredInput.get().isChunkUnloaded() && restoredOutput.get().isChunkUnloaded(),
                            "Both native ports enter the unloaded lifecycle before ownership is cleared");
                    close(helper, facet(restoredInput.get()).ownedBaseStress(session, 0), 0D,
                            "Unloading releases newly acquired input ownership");
                    close(helper, facet(restoredOutput.get()).ownedBaseStress(session, 1), 0D,
                            "Unloading releases newly acquired output ownership");
                    close(helper, facet(restoredInput.get()).state().baseContribution(), 0D,
                            "Unloaded input retains no recipe load");
                    close(helper, restoredOutput.get().getGeneratedSpeed(), 0D,
                            "Unloaded output retains no recipe generation or grace");
                    // The entities remain in this fixture until the following removals. Do not force a
                    // network recalculation here: real unload must not load neighbouring chunks.
                    CompoundTag inputAfterUnload = restoredInput.get().saveWithoutMetadata(helper.getLevel().registryAccess());
                    CompoundTag outputAfterUnload = restoredOutput.get().saveWithoutMetadata(helper.getLevel().registryAccess());
                    helper.assertTrue(inputBeforeUnload.getCompound("Network").equals(inputAfterUnload.getCompound("Network"))
                                    && inputBeforeUnload.getCompound("StressRecovery").equals(inputAfterUnload.getCompound("StressRecovery")),
                            "Input cleanup preserves its debit against the unchanged native cached total");
                    helper.assertTrue(outputBeforeUnload.getCompound("Network").equals(outputAfterUnload.getCompound("Network"))
                                    && outputBeforeUnload.getCompound("StressRecovery").equals(outputAfterUnload.getCompound("StressRecovery")),
                            "Output cleanup preserves its own-RPM debit without updating the unloading network");
                    helper.setBlock(inputPos, Blocks.AIR);
                    helper.setBlock(outputPos, Blocks.AIR);
                    session.releaseAll();
                }).thenWaitUntil(() -> {
                    close(helper, restoredNetwork.get().calculateStress(), baselineStress.get(), "Removal leaves no ghost SU");
                    close(helper, restoredNetwork.get().calculateCapacity(), 64D, "Removal leaves no ghost capacity");
                    helper.assertTrue(restoredNetwork.get().members.keySet().stream()
                                    .noneMatch(entity -> entity.getBlockPos().equals(helper.absolutePos(inputPos)))
                                    && restoredNetwork.get().sources.keySet().stream()
                                    .noneMatch(entity -> entity.getBlockPos().equals(helper.absolutePos(outputPos))),
                            "Native recalculation prunes removed load/source membership");
                }).thenSucceed();
    }

    private static SavedKinetic saveNative(GameTestHelper helper, BlockPos pos) {
        KineticBlockEntity entity = helper.getBlockEntity(pos);
        return new SavedKinetic(pos, entity.getBlockState(), entity.saveWithFullMetadata(helper.getLevel().registryAccess()));
    }

    private static KineticBlockEntity restoreNative(GameTestHelper helper, SavedKinetic saved) {
        helper.setBlock(saved.position(), saved.state());
        BlockPos pos = helper.absolutePos(saved.position());
        // Chunk deserialization creates/reads a fresh entity, not the placement entity marked wasMoved by Create.
        BlockEntity restored = BlockEntity.loadStatic(pos, helper.getLevel().getBlockState(pos), saved.tag().copy(),
                helper.getLevel().registryAccess());
        helper.assertTrue(restored instanceof KineticBlockEntity, "Saved native entity deserializes through its registered type");
        helper.getLevel().removeBlockEntity(pos);
        helper.getLevel().setBlockEntity(restored);
        return (KineticBlockEntity) restored;
    }

    private static void cleanReattachment(GameTestHelper helper, StressInputBlockEntity input,
                                          StressOutputBlockEntity output, double stress, double capacity) {
        StressFacet in = facet(input);
        StressFacet out = facet(output);
        helper.assertTrue(in.state().connected() && out.networkIdentity() == in.networkIdentity()
                        && input.getSpeed() == 64F && output.getSpeed() == 64F,
                "Restored ports reattach to a live source rather than persisted transient network state");
        helper.assertTrue(in.state().baseContribution() == 0D && out.state().baseContribution() == 0D
                        && output.getGeneratedSpeed() == 0F,
                "Read/reload without a running recipe never restores owned load or generation");
        KineticNetwork network = (KineticNetwork) in.networkIdentity();
        close(helper, network.calculateStress(), stress, "Restored native network contains no ghost load");
        close(helper, network.calculateCapacity(), capacity, "Restored native network contains no ghost source capacity");
    }

    private static void onlyForeignCapacity(GameTestHelper helper, StressOutputBlockEntity output,
                                            CreativeMotorBlockEntity motor) {
        helper.assertTrue(facet(output).state().connected(), "Revoked output remains a member of the external network");
        KineticNetwork network = (KineticNetwork) facet(output).networkIdentity();
        close(helper, network.calculateCapacity(), network.getActualCapacityOf(motor),
                "Revocation removes all recipe capacity from the external network");
        close(helper, facet(output).state().actualContribution(), 0D, "Revocation removes the output's actual SU");
    }

    private static GearRig gearRig(GameTestHelper helper, boolean rotated, List<BlockState> connectedStates) {
        List<BlockPos> positions = List.of(new BlockPos(1, 2, 4), new BlockPos(1, 2, 3),
                new BlockPos(1, 2, 2), new BlockPos(1, 2, 1), new BlockPos(2, 2, 1),
                new BlockPos(2, 2, 2), new BlockPos(2, 2, 3), new BlockPos(3, 3, 3), new BlockPos(3, 3, 4)).stream()
                .map(pos -> rotated ? new BlockPos(4 - pos.getZ(), pos.getY(), pos.getX()) : pos).toList();
        List<BlockState> states = connectedStates == null ? List.of(
                AllBlocks.CREATIVE_MOTOR.get().defaultBlockState().setValue(CreativeMotorBlock.FACING, Direction.NORTH),
                axis(AllBlocks.SHAFT.get(), Direction.Axis.Z), portState(StressInterfaceKind.INPUT),
                axis(AllBlocks.ENCASED_CHAIN_DRIVE.get(), Direction.Axis.Z),
                axis(AllBlocks.ENCASED_CHAIN_DRIVE.get(), Direction.Axis.Z), portState(StressInterfaceKind.OUTPUT),
                axis(AllBlocks.LARGE_COGWHEEL.get(), Direction.Axis.Z), axis(AllBlocks.COGWHEEL.get(), Direction.Axis.Z),
                portState(StressInterfaceKind.INPUT)) : connectedStates;
        for (int i = 0; i < positions.size(); i++) {
            helper.setBlock(positions.get(i), rotated ? states.get(i).rotate(Rotation.CLOCKWISE_90) : states.get(i));
        }
        CreativeMotorBlockEntity motor = helper.getBlockEntity(positions.get(0));
        // NORTH reverses the scroll sign; rotation changes the axis/sign but preserves absolute RPM.
        motor.getBehaviour(ScrollValueBehaviour.TYPE).setValue(-16);
        StressInputBlockEntity slow = helper.getBlockEntity(positions.get(2));
        StressOutputBlockEntity output = helper.getBlockEntity(positions.get(5));
        StressInputBlockEntity fast = helper.getBlockEntity(positions.get(8));
        bind(helper, slow, output, fast);
        return new GearRig(positions, motor, helper.getBlockEntity(positions.get(1)), slow,
                helper.getBlockEntity(positions.get(3)), output, fast);
    }

    private static ControllerRig controller(GameTestHelper helper, String name, StressInterfaceKind kind) {
        helper.setBlock(ITEMS, ModBlocks.BLOCKS.get("item_input_bus").get());
        helper.setBlock(PRODUCTS, ModBlocks.BLOCKS.get("item_output_bus").get());
        ResourceLocation baseId = MMCR.id("test_cube");
        helper.setBlock(CONTROLLER, ModBlocks.controllerFor(baseId).get().defaultBlockState()
                .setValue(MachineControllerBlock.FACING, Direction.SOUTH));
        ResourceLocation id = MMCR.id(name);
        DynamicMachine machine = new DynamicMachine(id, "machine.mmcr_test." + name, new BlockArray(Map.of(
                new BlockPos(0, 1, 0), new BlockPredicate.OfBlock(ModBlocks.BLOCKS.get(kind.id()).get()),
                new BlockPos(-1, 0, 0), new BlockPredicate.OfBlock(ModBlocks.BLOCKS.get("item_input_bus").get()),
                new BlockPos(0, -1, 0), new BlockPredicate.OfBlock(ModBlocks.BLOCKS.get("item_output_bus").get()))),
                MachineRegistry.getMachine(baseId).controller());
        if (!MachineRegistry.containsStatic(id)) MachineRegistry.register(machine);
        MachineControllerBlockEntity controller = helper.getBlockEntity(CONTROLLER);
        controller.setMachine(machine);
        controller.setStructureCheckIntervalForTesting(1);
        controller.requestImmediateStructureCheck();
        return new ControllerRig(id, controller, helper.getBlockEntity(ITEMS), helper.getBlockEntity(PRODUCTS));
    }

    private static CreativeMotorBlockEntity motor(GameTestHelper helper, BlockPos pos, Direction facing, int speed) {
        helper.setBlock(pos, AllBlocks.CREATIVE_MOTOR.get().defaultBlockState().setValue(CreativeMotorBlock.FACING, facing));
        CreativeMotorBlockEntity motor = helper.getBlockEntity(pos);
        motor.getBehaviour(ScrollValueBehaviour.TYPE).setValue(speed);
        return motor;
    }

    private static BlockState portState(StressInterfaceKind kind) {
        return axis(ModBlocks.BLOCKS.get(kind.id()).get(), Direction.Axis.Z);
    }

    private static BlockState axis(Block block, Direction.Axis axis) {
        return block.defaultBlockState().setValue(RotatedPillarKineticBlock.AXIS, axis);
    }

    private static StressFacet facet(CapabilityHost host) {
        return host.capabilitySnapshot().facets(StressFacet.class).getFirst();
    }

    private static void bind(GameTestHelper helper, KineticBlockEntity... ports) {
        ResourceLocation baseId = MMCR.id("test_cube");
        helper.setBlock(BlockPos.ZERO, ModBlocks.controllerFor(baseId).get().defaultBlockState()
                .setValue(MachineControllerBlock.FACING, Direction.SOUTH));
        BlockPos origin = helper.absolutePos(BlockPos.ZERO);
        Map<BlockPos, BlockPredicate> pattern = new LinkedHashMap<>();
        for (KineticBlockEntity port : ports) {
            pattern.put(port.getBlockPos().subtract(origin), new BlockPredicate.OfBlock(port.getBlockState().getBlock()));
        }
        ResourceLocation id = MMCR.id("create_stress_fixture_" + ports[0].getBlockPos().subtract(origin).asLong()
                + "_" + ports.length + "_" + ports[0].getBlockState().getValue(RotatedPillarKineticBlock.AXIS).getSerializedName());
        DynamicMachine machine = new DynamicMachine(id, "machine.mmcr_test.create_stress_fixture", new BlockArray(pattern),
                MachineRegistry.getMachine(baseId).controller());
        if (!MachineRegistry.containsStatic(id)) MachineRegistry.register(machine);
        MachineControllerBlockEntity controller = helper.getBlockEntity(BlockPos.ZERO);
        controller.setMachine(machine);
        controller.setStructureCheckIntervalForTesting(1);
        controller.requestImmediateStructureCheck();
    }

    private static void apply(GameTestHelper helper, CapabilityHost port, StressSession session,
                              int index, double baseStress, double rpm) {
        KineticBlockEntity entity = (KineticBlockEntity) port;
        helper.assertTrue(fixtureAssociated(helper, entity), "Standalone session commits require a formed, discovered port association");
        ((MachinePort) port).onMachineFormed(helper.absolutePos(BlockPos.ZERO));
        var result = facet(port).apply(session, index, baseStress, rpm);
        helper.assertTrue(result.success(), "Native contribution commit succeeds: " + result.status());
    }

    private static boolean fixtureAssociated(GameTestHelper helper, KineticBlockEntity port) {
        MachineControllerBlockEntity controller = helper.getBlockEntity(BlockPos.ZERO);
        return controller.structureSnapshot().formed()
                && controller.runtimeSnapshot().linkedPortPositions().contains(port.getBlockPos());
    }

    private static void close(GameTestHelper helper, double actual, double expected, String message) {
        helper.assertTrue(Math.abs(actual - expected) <= 0.001D, message + ": " + actual + " != " + expected);
    }

    private static void failure(GameTestHelper helper, MachineControllerBlockEntity controller, FailureReason reason) {
        var failure = controller.runtimeSnapshot().crafting().failure();
        helper.assertTrue(failure != null && reason.equals(failure.reason()), "Controller reports " + reason + ": " + failure);
    }

    private static int products(ControllerRig machine) {
        ItemStack stack = machine.products().nativeItemHandler().getStackInSlot(0);
        return stack.is(Items.DIAMOND) ? stack.getCount() : 0;
    }

    /** @author howxu <dev@howxu.cn> */
    private record GearRig(List<BlockPos> positions, CreativeMotorBlockEntity motor, KineticBlockEntity shaft,
                           StressInputBlockEntity slow, KineticBlockEntity chain, StressOutputBlockEntity output,
                           StressInputBlockEntity fast) {}

    /** @author howxu <dev@howxu.cn> */
    private record SavedKinetic(BlockPos position, BlockState state, CompoundTag tag) {}

    /** @author howxu <dev@howxu.cn> */
    private record ControllerRig(ResourceLocation id, MachineControllerBlockEntity controller,
                                 ItemBusBlockEntity items, ItemBusBlockEntity products) {}
}

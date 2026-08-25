package cn.howxu.mmcr.internal.tile;

import cn.howxu.mmcr.LevelStub;
import cn.howxu.mmcr.api.machine.BlockArray;
import cn.howxu.mmcr.api.machine.BlockPredicate;
import cn.howxu.mmcr.api.machine.BlockRotator;
import cn.howxu.mmcr.api.machine.MachineControllerSpec;
import cn.howxu.mmcr.api.machine.MachineDefinitions;
import cn.howxu.mmcr.api.machine.MachineRegistration;
import cn.howxu.mmcr.api.machine.MachineRegistry;
import cn.howxu.mmcr.api.machine.MachineStructureDefinition;
import cn.howxu.mmcr.api.machine.MachineStructureRequirements;
import cn.howxu.mmcr.api.machine.MachineStructureRegistry;
import cn.howxu.mmcr.api.machine.level.LevelMismatch;
import cn.howxu.mmcr.api.machine.level.LevelModifier;
import cn.howxu.mmcr.api.machine.level.LevelType;
import cn.howxu.mmcr.api.machine.level.MachineLevel;
import cn.howxu.mmcr.api.machine.level.MachineLevelRegistry;
import cn.howxu.mmcr.test.TestBootstrap;
import cn.howxu.mmcr.test.RuntimeTestFixtures;
import cn.howxu.mmcr.internal.block.MachineControllerBlock;
import cn.howxu.mmcr.internal.preview.MultiblockPreviewSnapshot;
import cn.howxu.mmcr.test.TestBootstrap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import java.util.ArrayList;
import java.util.HashSet;

import cn.howxu.mmcr.api.machine.Machine;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import static org.assertj.core.api.Assertions.assertThat;

class MachineControllerLevelTest {
    private static final Identifier MACHINE_ID = Identifier.parse("test:level_machine");
    private static final Identifier COIL_TYPE = Identifier.parse("test:coil");
    private MachineLevel copper;
    private MachineLevel kanthal;

    @BeforeAll
    static void bootstrapMinecraft() throws Exception {
        TestBootstrap.bootstrap();
    }

    @AfterEach
    void cleanup() {
        MachineDefinitions.clearForTesting();
        MachineRegistry.clearForTesting();
        MachineStructureRegistry.clearForTesting();
        TestBootstrap.beginRegistration();
        TestBootstrap.freezeRegistration();
    }

    @Test
    void formsWithDispersedSlotsOfTheSameLevel() throws Exception {
        MachineControllerBlockEntity controller = controllerWithSlots(Blocks.COPPER_BLOCK, Blocks.COPPER_BLOCK);

        assertThat(tryForm(controller)).isTrue();
        assertThat(controller.runtimeSnapshot().foundLevels()).containsEntry(COIL_TYPE, copper);
        assertThat(controller.structureSnapshot().formed()).isTrue();
    }

    @Test
    void rejectsMixedLevelsWithCoordinateAndBothIds() throws Exception {
        MachineControllerBlockEntity controller = controllerWithSlots(Blocks.COPPER_BLOCK, Blocks.IRON_BLOCK);

        assertThat(tryForm(controller)).isFalse();
        assertThat(controller.structureSnapshot().lastStructureError()).isInstanceOf(LevelMismatch.class);
        LevelMismatch mismatch = (LevelMismatch) controller.structureSnapshot().lastStructureError();
        assertThat(mismatch.expected().id()).isEqualTo(copper.id());
        assertThat(mismatch.actual().id()).isEqualTo(kanthal.id());
        assertThat(mismatch.worldPos()).isEqualTo(controller.getBlockPos().offset(2, 0, 0));
    }

    @Test
    void rejectsUnresolvedLevelSlotWithoutCrashing() throws Exception {
        MachineControllerBlockEntity controller = controllerWithUnresolvedSlot();

        assertThat(tryForm(controller)).isFalse();
        assertThat(controller.structureSnapshot().lastStructureError()).isInstanceOf(LevelMismatch.class);
        LevelMismatch mismatch = (LevelMismatch) controller.structureSnapshot().lastStructureError();
        assertThat(mismatch.expected()).isEqualTo(copper);
        assertThat(mismatch.actual()).isNull();
        assertThat(mismatch.worldPos()).isEqualTo(controller.getBlockPos().offset(2, 0, 0));
    }

    @Test
    void defaultControllerSpecFormsHorizontallyAndRejectsVerticalFacing() throws Exception {
        MachineControllerBlockEntity controller = controllerWithPattern(MachineControllerSpec.defaultsFor(MACHINE_ID), Direction.SOUTH,
                Map.of(new BlockPos(1, 0, 0), Blocks.IRON_BLOCK));

        assertThat(tryForm(controller, Direction.SOUTH)).isTrue();
        resetFormed(controller);
        assertThat(tryForm(controller, Direction.UP)).isFalse();
    }

    @Test
    void allowVerticalFacingFormsHorizontallyAndVerticallyFromSameBasePattern() throws Exception {
        MachineControllerSpec spec = controllerSpec(true, false, false);
        BlockPos rawEast = new BlockPos(1, 0, 0);
        BlockPos verticalEast = BlockRotator.rotateSouthTo(rawEast, Direction.UP, Direction.SOUTH);

        assertThat(tryForm(controllerWithPattern(spec, Direction.SOUTH,
                Map.of(rawEast, Blocks.IRON_BLOCK)), Direction.SOUTH)).isTrue();
        assertThat(tryForm(controllerWithPattern(spec, Direction.UP,
                Map.of(verticalEast, Blocks.IRON_BLOCK)), Direction.UP)).isTrue();
    }

    @Test
    void allowVerticalFacingFormsUsingRollAwareVerticalRotation() throws Exception {
        MachineControllerSpec spec = controllerSpec(true, false, false);
        BlockPos rawForward = new BlockPos(0, 0, 1);
        BlockPos worldUp = BlockRotator.rotateSouthTo(rawForward, Direction.UP, Direction.SOUTH);

        assertThat(tryForm(controllerWithPattern(spec, Direction.UP, Direction.SOUTH,
                Map.of(rawForward, Blocks.IRON_BLOCK), Map.of(worldUp, Blocks.IRON_BLOCK)), Direction.UP)).isTrue();
    }

    @Test
    void requireVerticalFacingRejectsHorizontalAndAcceptsVerticalFormation() throws Exception {
        MachineControllerSpec spec = controllerSpec(true, true, false);
        BlockPos rawEast = new BlockPos(1, 0, 0);
        BlockPos verticalEast = BlockRotator.rotateSouthTo(rawEast, Direction.UP, Direction.SOUTH);

        assertThat(tryForm(controllerWithPattern(spec, Direction.SOUTH,
                Map.of(rawEast, Blocks.IRON_BLOCK)), Direction.SOUTH)).isFalse();
        assertThat(tryForm(controllerWithPattern(spec, Direction.UP,
                Map.of(verticalEast, Blocks.IRON_BLOCK)), Direction.UP)).isTrue();
    }

    @Test
    void previewSnapshotForVerticalAllowedMachineFollowsHorizontalControllerFacing() throws Exception {
        MachineControllerSpec spec = controllerSpec(true, false, false);
        BlockPos horizontalSouthPos = new BlockPos(1, 0, 1);
        BlockPos verticalUpRolledEastPos = new BlockPos(1, 0, -1);
        MachineControllerBlockEntity controller = controllerWithPattern(spec, Direction.SOUTH, Direction.EAST,
                Map.of(horizontalSouthPos, Blocks.IRON_BLOCK), Map.of());

        MultiblockPreviewSnapshot snapshot = controller.createStructurePreviewSnapshot(16).orElseThrow();

        assertThat(snapshot.entries())
                .extracting(MultiblockPreviewSnapshot.Entry::relativePos)
                // SOUTH leaves the base pattern at (1,0,1); UP with the current EAST roll would rotate it to (1,0,-1).
                .contains(horizontalSouthPos)
                .doesNotContain(verticalUpRolledEastPos);
    }

    @Test
    void previewSnapshotForVerticalAllowedMachineUsesRollAwareVerticalRotation() throws Exception {
        MachineControllerSpec spec = controllerSpec(true, false, false);
        BlockPos rawForward = new BlockPos(0, 0, 1);
        BlockPos verticalUpPos = BlockRotator.rotateSouthTo(rawForward, Direction.UP, Direction.SOUTH);
        MachineControllerBlockEntity controller = controllerWithPattern(spec, Direction.UP, Direction.SOUTH,
                Map.of(rawForward, Blocks.IRON_BLOCK), Map.of());

        MultiblockPreviewSnapshot snapshot = controller.createStructurePreviewSnapshot(16).orElseThrow();

        assertThat(snapshot.entries())
                .extracting(MultiblockPreviewSnapshot.Entry::relativePos)
                .contains(verticalUpPos)
                .doesNotContain(rawForward);
    }

    private MachineControllerBlockEntity controllerWithSlots(Block first, Block second) throws Exception {
        MachineDefinitions.clearForTesting();
        MachineDefinitions.register(MachineRegistration.builder(MACHINE_ID).localizedName("Level Machine").build());
        TestBootstrap.beginRegistration();
        TestBootstrap.registerType(new LevelType(COIL_TYPE, Component.literal("Coils")));
        copper = level("test:copper", 1, Blocks.COPPER_BLOCK);
        kanthal = level("test:kanthal", 2, Blocks.IRON_BLOCK);
        TestBootstrap.registerLevel(copper);
        TestBootstrap.registerLevel(kanthal);
        TestBootstrap.freezeRegistration();

        Map<BlockPos, BlockPredicate> pattern = new LinkedHashMap<>();
        BlockPos firstSlot = new BlockPos(1, 0, 0);
        BlockPos secondSlot = new BlockPos(2, 0, 0);
        pattern.put(firstSlot, new BlockPredicate.AnyOf(List.of(copper.statePredicate(), kanthal.statePredicate())));
        pattern.put(secondSlot, new BlockPredicate.AnyOf(List.of(copper.statePredicate(), kanthal.statePredicate())));
        BlockArray blockArray = new BlockArray(pattern, Map.of(), Map.of(firstSlot, 'A', secondSlot, 'B'));
        MachineStructureRegistry.replaceDynamic(Map.of(MACHINE_ID, new MachineStructureDefinition(
                MACHINE_ID, blockArray, null, null, List.of(), MachineStructureRequirements.builder()
                .levelSlot('A', COIL_TYPE)
                .levelSlot('B', COIL_TYPE)
                .build(blockArray))));

        BlockPos controllerPos = new BlockPos(10, 4, 10);
        MachineControllerBlockEntity controller = RuntimeTestFixtures.controllerEntity(cn.howxu.mmcr.MMCR.id("test_cube"), controllerPos);
        controller.setMachine(MachineRegistry.getMachine(MACHINE_ID));
        var controllerState = controller.getBlockState()
                .setValue(MachineControllerBlock.FACING, Direction.SOUTH)
                .setValue(MachineControllerBlock.ROLL_FACING, Direction.NORTH);
        setField(BlockEntity.class, controller, "blockState", controllerState);
        Level level = LevelStub.create(Map.of(
                controllerPos, controllerState.getBlock(),
                controllerPos.offset(1, 0, 0), first,
                controllerPos.offset(2, 0, 0), second), List.of(controller));
        controller.setLevel(level);
        return controller;
    }

    private MachineControllerBlockEntity controllerWithUnresolvedSlot() throws Exception {
        MachineControllerBlockEntity controller = controllerWithSlots(Blocks.COPPER_BLOCK, Blocks.COPPER_BLOCK);
        Map<BlockPos, BlockPredicate> pattern = new LinkedHashMap<>();
        BlockPos firstSlot = new BlockPos(1, 0, 0);
        BlockPos secondSlot = new BlockPos(2, 0, 0);
        pattern.put(firstSlot, new BlockPredicate.AnyOf(List.of(copper.statePredicate(), kanthal.statePredicate())));
        pattern.put(secondSlot, new BlockPredicate.OfBlock(Blocks.GOLD_BLOCK));
        BlockArray blockArray = new BlockArray(pattern, Map.of(), Map.of(firstSlot, 'A', secondSlot, 'B'));
        MachineStructureRegistry.replaceDynamic(Map.of(MACHINE_ID, new MachineStructureDefinition(
                MACHINE_ID, blockArray, null, null, List.of(), MachineStructureRequirements.builder()
                .levelSlot('A', COIL_TYPE)
                .levelSlot('B', COIL_TYPE)
                .build(blockArray))));
        BlockPos controllerPos = controller.getBlockPos();
        controller.setLevel(LevelStub.create(Map.of(
                controllerPos, controller.getBlockState().getBlock(),
                controllerPos.offset(1, 0, 0), Blocks.COPPER_BLOCK,
                controllerPos.offset(2, 0, 0), Blocks.GOLD_BLOCK), List.of(controller)));
        return controller;
    }

    private static MachineControllerBlockEntity allocateController() throws Exception {
        Field unsafeField = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
        unsafeField.setAccessible(true);
        return (MachineControllerBlockEntity) ((sun.misc.Unsafe) unsafeField.get(null)).allocateInstance(MachineControllerBlockEntity.class);
    }

    private static MachineControllerBlockEntity controllerWithPattern(MachineControllerSpec spec, Direction facing,
                                                                      Map<BlockPos, Block> blocks) throws Exception {
        return controllerWithPattern(spec, facing, Direction.SOUTH,
                Map.of(new BlockPos(1, 0, 0), Blocks.IRON_BLOCK), blocks);
    }

    private static MachineControllerBlockEntity controllerWithPattern(MachineControllerSpec spec, Direction facing,
                                                                      Direction rollFacing,
                                                                      Map<BlockPos, Block> patternBlocks,
                                                                      Map<BlockPos, Block> blocks) throws Exception {
        MachineDefinitions.clearForTesting();
        MachineDefinitions.register(MachineRegistration.builder(MACHINE_ID)
                .localizedName("Facing Machine")
                .controllerSpec(spec)
                .build());
        Map<BlockPos, BlockPredicate> patternPredicates = new LinkedHashMap<>();
        patternBlocks.forEach((pos, block) -> patternPredicates.put(pos, new BlockPredicate.OfBlock(block)));
        BlockArray pattern = new BlockArray(patternPredicates);
        MachineStructureRegistry.replaceDynamic(Map.of(MACHINE_ID, new MachineStructureDefinition(
                MACHINE_ID, pattern, null, null, List.of(), MachineStructureRequirements.EMPTY)));

        BlockPos controllerPos = new BlockPos(10, 4, 10);
        MachineControllerBlockEntity controller = RuntimeTestFixtures.controllerEntity(cn.howxu.mmcr.MMCR.id("test_cube"), controllerPos);
        controller.setMachine(MachineRegistry.getMachine(MACHINE_ID));
        var controllerState = controller.getBlockState()
                .setValue(MachineControllerBlock.FACING, facing)
                .setValue(MachineControllerBlock.ROLL_FACING, rollFacing);
        setField(BlockEntity.class, controller, "blockState", controllerState);
        Map<BlockPos, Block> levelBlocks = new LinkedHashMap<>();
        levelBlocks.put(controllerPos, controllerState.getBlock());
        blocks.forEach((pos, block) -> levelBlocks.put(controllerPos.offset(pos), block));
        controller.setLevel(LevelStub.create(levelBlocks, List.of(controller)));
        return controller;
    }

    private static boolean tryForm(MachineControllerBlockEntity controller) throws Exception {
        return tryForm(controller, Direction.SOUTH);
    }

    private static boolean tryForm(MachineControllerBlockEntity controller, Direction facing) throws Exception {
        Method method = MachineControllerBlockEntity.class.getDeclaredMethod("tryFormMachine", Machine.class, Direction.class);
        method.setAccessible(true);
        boolean formed = (boolean) method.invoke(controller, MachineRegistry.getMachine(MACHINE_ID), facing);
        cn.howxu.mmcr.test.RuntimeTestFixtures.republish(controller);
        return formed;
    }

    private static void resetFormed(MachineControllerBlockEntity controller) throws Exception {
        RuntimeTestFixtures.publishStructure(controller, MachineRegistry.getMachine(MACHINE_ID), false, 1,
                controller.getBlockState().getValue(MachineControllerBlock.FACING), Direction.SOUTH);
    }

    private static MachineControllerSpec controllerSpec(boolean allowVerticalFacing, boolean requireVerticalFacing,
                                                        boolean fullyRotationallySymmetric) {
        return new MachineControllerSpec(
                Identifier.fromNamespaceAndPath(MACHINE_ID.getNamespace(), MACHINE_ID.getPath() + "_controller"),
                Identifier.parse("mmcr:block/controller_front"),
                Identifier.parse("mmcr:block/controller_side"),
                Identifier.parse("mmcr:block/controller_top"),
                Identifier.parse("mmcr:block/controller_bottom"),
                allowVerticalFacing,
                fullyRotationallySymmetric,
                requireVerticalFacing);
    }

    private static MachineLevel level(String id, int priority, Block block) {
        return new MachineLevel(Identifier.parse(id), COIL_TYPE, priority,
                new BlockPredicate.OfBlockState(block.defaultBlockState()), ItemStack.EMPTY, LevelModifier.IDENTITY);
    }

    private static void setField(Class<?> declaringClass, Object target, String name, Object value) throws ReflectiveOperationException {
        Field field = declaringClass.getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }
}

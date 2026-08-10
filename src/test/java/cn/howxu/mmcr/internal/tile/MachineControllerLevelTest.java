package cn.howxu.mmcr.internal.tile;

import cn.howxu.mmcr.LevelStub;
import cn.howxu.mmcr.api.machine.BlockArray;
import cn.howxu.mmcr.api.machine.BlockPredicate;
import cn.howxu.mmcr.api.machine.MachineControllerSpec;
import cn.howxu.mmcr.api.machine.MachineDefinitions;
import cn.howxu.mmcr.api.machine.MachineRegistration;
import cn.howxu.mmcr.api.machine.MachineRegistry;
import cn.howxu.mmcr.api.machine.MachineStructureDefinition;
import cn.howxu.mmcr.api.machine.MachineStructureRegistry;
import cn.howxu.mmcr.api.machine.level.LevelMismatch;
import cn.howxu.mmcr.api.machine.level.LevelModifier;
import cn.howxu.mmcr.api.machine.level.LevelType;
import cn.howxu.mmcr.api.machine.level.MachineLevel;
import cn.howxu.mmcr.api.machine.level.MachineLevelRegistry;
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
        MachineLevelRegistry.beginRegistration();
        MachineLevelRegistry.freezeRegistration();
    }

    @Test
    void formsWithDispersedSlotsOfTheSameLevel() throws Exception {
        MachineControllerBlockEntity controller = controllerWithSlots(Blocks.COPPER_BLOCK, Blocks.COPPER_BLOCK);

        assertThat(tryForm(controller)).isTrue();
        assertThat(controller.getFoundLevels()).containsEntry(COIL_TYPE, copper);
        assertThat(controller.isFormed()).isTrue();
    }

    @Test
    void rejectsMixedLevelsWithCoordinateAndBothIds() throws Exception {
        MachineControllerBlockEntity controller = controllerWithSlots(Blocks.COPPER_BLOCK, Blocks.IRON_BLOCK);

        assertThat(tryForm(controller)).isFalse();
        assertThat(controller.getLastStructureError()).isInstanceOf(LevelMismatch.class);
        LevelMismatch mismatch = (LevelMismatch) controller.getLastStructureError();
        assertThat(mismatch.expected().id()).isEqualTo(copper.id());
        assertThat(mismatch.actual().id()).isEqualTo(kanthal.id());
        assertThat(mismatch.worldPos()).isEqualTo(controller.getBlockPos().offset(2, 0, 0));
    }

    private MachineControllerBlockEntity controllerWithSlots(Block first, Block second) throws Exception {
        MachineDefinitions.clearForTesting();
        MachineDefinitions.register(MachineRegistration.builder(MACHINE_ID).localizedName("Level Machine").build());
        MachineLevelRegistry.beginRegistration();
        MachineLevelRegistry.registerType(new LevelType(COIL_TYPE, Component.literal("Coils")));
        copper = level("test:copper", 1, Blocks.COPPER_BLOCK);
        kanthal = level("test:kanthal", 2, Blocks.IRON_BLOCK);
        MachineLevelRegistry.registerLevel(copper);
        MachineLevelRegistry.registerLevel(kanthal);
        MachineLevelRegistry.freezeRegistration();

        Map<BlockPos, BlockPredicate> pattern = new LinkedHashMap<>();
        pattern.put(new BlockPos(1, 0, 0), new BlockPredicate.AnyOf(List.of(copper.statePredicate(), kanthal.statePredicate())));
        pattern.put(new BlockPos(2, 0, 0), new BlockPredicate.AnyOf(List.of(copper.statePredicate(), kanthal.statePredicate())));
        MachineStructureRegistry.replaceDynamic(Map.of(MACHINE_ID, new MachineStructureDefinition(
                MACHINE_ID, new BlockArray(pattern), null, null, List.of(), Map.of(),
                Map.of(new BlockPos(1, 0, 0), COIL_TYPE, new BlockPos(2, 0, 0), COIL_TYPE))));

        MachineControllerBlockEntity controller = allocateController();
        setField(MachineControllerBlockEntity.class, controller, "foundModifiers", new LinkedHashMap<>());
        setField(MachineControllerBlockEntity.class, controller, "foundLevels", Map.of());
        setField(MachineControllerBlockEntity.class, controller, "components", new java.util.ArrayList<>());
        setField(MachineControllerBlockEntity.class, controller, "linkedPortPositions", new java.util.HashSet<>());
        BlockPos controllerPos = new BlockPos(10, 4, 10);
        var controllerBlock = testControllerBlock();
        var controllerState = controllerBlock.defaultBlockState()
                .setValue(cn.howxu.mmcr.internal.block.MachineControllerBlock.FACING, Direction.SOUTH)
                .setValue(cn.howxu.mmcr.internal.block.MachineControllerBlock.ROLL_FACING, Direction.NORTH);
        setField(BlockEntity.class, controller, "worldPosition", controllerPos);
        setField(BlockEntity.class, controller, "blockState", controllerState);
        Level level = LevelStub.create(Map.of(
                controllerPos, controllerBlock,
                controllerPos.offset(1, 0, 0), first,
                controllerPos.offset(2, 0, 0), second), List.of(controller));
        setField(BlockEntity.class, controller, "level", level);
        return controller;
    }

    private static MachineControllerBlockEntity allocateController() throws Exception {
        Field unsafeField = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
        unsafeField.setAccessible(true);
        return (MachineControllerBlockEntity) ((sun.misc.Unsafe) unsafeField.get(null)).allocateInstance(MachineControllerBlockEntity.class);
    }

    private static cn.howxu.mmcr.internal.block.MachineControllerBlock testControllerBlock() throws Exception {
        Field unsafeField = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
        unsafeField.setAccessible(true);
        var block = (cn.howxu.mmcr.internal.block.MachineControllerBlock) ((sun.misc.Unsafe) unsafeField.get(null))
                .allocateInstance(cn.howxu.mmcr.internal.block.MachineControllerBlock.class);
        setField(cn.howxu.mmcr.internal.block.MachineControllerBlock.class, block, "machineId", MACHINE_ID);
        setField(net.minecraft.world.level.block.state.BlockBehaviour.class, block, "properties", Blocks.IRON_BLOCK.properties());
        var builder = new net.minecraft.world.level.block.state.StateDefinition.Builder<Block, net.minecraft.world.level.block.state.BlockState>(block);
        builder.add(cn.howxu.mmcr.internal.block.MachineControllerBlock.FACING,
                cn.howxu.mmcr.internal.block.MachineControllerBlock.ROLL_FACING,
                cn.howxu.mmcr.internal.block.MachineControllerBlock.FORMED,
                cn.howxu.mmcr.internal.block.MachineControllerBlock.ACTIVE);
        var stateDefinition = builder.create(Block::defaultBlockState, net.minecraft.world.level.block.state.BlockState::new);
        setField(Block.class, block, "stateDefinition", stateDefinition);
        setField(Block.class, block, "defaultBlockState", stateDefinition.any()
                .setValue(cn.howxu.mmcr.internal.block.MachineControllerBlock.FACING, Direction.NORTH)
                .setValue(cn.howxu.mmcr.internal.block.MachineControllerBlock.ROLL_FACING, Direction.NORTH)
                .setValue(cn.howxu.mmcr.internal.block.MachineControllerBlock.FORMED, false)
                .setValue(cn.howxu.mmcr.internal.block.MachineControllerBlock.ACTIVE, false));
        return block;
    }

    private static boolean tryForm(MachineControllerBlockEntity controller) throws Exception {
        Method method = MachineControllerBlockEntity.class.getDeclaredMethod("tryFormMachine", cn.howxu.mmcr.api.machine.Machine.class, Direction.class);
        method.setAccessible(true);
        return (boolean) method.invoke(controller, MachineRegistry.getMachine(MACHINE_ID), Direction.SOUTH);
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

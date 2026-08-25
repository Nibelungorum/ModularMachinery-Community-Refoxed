package cn.howxu.mmcr.test;

import cn.howxu.mmcr.LevelStub;
import cn.howxu.mmcr.api.machine.BlockArray;
import cn.howxu.mmcr.api.machine.DynamicMachine;
import cn.howxu.mmcr.api.machine.Machine;
import cn.howxu.mmcr.api.recipe.MachineComponent;
import cn.howxu.mmcr.api.recipe.helper.ProcessingComponent;
import cn.howxu.mmcr.internal.tile.EnergyInputHatchBlockEntity;
import cn.howxu.mmcr.internal.tile.EnergyOutputHatchBlockEntity;
import cn.howxu.mmcr.internal.tile.FluidInputHatchBlockEntity;
import cn.howxu.mmcr.internal.tile.FluidOutputHatchBlockEntity;
import cn.howxu.mmcr.internal.tile.IOPortBlockEntity;
import cn.howxu.mmcr.internal.tile.ItemInputBusBlockEntity;
import cn.howxu.mmcr.internal.tile.ItemOutputBusBlockEntity;
import cn.howxu.mmcr.internal.tile.MachineControllerBlockEntity;
import cn.howxu.mmcr.registry.ModBlockEntities;
import cn.howxu.mmcr.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.Level;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Creates real controller and capability owners for final runtime tests.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class RuntimeTestFixtures {
    private RuntimeTestFixtures() {
    }

    public static MachineControllerBlockEntity controller(Identifier machineId, IOPortBlockEntity... ports) {
        MachineControllerBlockEntity controller = controllerEntity(machineId, BlockPos.ZERO);
        BlockPos controllerPos = controller.getBlockPos();

        Map<BlockPos, net.minecraft.world.level.block.Block> blocks = new HashMap<>();
        blocks.put(controllerPos, ModBlocks.controllerFor(machineId).get());
        List<BlockEntity> entities = new ArrayList<>(List.of(controller));
        for (IOPortBlockEntity port : ports) {
            blocks.put(port.getBlockPos(), ModBlocks.BLOCKS.get(port.kind().id()).get());
            entities.add(port);
        }

        var level = LevelStub.create(blocks, entities);
        controller.setLevel(level);
        for (IOPortBlockEntity port : ports) port.setLevel(level);

        List<ProcessingComponent> components = new ArrayList<>();
        for (IOPortBlockEntity port : ports) {
            components.add(new ProcessingComponent(new MachineComponent(port.kind(), port.ioType()), port,
                    port.getBlockPos(), port.getBlockPos(), (String) null));
        }
        controller.componentRuntime().replaceComponents(components);
        controller.setMachine(new DynamicMachine(machineId, "runtime test", new BlockArray(Map.of())));
        controller.refreshModuleConnectionState();
        return controller;
    }

    public static MachineControllerBlockEntity controllerEntity(Identifier machineId, BlockPos pos) {
        return controllerEntity(machineId, pos, ModBlocks.controllerFor(machineId).get().defaultBlockState());
    }

    public static MachineControllerBlockEntity controllerEntity(Identifier machineId, BlockPos pos, BlockState state) {
        BlockEntity entity = ModBlockEntities.controllerFor(machineId).get().create(pos, state);
        if (!(entity instanceof MachineControllerBlockEntity controller)) {
            throw new AssertionError("Expected a machine controller block entity");
        }
        return controller;
    }

    public static void attachLevel(MachineControllerBlockEntity controller, Level level) {
        controller.setLevel(level);
    }

    public static void publishStructure(MachineControllerBlockEntity controller, Machine machine, boolean formed) {
        controller.setMachine(machine);
        controller.setFormed(formed);
    }

    public static void republish(MachineControllerBlockEntity controller) {
        controller.setFormed(controller.structureSnapshot().formed());
    }

    public static void formStructure(MachineControllerBlockEntity controller, Machine machine) {
        formStructure(controller, machine, 1);
    }

    public static void formStructure(MachineControllerBlockEntity controller, Machine machine, int stageNumber) {
        controller.setMachine(machine);
        TestServerLevel level = newTestServerLevel(controller, machine, stageNumber);
        controller.setLevel(level);
        for (int tick = 0; tick < 32 && !controller.structureSnapshot().formed(); tick++) {
            controller.tickStructure(level, controller.getBlockPos());
        }
        if (!controller.structureSnapshot().formed()) {
            throw new AssertionError("Unable to form test structure: " + controller.structureSnapshot());
        }
    }

    private static TestServerLevel newTestServerLevel(MachineControllerBlockEntity controller, Machine machine,
                                                      int stageNumber) {
        try {
            TestServerLevel level = (TestServerLevel) unsafe().allocateInstance(TestServerLevel.class);
            setField(TestServerLevel.class, level, "blockEntities", Map.of(controller.getBlockPos(), controller));
            Map<BlockPos, BlockState> blocks = new HashMap<>();
            blocks.put(controller.getBlockPos(), controller.getBlockState());
            controller.assemblyPattern(machine, stageNumber).pattern().forEach((relative, predicate) ->
                    predicate.preferredState().ifPresent(state -> blocks.put(controller.getBlockPos().offset(relative), state)));
            setField(TestServerLevel.class, level, "blocks", blocks);
            setField(ServerLevel.class, level, "players", List.of());
            setField(Level.class, level, "dimension", Level.OVERWORLD);
            return level;
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError("Unable to create server level fixture", exception);
        }
    }

    private static sun.misc.Unsafe unsafe() throws ReflectiveOperationException {
        Field unsafeField = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
        unsafeField.setAccessible(true);
        return (sun.misc.Unsafe) unsafeField.get(null);
    }

    private static void setField(Class<?> declaringClass, Object target, String name, Object value)
            throws ReflectiveOperationException {
        Field field = declaringClass.getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    public static ItemInputBusBlockEntity itemInput(BlockPos pos) {
        return create("item_input_bus", pos, ItemInputBusBlockEntity.class);
    }

    public static ItemOutputBusBlockEntity itemOutput(BlockPos pos) {
        return create("item_output_bus", pos, ItemOutputBusBlockEntity.class);
    }

    public static FluidInputHatchBlockEntity fluidInput(BlockPos pos) {
        return create("fluid_input_hatch", pos, FluidInputHatchBlockEntity.class);
    }

    public static FluidOutputHatchBlockEntity fluidOutput(BlockPos pos) {
        return create("fluid_output_hatch", pos, FluidOutputHatchBlockEntity.class);
    }

    public static EnergyInputHatchBlockEntity energyInput(BlockPos pos) {
        return create("energy_input_hatch", pos, EnergyInputHatchBlockEntity.class);
    }

    public static EnergyOutputHatchBlockEntity energyOutput(BlockPos pos) {
        return create("energy_output_hatch", pos, EnergyOutputHatchBlockEntity.class);
    }

    private static <T extends IOPortBlockEntity> T create(String kind, BlockPos pos, Class<T> type) {
        BlockEntity entity = ModBlockEntities.BES.get(kind).get().create(pos, ModBlocks.BLOCKS.get(kind).get().defaultBlockState());
        if (!type.isInstance(entity)) throw new AssertionError("Expected " + type.getSimpleName());
        return type.cast(entity);
    }

    private static final class TestServerLevel extends ServerLevel {
        private Map<BlockPos, BlockEntity> blockEntities;
        private Map<BlockPos, BlockState> blocks;

        private TestServerLevel() {
            super(null, null, null, null, Level.OVERWORLD, null, false, 0L, List.of(), false);
        }

        @Override public BlockState getBlockState(BlockPos pos) {
            return blocks.getOrDefault(pos, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
        }

        @Override public BlockEntity getBlockEntity(BlockPos pos) {
            return blockEntities.get(pos);
        }

        @Override public boolean setBlock(BlockPos pos, BlockState state, int flags) {
            blocks.put(pos, state);
            return true;
        }

        @Override public boolean hasChunk(int chunkX, int chunkZ) {
            return true;
        }

        @Override public long getGameTime() {
            return 1L;
        }

        @Override public void sendBlockUpdated(BlockPos pos, BlockState oldState, BlockState newState, int flags) {
        }

        @Override public void blockEntityChanged(BlockPos pos) {
        }
    }
}

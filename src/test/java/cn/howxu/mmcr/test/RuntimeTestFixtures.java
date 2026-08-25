package cn.howxu.mmcr.test;

import cn.howxu.mmcr.LevelStub;
import cn.howxu.mmcr.api.machine.BlockArray;
import cn.howxu.mmcr.api.machine.CompiledMachinePattern;
import cn.howxu.mmcr.api.machine.DynamicMachine;
import cn.howxu.mmcr.api.machine.Machine;
import cn.howxu.mmcr.api.machine.MachinePatternCompiler;
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
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.Level;
import net.minecraft.core.Direction;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
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
        BlockEntity entity = ModBlockEntities.controllerFor(machineId).get().create(
                pos, ModBlocks.controllerFor(machineId).get().defaultBlockState());
        if (!(entity instanceof MachineControllerBlockEntity controller)) {
            throw new AssertionError("Expected a machine controller block entity");
        }
        return controller;
    }

    public static void attachLevel(MachineControllerBlockEntity controller, Level level) {
        controller.setLevel(level);
    }

    public static void publishStructure(MachineControllerBlockEntity controller, Machine machine,
                                        boolean formed, int stage, Direction facing, Direction rollFacing) {
        controller.setMachine(machine);
        try {
            Object runtime = field(MachineControllerBlockEntity.class, controller, "runtime");
            Object structure = field(runtime.getClass(), runtime, "structure");
            setField(structure.getClass(), structure, "foundMachine", formed ? machine : null);
            CompiledMachinePattern compiled = formed ? MachinePatternCompiler.compileStages(machine, new java.util.HashMap<>()).stream()
                    .filter(pattern -> pattern.stageNumber() == stage).findFirst().orElseThrow() : null;
            setField(structure.getClass(), structure, "foundPattern", compiled == null ? null : compiled.rotatedPattern(facing));
            setField(structure.getClass(), structure, "foundCompiledPattern", compiled);
            setField(structure.getClass(), structure, "controllerFacing", formed ? facing : null);
            setField(structure.getClass(), structure, "matchedRollFacing", rollFacing);
            setField(structure.getClass(), structure, "matchedStructureStage", formed ? stage : 0);
            setField(structure.getClass(), structure, "formed", formed);
            setField(structure.getClass(), structure, "dirty", false);
            setField(BlockEntity.class, controller, "blockState",
                    controller.getBlockState().setValue(cn.howxu.mmcr.internal.block.MachineControllerBlock.FORMED, formed));
            Method publishSnapshot = runtime.getClass().getDeclaredMethod("publishSnapshot");
            publishSnapshot.setAccessible(true);
            publishSnapshot.invoke(runtime);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError("Unable to publish test structure snapshot", exception);
        }
    }

    public static void republish(MachineControllerBlockEntity controller) {
        try {
            Object runtime = field(MachineControllerBlockEntity.class, controller, "runtime");
            Method publishSnapshot = runtime.getClass().getDeclaredMethod("publishSnapshot");
            publishSnapshot.setAccessible(true);
            publishSnapshot.invoke(runtime);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError("Unable to republish test runtime snapshot", exception);
        }
    }

    private static Object field(Class<?> type, Object target, String name) throws ReflectiveOperationException {
        Field field = type.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }

    private static void setField(Class<?> type, Object target, String name, Object value)
            throws ReflectiveOperationException {
        Field field = type.getDeclaredField(name);
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
}

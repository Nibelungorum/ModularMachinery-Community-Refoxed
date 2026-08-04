package cn.howxu.mmcr.registry;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.machine.Machine;
import cn.howxu.mmcr.api.machine.MachineControllerSpec;
import cn.howxu.mmcr.api.machine.MachineDefinitions;
import cn.howxu.mmcr.internal.block.IOPortBlock;
import cn.howxu.mmcr.internal.block.MachineCasingBlock;
import cn.howxu.mmcr.internal.block.MachineControllerBlock;
import cn.howxu.mmcr.internal.port.IOPortKind;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.LinkedHashMap;
import java.util.function.Supplier;

public final class ModBlocks {

    public static final DeferredRegister.Blocks REGISTER = DeferredRegister.createBlocks(MMCR.MODID);

    public static final LinkedHashMap<String, DeferredHolder<Block, Block>> BLOCKS = new LinkedHashMap<>();

    static {
        MachineDefinitions.all().forEach(machine -> registerMachineController(machine.registryName()));
        BLOCKS.put("basic_casing", REGISTER.registerBlock("basic_casing", MachineCasingBlock::new));
        PortKinds.all().forEach(ModBlocks::registerIoPort);
    }

    public static final DeferredHolder<Block, Block> BLAST_FURNACE_CONTROLLER = controllerFor(MMCR.id("blast_furnace"));
    public static final DeferredHolder<Block, Block> CONTROLLER = BLAST_FURNACE_CONTROLLER;
    public static final DeferredHolder<Block, Block> BASIC_CASING = BLOCKS.get("basic_casing");

    /** Compatibility alias for {@link #BASIC_CASING}; the block id was renamed from {@code casing} to {@code basic_casing}. */
    public static final DeferredHolder<Block, Block> CASING = BASIC_CASING;

    private static void registerMachineController(Identifier machineId) {
        String name = MachineControllerSpec.defaultsFor(machineId).id().getPath();
        BLOCKS.put(name, REGISTER.registerBlock(name,
                properties -> new MachineControllerBlock(machineId, properties)));
    }

    public static DeferredHolder<Block, Block> controllerFor(Machine machine) {
        return controllerFor(machine.registryName());
    }

    public static DeferredHolder<Block, Block> controllerFor(Identifier machineId) {
        String name = MachineControllerSpec.defaultsFor(machineId).id().getPath();
        DeferredHolder<Block, Block> holder = BLOCKS.get(name);
        if (holder == null) throw new IllegalArgumentException("No controller registered for machine: " + machineId);
        return holder;
    }

    public static Identifier machineIdForController(Block block) {
        if (block instanceof MachineControllerBlock controller) {
            return controller.machineId();
        }
        return null;
    }

    private static void registerIoPort(IOPortKind kind) {
        String name = kind.id();
        Supplier<? extends BlockEntityType<?>> beTypeSupplier =
                () -> ModBlockEntities.BES.get(name).get();
        BLOCKS.put(name, REGISTER.registerBlock(name,
                properties -> new IOPortBlock(kind, beTypeSupplier, properties)));
    }

    public static void register(IEventBus bus) {
        REGISTER.register(bus);
    }

    private ModBlocks() {}
}

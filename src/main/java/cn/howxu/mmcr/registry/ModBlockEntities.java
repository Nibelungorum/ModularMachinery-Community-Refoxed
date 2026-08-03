package cn.howxu.mmcr.registry;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.machine.MachineControllerSpec;
import cn.howxu.mmcr.api.machine.MachineDefinitions;
import cn.howxu.mmcr.internal.tile.MachineControllerBlockEntity;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.LinkedHashMap;
import java.util.function.Supplier;

public final class ModBlockEntities {

    public static final DeferredRegister<BlockEntityType<?>> REGISTER =
            DeferredRegister.create(BuiltInRegistries.BLOCK_ENTITY_TYPE, MMCR.MODID);

    public static final LinkedHashMap<String, DeferredHolder<BlockEntityType<?>, BlockEntityType<?>>> BES =
            new LinkedHashMap<>();

    static {
        MachineDefinitions.all().forEach(machine -> registerMachineController(machine.registryName()));
        PortKinds.all().forEach(kind -> {
            String name = "io_port_" + kind.id() + "_basic";
            BES.put(name, register(name, () -> new BlockEntityType<>(
                    (BlockEntityType.BlockEntitySupplier) kind.entityFactory(),
                    ModBlocks.BLOCKS.get(name).get())));
        });
    }

    private static void registerMachineController(Identifier machineId) {
        String name = MachineControllerSpec.defaultsFor(machineId).id().getPath();
        BES.put(name, register(name, () -> new BlockEntityType<>(
                MachineControllerBlockEntity::new, ModBlocks.controllerFor(machineId).get())));
    }

    public static DeferredHolder<BlockEntityType<?>, BlockEntityType<?>> controllerFor(Identifier machineId) {
        String name = MachineControllerSpec.defaultsFor(machineId).id().getPath();
        DeferredHolder<BlockEntityType<?>, BlockEntityType<?>> holder = BES.get(name);
        if (holder == null) throw new IllegalArgumentException("No controller block entity registered for machine: " + machineId);
        return holder;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static DeferredHolder<BlockEntityType<?>, BlockEntityType<?>> register(
            String name, Supplier<BlockEntityType<?>> supplier) {
        return (DeferredHolder<BlockEntityType<?>, BlockEntityType<?>>) (DeferredHolder<?, ?>)
                REGISTER.register(name, supplier);
    }

    public static void register(IEventBus bus) {
        REGISTER.register(bus);
    }

    private ModBlockEntities() {}
}

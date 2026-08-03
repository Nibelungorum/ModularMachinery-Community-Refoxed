package cn.howxu.mmcr.registry;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.internal.tile.MachineControllerBlockEntity;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.LinkedHashMap;
import java.util.function.Supplier;

public final class MMCRBlockEntities {

    public static final DeferredRegister<BlockEntityType<?>> REGISTER =
            DeferredRegister.create(BuiltInRegistries.BLOCK_ENTITY_TYPE, MMCR.MODID);

    public static final LinkedHashMap<String, DeferredHolder<BlockEntityType<?>, BlockEntityType<?>>> BES =
            new LinkedHashMap<>();

    static {
        BES.put("controller", register("controller", () -> new BlockEntityType<>(
                MachineControllerBlockEntity::new, MMCRBlocks.CONTROLLER.get())));
        MMCRPortKinds.all().forEach(kind -> {
            String name = "io_port_" + kind.id() + "_basic";
            BES.put(name, register(name, () -> new BlockEntityType<>(
                    (BlockEntityType.BlockEntitySupplier) kind.entityFactory(),
                    MMCRBlocks.BLOCKS.get(name).get())));
        });
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

    private MMCRBlockEntities() {}
}

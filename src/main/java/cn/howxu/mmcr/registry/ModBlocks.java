package cn.howxu.mmcr.registry;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.internal.block.IOPortBlock;
import cn.howxu.mmcr.internal.block.MachineCasingBlock;
import cn.howxu.mmcr.internal.block.MachineControllerBlock;
import cn.howxu.mmcr.internal.port.IOPortKind;
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
        BLOCKS.put("controller", REGISTER.registerBlock("controller", MachineControllerBlock::new));
        BLOCKS.put("casing", REGISTER.registerBlock("casing", MachineCasingBlock::new));
        PortKinds.all().forEach(ModBlocks::registerIoPort);
    }

    public static final DeferredHolder<Block, Block> CONTROLLER = BLOCKS.get("controller");
    public static final DeferredHolder<Block, Block> CASING = BLOCKS.get("casing");

    private static void registerIoPort(IOPortKind kind) {
        String name = "io_port_" + kind.id() + "_basic";
        Supplier<? extends BlockEntityType<?>> beTypeSupplier =
                () -> ModBlockEntities.BES.get(name).get();
        BLOCKS.put(name, REGISTER.registerBlock(name,
                properties -> new IOPortBlock(kind.id(), beTypeSupplier, properties)));
    }

    public static void register(IEventBus bus) {
        REGISTER.register(bus);
    }

    private ModBlocks() {}
}

package cn.howxu.mmcr.registry;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.machine.MachineControllerSpec;
import cn.howxu.mmcr.api.recipe.ParallelTier;
import cn.howxu.mmcr.internal.block.FactorySchedulerBlock;
import cn.howxu.mmcr.internal.block.IOPortBlock;
import cn.howxu.mmcr.internal.block.MachineCasingBlock;
import cn.howxu.mmcr.internal.block.MachineControllerBlock;
import cn.howxu.mmcr.internal.block.ModuleCouplerBlock;
import cn.howxu.mmcr.internal.block.ParallelControllerBlock;
import cn.howxu.mmcr.internal.block.SmartInterfaceBlock;
import cn.howxu.mmcr.internal.block.DataStorageBlock;
import cn.howxu.mmcr.internal.block.UpgradeBusBlock;
import cn.howxu.mmcr.internal.port.IOPortKind;
import cn.howxu.mmcr.internal.port.UpgradeBusSize;
import cn.howxu.mmcr.api.port.PortDefinitionRegistry;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.LinkedHashMap;
import java.util.Collection;
import java.util.function.Supplier;

public final class ModBlocks {

    public static final DeferredRegister.Blocks REGISTER = DeferredRegister.createBlocks(MMCR.MODID);

    public static final LinkedHashMap<String, DeferredHolder<Block, Block>> BLOCKS = new LinkedHashMap<>();

    static {
        BLOCKS.put("basic_casing", REGISTER.registerBlock("basic_casing", MachineCasingBlock::new));
        PortKinds.all().forEach(ModBlocks::registerIoPort);
        PortDefinitionRegistry.freeze();
        for (ParallelTier tier : ParallelTier.values()) registerParallelController(tier);
        registerFactoryController();
        registerSmartInterface();
        registerDataStorage();
        registerModuleCoupler();
        for (UpgradeBusSize size : UpgradeBusSize.values()) registerUpgradeBus(size);
    }

    public static final DeferredHolder<Block, Block> BASIC_CASING = BLOCKS.get("basic_casing");
    public static final DeferredHolder<Block, Block> SMART_INTERFACE = BLOCKS.get("smart_interface");
    public static final DeferredHolder<Block, Block> DATA_STORAGE = BLOCKS.get("data_storage");
    public static final DeferredHolder<Block, Block> MODULE_BRIDGE = BLOCKS.get("module_bridge");

    /** Compatibility alias for {@link #BASIC_CASING}; the block id was renamed from {@code casing} to {@code basic_casing}. */
    public static final DeferredHolder<Block, Block> CASING = BASIC_CASING;

    private static void registerMachineController(Identifier machineId) {
        String name = MachineControllerSpec.defaultsFor(machineId).id().getPath();
        if (BLOCKS.containsKey(name)) return;
        BLOCKS.put(name, REGISTER.registerBlock(name,
                properties -> new MachineControllerBlock(machineId, properties)));
    }

    public static DeferredHolder<Block, Block> controllerFor(Identifier machineId) {
        String name = MachineControllerSpec.defaultsFor(machineId).id().getPath();
        DeferredHolder<Block, Block> holder = BLOCKS.get(name);
        if (holder == null) throw new IllegalArgumentException("No controller registered for machine: " + machineId);
        return holder;
    }

    public static void registerMachineControllers(Collection<Identifier> machineIds) {
        machineIds.forEach(ModBlocks::registerMachineController);
    }

    public static boolean hasControllerFor(Identifier machineId) {
        String name = MachineControllerSpec.defaultsFor(machineId).id().getPath();
        DeferredHolder<Block, Block> holder = BLOCKS.get(name);
        return holder != null && holder.isBound()
                && holder.get() instanceof MachineControllerBlock controller
                && controller.machineId().equals(machineId);
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

    private static void registerParallelController(ParallelTier tier) {
        String name = tier.idSuffix();
        Supplier<? extends BlockEntityType<?>> beTypeSupplier =
                () -> ModBlockEntities.BES.get(name).get();
        BLOCKS.put(name, REGISTER.registerBlock(name,
                properties -> new ParallelControllerBlock(tier, beTypeSupplier, properties)));
    }

    private static void registerFactoryController() {
        String name = "factory_controller";
        Supplier<? extends BlockEntityType<?>> beTypeSupplier =
                () -> ModBlockEntities.BES.get(name).get();
        BLOCKS.put(name, REGISTER.registerBlock(name,
                properties -> new FactorySchedulerBlock(beTypeSupplier, properties)));
    }

    private static void registerSmartInterface() {
        String name = "smart_interface";
        Supplier<? extends BlockEntityType<?>> beTypeSupplier = () -> ModBlockEntities.SMART_INTERFACE.get();
        BLOCKS.put(name, REGISTER.registerBlock(name,
                properties -> new SmartInterfaceBlock(beTypeSupplier, properties)));
    }

    private static void registerDataStorage() {
        String name = "data_storage";
        Supplier<? extends BlockEntityType<?>> beTypeSupplier = () -> ModBlockEntities.DATA_STORAGE.get();
        BLOCKS.put(name, REGISTER.registerBlock(name,
                properties -> new DataStorageBlock(beTypeSupplier, properties)));
    }

    private static void registerModuleCoupler() {
        String name = "module_bridge";
        Supplier<? extends BlockEntityType<?>> beTypeSupplier = () -> ModBlockEntities.MODULE_BRIDGE.get();
        BLOCKS.put(name, REGISTER.registerBlock(name,
                properties -> new ModuleCouplerBlock(beTypeSupplier, properties)));
    }

    private static void registerUpgradeBus(UpgradeBusSize size) {
        String name = "upgrade_bus_" + size.id();
        Supplier<? extends BlockEntityType<?>> beTypeSupplier = () -> ModBlockEntities.BES.get(name).get();
        BLOCKS.put(name, REGISTER.registerBlock(name,
                properties -> new UpgradeBusBlock(size, beTypeSupplier, properties)));
    }

    public static void register(IEventBus bus) {
        REGISTER.register(bus);
    }

    private ModBlocks() {}
}

package cn.howxu.mmcr.registry;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.machine.MachineControllerSpec;
import cn.howxu.mmcr.api.recipe.ParallelTier;
import cn.howxu.mmcr.internal.tile.FactorySchedulerBlockEntity;
import cn.howxu.mmcr.internal.tile.MachineControllerBlockEntity;
import cn.howxu.mmcr.internal.tile.ModuleCouplerBlockEntity;
import cn.howxu.mmcr.internal.tile.ParallelControllerBlockEntity;
import cn.howxu.mmcr.internal.tile.SmartInterfaceBlockEntity;
import cn.howxu.mmcr.internal.tile.DataStorageBlockEntity;
import cn.howxu.mmcr.internal.tile.UpgradeBusBlockEntity;
import cn.howxu.mmcr.internal.port.UpgradeBusSize;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.LinkedHashMap;
import java.util.Collection;
import java.util.function.Supplier;

public final class ModBlockEntities {

    public static final DeferredRegister<BlockEntityType<?>> REGISTER =
            DeferredRegister.create(BuiltInRegistries.BLOCK_ENTITY_TYPE, MMCR.MODID);

    public static final LinkedHashMap<String, DeferredHolder<BlockEntityType<?>, BlockEntityType<?>>> BES =
            new LinkedHashMap<>();

    static {
        PortKinds.all().forEach(kind -> {
            String name = kind.id();
            BES.put(name, register(name, () -> new BlockEntityType<>(
                    (BlockEntityType.BlockEntitySupplier) kind.entityFactory(),
                    ModBlocks.BLOCKS.get(name).get())));
        });
        for (ParallelTier tier : ParallelTier.values()) registerParallelController(tier);
        registerFactoryController();
        registerSmartInterface();
        registerDataStorage();
        registerModuleCoupler();
        for (UpgradeBusSize size : UpgradeBusSize.values()) registerUpgradeBus(size);
    }

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<?>> SMART_INTERFACE = BES.get("smart_interface");
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<?>> DATA_STORAGE = BES.get("data_storage");
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<?>> MODULE_BRIDGE = BES.get("module_bridge");

    private static void registerMachineController(Identifier machineId) {
        String name = MachineControllerSpec.defaultsFor(machineId).id().getPath();
        if (BES.containsKey(name)) return;
        BES.put(name, register(name, () -> new BlockEntityType<>(
                MachineControllerBlockEntity::new, ModBlocks.controllerFor(machineId).get())));
    }

    public static void registerMachineControllers(Collection<Identifier> machineIds) {
        machineIds.forEach(ModBlockEntities::registerMachineController);
    }

    private static void registerParallelController(ParallelTier tier) {
        String name = tier.idSuffix();
        BES.put(name, register(name, () -> new BlockEntityType<>(
                (pos, state) -> new ParallelControllerBlockEntity(tier, pos, state),
                ModBlocks.BLOCKS.get(name).get())));
    }

    private static void registerFactoryController() {
        String name = "factory_controller";
        BES.put(name, register(name, () -> new BlockEntityType<>(
                FactorySchedulerBlockEntity::new,
                ModBlocks.BLOCKS.get(name).get())));
    }

    private static void registerSmartInterface() {
        BES.put("smart_interface", register("smart_interface", () -> new BlockEntityType<>(
                SmartInterfaceBlockEntity::new, ModBlocks.SMART_INTERFACE.get())));
    }

    private static void registerDataStorage() {
        BES.put("data_storage", register("data_storage", () -> new BlockEntityType<>(
                DataStorageBlockEntity::new, ModBlocks.DATA_STORAGE.get())));
    }

    private static void registerModuleCoupler() {
        BES.put("module_bridge", register("module_bridge", () -> new BlockEntityType<>(
                ModuleCouplerBlockEntity::new, ModBlocks.MODULE_BRIDGE.get())));
    }

    private static void registerUpgradeBus(UpgradeBusSize size) {
        String name = "upgrade_bus_" + size.id();
        BES.put(name, register(name, () -> new BlockEntityType<>(
                (pos, state) -> new UpgradeBusBlockEntity(size, pos, state),
                ModBlocks.BLOCKS.get(name).get())));
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

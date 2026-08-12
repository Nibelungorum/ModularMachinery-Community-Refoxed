package cn.howxu.mmcr.registry;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.machine.MachineControllerSpec;
import cn.howxu.mmcr.api.machine.MachineDefinitions;
import cn.howxu.mmcr.api.recipe.ParallelTier;
import cn.howxu.mmcr.internal.tile.DebugInfiniteEnergySourceBlockEntity;
import cn.howxu.mmcr.internal.tile.DebugInfiniteFluidSourceBlockEntity;
import cn.howxu.mmcr.internal.tile.FactorySchedulerBlockEntity;
import cn.howxu.mmcr.internal.tile.MachineControllerBlockEntity;
import cn.howxu.mmcr.internal.tile.ParallelControllerBlockEntity;
import cn.howxu.mmcr.internal.tile.SmartInterfaceBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Supplier;

public final class ModBlockEntities {

    public static final DeferredRegister<BlockEntityType<?>> REGISTER =
            DeferredRegister.create(BuiltInRegistries.BLOCK_ENTITY_TYPE, MMCR.MODID);

    public static final LinkedHashMap<String, DeferredHolder<BlockEntityType<?>, BlockEntityType<?>>> BES =
            new LinkedHashMap<>();

    static {
        MachineDefinitions.allRegistrations().forEach(registration -> registerMachineController(registration.id()));
        PortKinds.all().forEach(kind -> {
            String name = kind.id();
            BES.put(name, register(name, () -> new BlockEntityType<>(
                    (BlockEntityType.BlockEntitySupplier) kind.entityFactory(),
                    ModBlocks.BLOCKS.get(name).get())));
        });
        for (ParallelTier tier : ParallelTier.values()) registerParallelController(tier);
        registerFactoryController();
        registerSmartInterface();
        registerDebugBe("debug_infinite_energy_source", (pos, state) ->
                new DebugInfiniteEnergySourceBlockEntity(pos, state));
        Map<Fluid, String> debugFluidBe = Map.of(
                Fluids.WATER, "debug_infinite_water_source",
                Fluids.LAVA,  "debug_infinite_lava_source");
        debugFluidBe.forEach((fluid, name) -> registerDebugBe(name, (pos, state) ->
                new DebugInfiniteFluidSourceBlockEntity(pos, state, fluid)));
    }

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<?>> SMART_INTERFACE = BES.get("smart_interface");

    private static void registerMachineController(Identifier machineId) {
        String name = MachineControllerSpec.defaultsFor(machineId).id().getPath();
        BES.put(name, register(name, () -> new BlockEntityType<>(
                MachineControllerBlockEntity::new, ModBlocks.controllerFor(machineId).get())));
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

    private static void registerDebugBe(String name,
                                         BiFunction<BlockPos, BlockState, ? extends BlockEntity> factory) {
        BES.put(name, register(name, () -> new BlockEntityType<>(
                (BlockEntityType.BlockEntitySupplier) factory::apply,
                ModBlocks.BLOCKS.get(name).get())));
    }

    public static void register(IEventBus bus) {
        REGISTER.register(bus);
    }

    private ModBlockEntities() {}
}

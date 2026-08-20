package cn.howxu.mmcr.test;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.machine.MachineControllerSpec;
import cn.howxu.mmcr.api.machine.MachineDefinitions;
import cn.howxu.mmcr.api.machine.MachineRegistration;
import cn.howxu.mmcr.api.machine.MachineRegistry;
import cn.howxu.mmcr.api.machine.level.MachineLevelRegistry;
import cn.howxu.mmcr.api.machine.level.MachineLevelRegistryBridge;
import cn.howxu.mmcr.api.publicapi.event.RegisterMachineStructuresEvent;
import cn.howxu.mmcr.api.recipe.ParallelTier;
import cn.howxu.mmcr.internal.block.FactorySchedulerBlock;
import cn.howxu.mmcr.internal.block.IOPortBlock;
import cn.howxu.mmcr.internal.block.MachineControllerBlock;
import cn.howxu.mmcr.internal.block.ModuleCouplerBlock;
import cn.howxu.mmcr.internal.block.ParallelControllerBlock;
import cn.howxu.mmcr.internal.block.SmartInterfaceBlock;
import cn.howxu.mmcr.internal.reload.DynamicContentReloadService;
import cn.howxu.mmcr.internal.api.PublicBuiltinRuntime;
import cn.howxu.mmcr.internal.api.PublicApiBootstrap;
import cn.howxu.mmcr.api.publicapi.event.RegisterMachineDefinationsEvent;
import cn.howxu.mmcr.api.publicapi.event.RegisterMachineStructuresEvent;
import cn.howxu.mmcr.internal.tile.FactorySchedulerBlockEntity;
import cn.howxu.mmcr.internal.tile.ModuleCouplerBlockEntity;
import cn.howxu.mmcr.internal.tile.ParallelControllerBlockEntity;
import cn.howxu.mmcr.internal.tile.SmartInterfaceBlockEntity;
import cn.howxu.mmcr.registry.ModBlocks;
import cn.howxu.mmcr.registry.ModBlockEntities;
import cn.howxu.mmcr.registry.ModItems;
import cn.howxu.mmcr.registry.PortKinds;

import net.minecraft.core.Holder;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.registries.DeferredHolder;
import org.nibelungorum.DefaultMachineLevels;
import org.nibelungorum.builtin.PublicBuiltinDefinitions;


import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import java.nio.file.Path;

public final class TestBootstrap {
    private static boolean initialized;

    private TestBootstrap() {
    }

    public static synchronized void bootstrap() throws Exception {
        if (initialized) {
            restoreMachineDefinitions();
            return;
        }

        Class<?> fmlLoaderCls = Class.forName("net.neoforged.fml.loading.FMLLoader");
        Class<?> distCls = Class.forName("net.neoforged.api.distmarker.Dist");
        Class<?> loadingModListCls = Class.forName("net.neoforged.fml.loading.LoadingModList");
        var fmlCtor = fmlLoaderCls.getDeclaredConstructor(
                ClassLoader.class, String[].class, distCls, boolean.class, Path.class);
        fmlCtor.setAccessible(true);
        Object fmlLoader = fmlCtor.newInstance(
                Thread.currentThread().getContextClassLoader(), new String[0],
                distCls.getField("CLIENT").get(null), false, Path.of("."));

        var lmlCtor = loadingModListCls.getDeclaredConstructor(
                List.class, List.class, List.class, List.class, Map.class);
        lmlCtor.setAccessible(true);
        Object emptyLoadingModList = lmlCtor.newInstance(
                List.of(), List.of(), List.of(), List.of(), Map.of());
        Field loadingModListField = fmlLoaderCls.getDeclaredField("loadingModList");
        loadingModListField.setAccessible(true);
        loadingModListField.set(fmlLoader, emptyLoadingModList);

        Class.forName("net.minecraft.SharedConstants").getMethod("tryDetectVersion").invoke(null);
        NeoForge.EVENT_BUS.start();
        MachineDefinitions.beginRegistryPhase();
        addTestMachineSuppliers();
        Bootstrap.bootStrap();
        MachineDefinitions.bootstrapBuiltins();
        registerPublicBuiltinEvents();
        bindAllVanillaItemComponents();
        bindController(id("test_cube"));
        bindController(id("controller_tick"));
        bindController(id("iron_compressor"));
        bindController(id("distillation_tower_test"));
        bindController(id("expandable_structure_stages"));
        bindController(id("expandable_structure_vertical_roll"));
        bind(ModBlocks.CASING, Blocks.STONE);
        bindPortBlocks();
        for (ParallelTier tier : ParallelTier.values()) bindParallelController(tier);
        bindFactoryController();
        bindSmartInterface();
        bindModuleBridge();
        bind(ModItems.THREAD_DISPERSER, registerItem(ModItems.THREAD_DISPERSER));
        registerDefaultMachineLevels();
        restoreMachineDefinitions();
        registerRuntimeBuiltins();
        initialized = true;
    }

    public static void restoreMachineDefinitions() {
        MachineDefinitions.clearForTesting();
        MachineDefinitions.beginRegistryPhase();
        addTestMachineSuppliers();
        MachineDefinitions.bootstrapBuiltins();
        registerPublicBuiltinEvents();
    }

    public static void registerRuntimeBuiltins() {
        restoreMachineDefinitions();
        registerDefaultMachineLevels();
        DynamicContentReloadService.reload(candidate -> {
            PublicBuiltinRuntime.registerStructures(candidate);
        });
        PublicBuiltinRuntime.registerRecipes();
        MachineRegistry.rebuildCompiledCache();
    }

    private static void registerDefaultMachineLevels() {
        if (MachineLevelRegistry.getType(DefaultMachineLevels.THERMAL_SMELTING_COIL_TYPE) != null) return;

        RegisterMachineStructuresEvent.resetCollector();
        RegisterMachineStructuresEvent event = RegisterMachineStructuresEvent.prepare(java.util.Set.of());
        DefaultMachineLevels.register(event);
        MachineLevelRegistryBridge.install(event.levelTypes().values(), event.levels().values());
    }

    private static void addTestMachineSuppliers() {
        MachineDefinitions.addBuiltinSupplier(() -> MachineRegistration.builder(id("test_cube")).localizedName("Test").build());
        MachineDefinitions.addBuiltinSupplier(() -> MachineRegistration.builder(id("controller_tick")).localizedName("Controller Tick").build());
        MachineDefinitions.addBuiltinSupplier(() -> MachineRegistration.builder(id("iron_compressor")).localizedName("Iron Compressor").build());
        MachineDefinitions.addBuiltinSupplier(() -> MachineRegistration.builder(id("distillation_tower_test"))
                .localizedName("Distillation Tower Test")
                .expandableStructure()
                .build());
        MachineDefinitions.addBuiltinSupplier(() -> MachineRegistration.builder(id("expandable_structure_stages"))
                .localizedName("Expandable Structure Stages")
                .expandableStructure()
                .build());
        MachineDefinitions.addBuiltinSupplier(() -> {
            Identifier machineId = id("expandable_structure_vertical_roll");
            MachineControllerSpec defaults = MachineControllerSpec.defaultsFor(machineId);
            return MachineRegistration.builder(machineId)
                    .localizedName("Expandable Structure Vertical Roll")
                    .controllerSpec(new MachineControllerSpec(defaults.id(), defaults.frontTexture(), defaults.sideTexture(),
                            defaults.topTexture(), defaults.bottomTexture(), true, false))
                    .expandableStructure()
                    .build();
        });
    }

    private static void registerPublicBuiltinEvents() {
        PublicApiBootstrap.clearForTesting();
        PublicApiBootstrap.begin();
        RegisterMachineDefinationsEvent definitions = new RegisterMachineDefinationsEvent();
        PublicBuiltinDefinitions.machineDefinitions().values().forEach(definitions::registerMachine);
        NeoForge.EVENT_BUS.post(definitions);
        definitions.freeze();
        PublicApiBootstrap.registerDefinitions(definitions);
        RegisterMachineStructuresEvent structures = new RegisterMachineStructuresEvent(definitions.definitions().keySet());
        PublicBuiltinDefinitions.structureDefinitions().values().forEach(structures::registerStructure);
        NeoForge.EVENT_BUS.post(structures);
        structures.freeze();
        PublicApiBootstrap.composeMachineRegistrations(definitions, structures);
        MachineDefinitions.validateRegistryPhase();
        MachineDefinitions.freezeRegistryPhase();
    }


    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MMCR.MODID, path);
    }

    private static void bindController(Identifier machineId) throws Exception {
        MachineControllerBlock block = controllerBlock(machineId);
        bind(ModBlocks.controllerFor(machineId), block);
        String itemName = MachineControllerSpec.defaultsFor(machineId).id().getPath();
        DeferredHolder<Item, Item> itemHolder = ModItems.ITEMS.get(itemName);
        Item item = registerItem(itemHolder);
        bind(itemHolder, item);
        Item.BY_BLOCK.put(block, item);
    }

    private static MachineControllerBlock controllerBlock(Identifier machineId) {
        MappedRegistry<Block> blocks = (MappedRegistry<Block>) BuiltInRegistries.BLOCK;
        blocks.unfreeze(true);
        MachineControllerBlock block = new MachineControllerBlock(machineId, Blocks.IRON_BLOCK.properties());
        Registry.register(BuiltInRegistries.BLOCK, MachineControllerSpec.defaultsFor(machineId).id(), block);
        blocks.freeze();
        return block;
    }

    private static void bindParallelController(ParallelTier tier) throws Exception {
        String name = tier.idSuffix();
        ParallelControllerBlock block = parallelControllerBlock(tier);
        bind(ModBlocks.BLOCKS.get(name), block);
        DeferredHolder<Item, Item> itemHolder = ModItems.ITEMS.get(name);
        Item item = registerItem(itemHolder);
        bind(itemHolder, item);
        Item.BY_BLOCK.put(block, item);
        bind(ModBlockEntities.BES.get(name), parallelControllerBlockEntityType(tier, block));
    }

    private static ParallelControllerBlock parallelControllerBlock(ParallelTier tier) {
        MappedRegistry<Block> blocks = (MappedRegistry<Block>) BuiltInRegistries.BLOCK;
        blocks.unfreeze(true);
        ParallelControllerBlock block = new ParallelControllerBlock(
                tier,
                () -> ModBlockEntities.BES.get(tier.idSuffix()).get(),
                Blocks.IRON_BLOCK.properties());
        Registry.register(BuiltInRegistries.BLOCK, MMCR.id(tier.idSuffix()), block);
        blocks.freeze();
        return block;
    }

    private static BlockEntityType<?> parallelControllerBlockEntityType(ParallelTier tier, ParallelControllerBlock block) {
        MappedRegistry<BlockEntityType<?>> blockEntities = (MappedRegistry<BlockEntityType<?>>) BuiltInRegistries.BLOCK_ENTITY_TYPE;
        blockEntities.unfreeze(true);
        BlockEntityType<?> beType = new BlockEntityType<>(
                (pos, state) -> new ParallelControllerBlockEntity(tier, pos, state),
                block);
        Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, MMCR.id(tier.idSuffix()), beType);
        blockEntities.freeze();
        return beType;
    }

    private static void bindFactoryController() throws Exception {
        String name = "factory_controller";
        FactorySchedulerBlock block = factoryControllerBlock();
        bind(ModBlocks.BLOCKS.get(name), block);
        DeferredHolder<Item, Item> itemHolder = ModItems.ITEMS.get(name);
        Item item = registerItem(itemHolder);
        bind(itemHolder, item);
        Item.BY_BLOCK.put(block, item);
        bind(ModBlockEntities.BES.get(name), factoryControllerBlockEntityType(block));
    }

    private static FactorySchedulerBlock factoryControllerBlock() {
        MappedRegistry<Block> blocks = (MappedRegistry<Block>) BuiltInRegistries.BLOCK;
        blocks.unfreeze(true);
        FactorySchedulerBlock block = new FactorySchedulerBlock(
                () -> ModBlockEntities.BES.get("factory_controller").get(),
                Blocks.IRON_BLOCK.properties());
        Registry.register(BuiltInRegistries.BLOCK, MMCR.id("factory_controller"), block);
        blocks.freeze();
        return block;
    }

    private static BlockEntityType<?> factoryControllerBlockEntityType(FactorySchedulerBlock block) {
        MappedRegistry<BlockEntityType<?>> blockEntities = (MappedRegistry<BlockEntityType<?>>) BuiltInRegistries.BLOCK_ENTITY_TYPE;
        blockEntities.unfreeze(true);
        BlockEntityType<?> beType = new BlockEntityType<>(FactorySchedulerBlockEntity::new, block);
        Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, MMCR.id("factory_controller"), beType);
        blockEntities.freeze();
        return beType;
    }

    private static void bindSmartInterface() throws Exception {
        MappedRegistry<Block> blocks = (MappedRegistry<Block>) BuiltInRegistries.BLOCK;
        MappedRegistry<BlockEntityType<?>> blockEntities = (MappedRegistry<BlockEntityType<?>>) BuiltInRegistries.BLOCK_ENTITY_TYPE;
        blocks.unfreeze(true);
        blockEntities.unfreeze(true);
        SmartInterfaceBlock block = new SmartInterfaceBlock(
                () -> ModBlockEntities.SMART_INTERFACE.get(), Blocks.IRON_BLOCK.properties());
        Registry.register(BuiltInRegistries.BLOCK, MMCR.id("smart_interface"), block);
        bind(ModBlocks.SMART_INTERFACE, block);
        Item item = registerItem(ModItems.ITEMS.get("smart_interface"));
        bind(ModItems.ITEMS.get("smart_interface"), item);
        Item.BY_BLOCK.put(block, item);

        BlockEntityType<?> blockEntityType = new BlockEntityType<>(SmartInterfaceBlockEntity::new, block);
        Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, MMCR.id("smart_interface"), blockEntityType);
        blockEntities.freeze();
        blocks.freeze();
        bind(ModBlockEntities.SMART_INTERFACE, blockEntityType);
    }

    private static void bindModuleBridge() throws Exception {
        MappedRegistry<Block> blocks = (MappedRegistry<Block>) BuiltInRegistries.BLOCK;
        MappedRegistry<BlockEntityType<?>> blockEntities = (MappedRegistry<BlockEntityType<?>>) BuiltInRegistries.BLOCK_ENTITY_TYPE;
        blocks.unfreeze(true);
        blockEntities.unfreeze(true);
        ModuleCouplerBlock block = new ModuleCouplerBlock(
                () -> ModBlockEntities.MODULE_BRIDGE.get(), Blocks.IRON_BLOCK.properties());
        Registry.register(BuiltInRegistries.BLOCK, MMCR.id("module_bridge"), block);
        bind(ModBlocks.MODULE_BRIDGE, block);
        Item item = registerItem(ModItems.ITEMS.get("module_bridge"));
        bind(ModItems.ITEMS.get("module_bridge"), item);
        Item.BY_BLOCK.put(block, item);

        BlockEntityType<?> blockEntityType = new BlockEntityType<>(ModuleCouplerBlockEntity::new, block);
        Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, MMCR.id("module_bridge"), blockEntityType);
        blockEntities.freeze();
        blocks.freeze();
        bind(ModBlockEntities.MODULE_BRIDGE, blockEntityType);
    }

    private static void bind(Object deferredHolder, Object value) throws Exception {
        Field holder = fieldInHierarchy(deferredHolder.getClass(), "holder");
        holder.setAccessible(true);
        holder.set(deferredHolder, Holder.direct(value));
    }

    private static Field fieldInHierarchy(Class<?> type, String name) throws NoSuchFieldException {
        Class<?> current = type;
        while (current != null) {
            try {
                return current.getDeclaredField(name);
            } catch (NoSuchFieldException ignored) {
                current = current.getSuperclass();
            }
        }
        throw new NoSuchFieldException(name);
    }

    private static void bindAllVanillaItemComponents() {
        Fluids.WATER.builtInRegistryHolder().bindComponents(DataComponentMap.EMPTY);
        for (Item item : BuiltInRegistries.ITEM) {
            Holder.Reference<Item> holder = item.builtInRegistryHolder();
            try {
                holder.components();
            } catch (NullPointerException ignored) {
                holder.bindComponents(DataComponentMap.EMPTY);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static Supplier<Item> registeredItemSupplier(DeferredHolder<Item, Item> itemHolder) throws Exception {
        Field field = ModItems.REGISTER.getClass().getSuperclass().getDeclaredField("entries");
        field.setAccessible(true);
        Map<DeferredHolder<Item, ? extends Item>, Supplier<? extends Item>> entries =
                (Map<DeferredHolder<Item, ? extends Item>, Supplier<? extends Item>>) field.get(ModItems.REGISTER);
        return (Supplier<Item>) entries.get(itemHolder);
    }

    private static Item registerItem(DeferredHolder<Item, Item> itemHolder) throws Exception {
        MappedRegistry<Item> items = (MappedRegistry<Item>) BuiltInRegistries.ITEM;
        items.unfreeze(true);
        Item item = registeredItemSupplier(itemHolder).get();
        Registry.register(BuiltInRegistries.ITEM, itemHolder.getId(), item);
        items.freeze();
        item.builtInRegistryHolder().bindComponents(DataComponentMap.builder().set(DataComponents.MAX_STACK_SIZE, 64).build());
        return item;
    }

    private static void bindPortBlocks() throws Exception {
        MappedRegistry<Block> blocks = (MappedRegistry<Block>) BuiltInRegistries.BLOCK;
        MappedRegistry<BlockEntityType<?>> blockEntities = (MappedRegistry<BlockEntityType<?>>) BuiltInRegistries.BLOCK_ENTITY_TYPE;
        blocks.unfreeze(true);
        blockEntities.unfreeze(true);
        for (var kind : PortKinds.all()) {
            Block block = new IOPortBlock(kind, () -> ModBlockEntities.BES.get(kind.id()).get(), Blocks.IRON_BLOCK.properties());
            if (!BuiltInRegistries.BLOCK.containsKey(MMCR.id(kind.id()))) {
                Registry.register(BuiltInRegistries.BLOCK, MMCR.id(kind.id()), block);
            }
            bind(ModBlocks.BLOCKS.get(kind.id()), block);
            DeferredHolder<Item, Item> itemHolder = ModItems.ITEMS.get(kind.id());
            Item item = registerItem(itemHolder);
            bind(itemHolder, item);
            Item.BY_BLOCK.put(block, item);

            if (!BuiltInRegistries.BLOCK_ENTITY_TYPE.containsKey(MMCR.id(kind.id()))) {
                Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, MMCR.id(kind.id()),
                        new BlockEntityType<>(kind.entityFactory(), block));
            }
            bind(ModBlockEntities.BES.get(kind.id()), BuiltInRegistries.BLOCK_ENTITY_TYPE.getValue(MMCR.id(kind.id())));
        }
        blockEntities.freeze();
        blocks.freeze();
    }

}

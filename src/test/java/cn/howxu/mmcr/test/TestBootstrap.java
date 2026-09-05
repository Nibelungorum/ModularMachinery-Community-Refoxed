package cn.howxu.mmcr.test;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.machine.MachineControllerSpec;
import cn.howxu.mmcr.api.machine.MachineDefinitions;
import cn.howxu.mmcr.api.machine.MachineRegistry;
import cn.howxu.mmcr.api.machine.level.LevelType;
import cn.howxu.mmcr.api.machine.level.MachineLevel;
import cn.howxu.mmcr.api.machine.level.MachineLevelRegistry;
import cn.howxu.mmcr.api.publicapi.event.MMCRMachineDefinationsEvent;
import cn.howxu.mmcr.api.publicapi.event.MMCRMachineStructuresEvent;
import cn.howxu.mmcr.api.publicapi.event.MMCRMachineRecipesEvent;
import cn.howxu.mmcr.api.publicapi.machine.BlockPredicate;
import cn.howxu.mmcr.api.publicapi.recipe.MachineRecipeBuilder;
import cn.howxu.mmcr.api.recipe.ParallelTier;
import cn.howxu.mmcr.api.recipe.RecipeRegistry;
import cn.howxu.mmcr.internal.block.FactorySchedulerBlock;
import cn.howxu.mmcr.internal.block.IOPortBlock;
import cn.howxu.mmcr.internal.block.MachineControllerBlock;
import cn.howxu.mmcr.internal.block.ModuleCouplerBlock;
import cn.howxu.mmcr.internal.block.NetworkInterfaceBlock;
import cn.howxu.mmcr.internal.block.ParallelControllerBlock;
import cn.howxu.mmcr.internal.block.SmartInterfaceBlock;
import cn.howxu.mmcr.internal.block.DataStorageBlock;
import cn.howxu.mmcr.internal.block.UpgradeBusBlock;
import cn.howxu.mmcr.internal.api.PublicApiBootstrap;
import cn.howxu.mmcr.internal.reload.DynamicContentReloadService;
import cn.howxu.mmcr.internal.registration.ContentRegistrationCoordinator;
import cn.howxu.mmcr.internal.registration.StartupContentRegistration;
import cn.howxu.mmcr.internal.tile.FactorySchedulerBlockEntity;
import cn.howxu.mmcr.internal.tile.MachineControllerBlockEntity;
import cn.howxu.mmcr.internal.tile.ModuleCouplerBlockEntity;
import cn.howxu.mmcr.internal.tile.NetworkInterfaceBlockEntity;
import cn.howxu.mmcr.internal.tile.ParallelControllerBlockEntity;
import cn.howxu.mmcr.internal.tile.SmartInterfaceBlockEntity;
import cn.howxu.mmcr.internal.tile.DataStorageBlockEntity;
import cn.howxu.mmcr.internal.tile.UpgradeBusBlockEntity;
import cn.howxu.mmcr.internal.port.UpgradeBusSize;
import cn.howxu.mmcr.registry.ModBlocks;
import cn.howxu.mmcr.registry.ModBlockEntities;
import cn.howxu.mmcr.registry.ModItems;
import cn.howxu.mmcr.registry.PortKinds;

import net.minecraft.core.Holder;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponentInitializers;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.registries.DeferredHolder;


import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.function.Supplier;

import java.nio.file.Path;

public final class TestBootstrap {
    private static boolean initialized;
    private static final Map<Identifier, LevelType> TEST_LEVEL_TYPES = new LinkedHashMap<>();
    private static final Map<Identifier, MachineLevel> TEST_LEVELS = new LinkedHashMap<>();

    private TestBootstrap() {
    }

    public static void beginRegistration() {
        TEST_LEVEL_TYPES.clear();
        TEST_LEVELS.clear();
        MachineLevelRegistry.installSnapshot(List.of(), List.of());
    }

    /** Resets and reopens the capability registry for tests that create real ports. */
    public static synchronized void bootstrapCapabilities() throws Exception {
        bootstrap();
        PublicApiBootstrap.clearForTesting();
        PublicApiBootstrap.begin();
    }

    public static void freezeRegistration() {
        MachineLevelRegistry.installSnapshot(TEST_LEVEL_TYPES.values(), TEST_LEVELS.values());
    }

    public static void registerType(LevelType type) {
        TEST_LEVEL_TYPES.put(type.id(), type);
    }

    public static void registerLevel(MachineLevel level) {
        TEST_LEVELS.put(level.id(), level);
        MachineLevelRegistry.installSnapshot(TEST_LEVEL_TYPES.values(), TEST_LEVELS.values());
    }

    public static synchronized void bootstrap() throws Exception {
        if (initialized) {
            if (MachineDefinitions.getRegistration(id("test_cube")) == null) {
                restoreMachineDefinitions();
            }
            bindAllVanillaItemComponents();
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
        Bootstrap.bootStrap();
        bindAllVanillaItemComponents();
        bindPortBlocks();
        for (ParallelTier tier : ParallelTier.values()) bindParallelController(tier);
        bindFactoryController();
        bindSmartInterface();
        bindDataStorage();
        bindNetworkInterface();
        bindModuleBridge();
        bindUpgradeBuses();
        bind(ModItems.THREAD_DISPERSER, registerItem(ModItems.THREAD_DISPERSER));
        bind(ModItems.TERMINAL, registerItem(ModItems.TERMINAL));
        bind(ModItems.KEY_CARD, registerItem(ModItems.KEY_CARD));
        bind(ModItems.BLUEPRINT, registerItem(ModItems.BLUEPRINT));
        registerTestEvents();
        registerRuntimeTestContent();
        initialized = true;
    }

    public static void restoreMachineDefinitions() {
        ContentRegistrationCoordinator.resetForTesting();
        MachineDefinitions.beginRegistryPhase();
        registerTestEvents();
    }

    public static void registerRuntimeBuiltins() {
        if (!ContentRegistrationCoordinator.isCommitted()
                || MachineDefinitions.getRegistration(id("test_cube")) == null
                || MachineRegistry.getMachine(id("test_cube")) == null) {
            restoreMachineDefinitions();
        }
        registerRuntimeTestContent();
    }

    private static void registerRuntimeTestContent() {
        DynamicContentReloadService.reload(candidate -> {
        });
        ModBlocks.registerMachineControllers(MachineRegistry.effectiveSnapshot().keySet());
        try {
            for (Identifier machineId : MachineRegistry.effectiveSnapshot().keySet()) {
                if (!ModBlocks.hasControllerFor(machineId)) bindController(machineId);
            }
        } catch (Exception e) {
            throw new AssertionError("Unable to bind runtime machine controllers", e);
        }
        MachineRegistry.rebuildCompiledCache();
    }

    public static synchronized void bindControllerForTesting(Identifier machineId) {
        ModBlocks.registerMachineControllers(List.of(machineId));
        ModBlockEntities.registerMachineControllers(List.of(machineId));
        try {
            bindControllerBlockEntity(machineId);
        } catch (Exception exception) {
            throw new AssertionError("Unable to bind test controller " + machineId, exception);
        }
    }

    private static void registerTestEvents() {
        StartupContentRegistration.registerForTesting(
                TestBootstrap::registerAllMachineDefinitions,
                TestBootstrap::registerAllMachineStructures,
                TestBootstrap::registerAllRecipes);
    }

    public static void registerAllMachineDefinitions(MMCRMachineDefinationsEvent event) {
        registerTestMachineDefinitions(event);
    }

    public static void registerAllMachineStructures(MMCRMachineStructuresEvent event) {
        registerTestMachineStructures(event);
    }

    public static void registerAllRecipes(MMCRMachineRecipesEvent event) {
        registerTestRecipes(event);
    }

    public static void registerTestMachineDefinitions(MMCRMachineDefinationsEvent event) {
        for (String name : testMachineNames()) {
            Identifier id = id(name);
            event.registerMachine(id, builder -> {
                builder.displayNameKey("machine.mmcr_test." + name);
                if (name.equals("test_cube")) builder.allowModifiers();
                return builder;
            });
        }
    }

    public static void registerTestMachineStructures(MMCRMachineStructuresEvent event) {
        try {
            for (String name : testMachineNames()) bindController(id(name));
            bind(ModBlocks.CASING, Blocks.STONE);
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to bind GameTest machine blocks", exception);
        }
        for (String name : testMachineNames()) {
            Identifier machineId = id(name);
            event.registerStructure(machineId, structure -> {
                structure.fullStructure(stage -> stage.pattern(pattern -> pattern
                        .layer("XXX").layer("XCX").layer("XXX")
                        .where('X', BlockPredicate.block(ModBlocks.CASING.get()))
                        .where('C', BlockPredicate.block(ModBlocks.controllerFor(machineId).get()))
                        .controller('C')));
                if (name.contains("expandable") || name.contains("distillation")) {
                    structure.extension(stage -> stage.pattern(pattern -> pattern
                            .layer("XXX").layer("XCX").layer("XXX")
                            .where('X', BlockPredicate.block(ModBlocks.CASING.get()))
                            .where('C', BlockPredicate.block(ModBlocks.controllerFor(machineId).get()))
                            .controller('C')));
                    structure.extension(stage -> stage.pattern(pattern -> pattern
                            .layer("XXX").layer("XCX").layer("XXX")
                            .where('X', BlockPredicate.block(ModBlocks.CASING.get()))
                            .where('C', BlockPredicate.block(ModBlocks.controllerFor(machineId).get()))
                            .controller('C')));
                }
                return structure;
            });
        }
    }

    public static void registerTestRecipes(MMCRMachineRecipesEvent event) {
        event.registerRecipe(MachineRecipeBuilder.recipe(
                Identifier.parse("mmcr_test:datapack_static_override"), id("iron_compressor"))
                .duration(20).inputItem(Items.COAL, 1).outputItem(Items.CHARCOAL, 1).build());
    }

    private static List<String> testMachineNames() {
        return List.of("test_cube", "test_machine_name", "runtime_test_machine", "runtime_test_machine_new",
                "controller_tick", "iron_compressor", "distillation_tower_test",
                "expandable_structure_stages", "expandable_structure_vertical_roll");
    }


    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MMCR.MODID, path);
    }

    private static void bindController(Identifier machineId) throws Exception {
        MachineControllerBlock block = bindControllerBlockEntity(machineId);
        String itemName = MachineControllerSpec.defaultsFor(machineId).id().getPath();
        DeferredHolder<Item, Item> itemHolder = ModItems.ITEMS.get(itemName);
        Item item = registerItem(itemHolder);
        bind(itemHolder, item);
        Item.BY_BLOCK.put(block, item);
    }

    private static MachineControllerBlock bindControllerBlockEntity(Identifier machineId) throws Exception {
        MachineControllerBlock block = controllerBlock(machineId);
        bind(ModBlocks.controllerFor(machineId), block);
        if (!ModBlockEntities.controllerFor(machineId).isBound()) {
            Identifier typeId = MachineControllerSpec.defaultsFor(machineId).id();
            MappedRegistry<BlockEntityType<?>> blockEntities = (MappedRegistry<BlockEntityType<?>>) BuiltInRegistries.BLOCK_ENTITY_TYPE;
            BlockEntityType<?> type;
            if (BuiltInRegistries.BLOCK_ENTITY_TYPE.containsKey(typeId)) {
                type = BuiltInRegistries.BLOCK_ENTITY_TYPE.getValue(typeId);
            } else {
                blockEntities.unfreeze(true);
                type = new BlockEntityType<>(MachineControllerBlockEntity::new, block);
                Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, typeId, type);
                blockEntities.freeze();
            }
            bind(ModBlockEntities.controllerFor(machineId), type);
        }
        return block;
    }

    private static MachineControllerBlock controllerBlock(Identifier machineId) {
        MappedRegistry<Block> blocks = (MappedRegistry<Block>) BuiltInRegistries.BLOCK;
        Identifier id = MachineControllerSpec.defaultsFor(machineId).id();
        if (BuiltInRegistries.BLOCK.containsKey(id)) {
            return (MachineControllerBlock) BuiltInRegistries.BLOCK.getValue(id);
        }
        blocks.unfreeze(true);
        MachineControllerBlock block = new MachineControllerBlock(machineId, Blocks.IRON_BLOCK.properties());
        Registry.register(BuiltInRegistries.BLOCK, id, block);
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
        Identifier id = MMCR.id(tier.idSuffix());
        if (BuiltInRegistries.BLOCK.containsKey(id)) {
            return (ParallelControllerBlock) BuiltInRegistries.BLOCK.getValue(id);
        }
        blocks.unfreeze(true);
        ParallelControllerBlock block = new ParallelControllerBlock(
                tier,
                () -> ModBlockEntities.BES.get(tier.idSuffix()).get(),
                Blocks.IRON_BLOCK.properties());
        Registry.register(BuiltInRegistries.BLOCK, id, block);
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

    private static void bindNetworkInterface() throws Exception {
        MappedRegistry<Block> blocks = (MappedRegistry<Block>) BuiltInRegistries.BLOCK;
        MappedRegistry<BlockEntityType<?>> blockEntities = (MappedRegistry<BlockEntityType<?>>) BuiltInRegistries.BLOCK_ENTITY_TYPE;
        blocks.unfreeze(true);
        blockEntities.unfreeze(true);
        NetworkInterfaceBlock block = new NetworkInterfaceBlock(
                () -> ModBlockEntities.NETWORK_INTERFACE.get(), Blocks.IRON_BLOCK.properties());
        Registry.register(BuiltInRegistries.BLOCK, MMCR.id("network_interface"), block);
        bind(ModBlocks.NETWORK_INTERFACE, block);
        Item item = registerItem(ModItems.ITEMS.get("network_interface"));
        bind(ModItems.ITEMS.get("network_interface"), item);
        Item.BY_BLOCK.put(block, item);

        BlockEntityType<?> blockEntityType = new BlockEntityType<>(NetworkInterfaceBlockEntity::new, block);
        Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, MMCR.id("network_interface"), blockEntityType);
        blockEntities.freeze();
        blocks.freeze();
        bind(ModBlockEntities.NETWORK_INTERFACE, blockEntityType);
    }

    private static void bindDataStorage() throws Exception {
        MappedRegistry<Block> blocks = (MappedRegistry<Block>) BuiltInRegistries.BLOCK;
        MappedRegistry<BlockEntityType<?>> blockEntities = (MappedRegistry<BlockEntityType<?>>) BuiltInRegistries.BLOCK_ENTITY_TYPE;
        blocks.unfreeze(true);
        blockEntities.unfreeze(true);
        DataStorageBlock block = new DataStorageBlock(
                () -> ModBlockEntities.DATA_STORAGE.get(), Blocks.IRON_BLOCK.properties());
        Registry.register(BuiltInRegistries.BLOCK, MMCR.id("data_storage"), block);
        bind(ModBlocks.DATA_STORAGE, block);
        Item item = registerItem(ModItems.ITEMS.get("data_storage"));
        bind(ModItems.ITEMS.get("data_storage"), item);
        Item.BY_BLOCK.put(block, item);

        BlockEntityType<?> blockEntityType = new BlockEntityType<>(DataStorageBlockEntity::new, block);
        Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, MMCR.id("data_storage"), blockEntityType);
        blockEntities.freeze();
        blocks.freeze();
        bind(ModBlockEntities.DATA_STORAGE, blockEntityType);
    }

    private static void bindUpgradeBuses() throws Exception {
        MappedRegistry<Block> blocks = (MappedRegistry<Block>) BuiltInRegistries.BLOCK;
        MappedRegistry<BlockEntityType<?>> blockEntities = (MappedRegistry<BlockEntityType<?>>) BuiltInRegistries.BLOCK_ENTITY_TYPE;
        blocks.unfreeze(true);
        blockEntities.unfreeze(true);
        for (UpgradeBusSize size : UpgradeBusSize.values()) {
            String name = "upgrade_bus_" + size.id();
            UpgradeBusBlock block = new UpgradeBusBlock(size,
                    () -> ModBlockEntities.BES.get(name).get(), Blocks.IRON_BLOCK.properties());
            if (!BuiltInRegistries.BLOCK.containsKey(MMCR.id(name))) {
                Registry.register(BuiltInRegistries.BLOCK, MMCR.id(name), block);
            }
            bind(ModBlocks.BLOCKS.get(name), block);
            DeferredHolder<Item, Item> itemHolder = ModItems.ITEMS.get(name);
            Item item = registerItem(itemHolder);
            bind(itemHolder, item);
            Item.BY_BLOCK.put(block, item);

            BlockEntityType<?> blockEntityType;
            if (BuiltInRegistries.BLOCK_ENTITY_TYPE.containsKey(MMCR.id(name))) {
                blockEntityType = BuiltInRegistries.BLOCK_ENTITY_TYPE.getValue(MMCR.id(name));
            } else {
                blockEntityType = new BlockEntityType<>((pos, state) -> new UpgradeBusBlockEntity(size, pos, state), block);
                Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, MMCR.id(name), blockEntityType);
            }
            bind(ModBlockEntities.BES.get(name), blockEntityType);
        }
        blockEntities.freeze();
        blocks.freeze();
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
        try {
            Fluids.WATER.builtInRegistryHolder().bindComponents(DataComponentMap.EMPTY);
            Fluids.LAVA.builtInRegistryHolder().bindComponents(DataComponentMap.EMPTY);
            Field initializersField = DataComponentInitializers.class.getDeclaredField("initializers");
            initializersField.setAccessible(true);
            Field keyField = null;
            Field initializerField = null;
            RegistryAccess registryAccess = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
            for (Object entry : (List<?>) initializersField.get(BuiltInRegistries.DATA_COMPONENT_INITIALIZERS)) {
                if (keyField == null) {
                    keyField = entry.getClass().getDeclaredField("key");
                    keyField.setAccessible(true);
                    initializerField = entry.getClass().getDeclaredField("initializer");
                    initializerField.setAccessible(true);
                }
                ResourceKey<?> key = (ResourceKey<?>) keyField.get(entry);
                if (!key.isFor(Registries.ITEM)) continue;
                @SuppressWarnings("unchecked")
                ResourceKey<Item> itemKey = (ResourceKey<Item>) key;
                Item item = BuiltInRegistries.ITEM.getValue(itemKey);
                if (item == null) continue;

                DataComponentMap.Builder builder = DataComponentMap.builder();
                @SuppressWarnings("unchecked")
                DataComponentInitializers.Initializer<Item> initializer =
                        (DataComponentInitializers.Initializer<Item>) initializerField.get(entry);
                try {
                    initializer.run(builder, registryAccess, itemKey);
                } catch (IllegalStateException ignored) {
                    // The test bootstrap has no loaded tags for delayed components.
                }
                item.builtInRegistryHolder().bindComponents(builder.build());
            }
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError("Unable to bind vanilla item components", exception);
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
        if (BuiltInRegistries.ITEM.containsKey(itemHolder.getId())) {
            return BuiltInRegistries.ITEM.getValue(itemHolder.getId());
        }
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

package cn.howxu.mmcr.test;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.machine.MachineControllerSpec;
import cn.howxu.mmcr.api.machine.MachineDefinitions;
import cn.howxu.mmcr.api.machine.MachineRegistry;
import cn.howxu.mmcr.api.machine.level.LevelType;
import cn.howxu.mmcr.api.machine.level.MachineLevel;
import cn.howxu.mmcr.api.machine.level.MachineLevelRegistry;
import cn.howxu.mmcr.api.capability.status.BuiltinFailureReasons;
import cn.howxu.mmcr.api.capability.status.FailureReasonRegistry;
import cn.howxu.mmcr.api.registration.MachineDefinitionRegistration;
import cn.howxu.mmcr.api.registration.StructureRegistration;
import cn.howxu.mmcr.api.registration.MachineRecipeRegistration;
import cn.howxu.mmcr.api.machine.definition.BlockPredicate;
import cn.howxu.mmcr.api.recipe.MachineRecipeBuilder;
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
import cn.howxu.mmcr.internal.recipe.FactoryRecipeThread;
import cn.howxu.mmcr.internal.tile.FactorySchedulerBlockEntity;
import cn.howxu.mmcr.internal.tile.MachineControllerBlockEntity;
import cn.howxu.mmcr.internal.tile.ModuleCouplerBlockEntity;
import cn.howxu.mmcr.internal.tile.NetworkInterfaceBlockEntity;
import cn.howxu.mmcr.internal.tile.ParallelControllerBlockEntity;
import cn.howxu.mmcr.internal.tile.SmartInterfaceBlockEntity;
import cn.howxu.mmcr.internal.tile.DataStorageBlockEntity;
import cn.howxu.mmcr.internal.tile.UpgradeBusBlockEntity;
import cn.howxu.mmcr.api.recipe.ActiveMachineRecipe;
import cn.howxu.mmcr.api.recipe.MachineOutput;
import cn.howxu.mmcr.api.recipe.requirement.MachineRequirement;
import cn.howxu.mmcr.internal.runtime.CraftingRuntime;
import cn.howxu.mmcr.internal.runtime.FactoryRuntime;
import cn.howxu.mmcr.internal.tile.MachineControllerRuntime;
import cn.howxu.mmcr.internal.port.UpgradeBusSize;
import cn.howxu.mmcr.registry.ModBlocks;
import cn.howxu.mmcr.registry.ModBlockEntities;
import cn.howxu.mmcr.registry.ModItems;
import cn.howxu.mmcr.registry.PortKinds;

import net.minecraft.core.Holder;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.registries.DeferredHolder;


import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.function.Supplier;

public final class TestBootstrap {
    private static boolean initialized;
    private static final Map<ResourceLocation, LevelType> TEST_LEVEL_TYPES = new LinkedHashMap<>();
    private static final Map<ResourceLocation, MachineLevel> TEST_LEVELS = new LinkedHashMap<>();

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
        ensureFailureReasons();
        if (initialized) {
            if (MachineDefinitions.getRegistration(id("test_cube")) == null
                    || MachineRegistry.getCompiled(id("test_cube")) == null) {
                restoreMachineDefinitions();
            }
            return;
        }

        Class<?> fmlLoaderCls = Class.forName("net.neoforged.fml.loading.FMLLoader");
        Class<?> loadingModListCls = Class.forName("net.neoforged.fml.loading.LoadingModList");
        Object fmlLoader = fmlLoaderCls.getConstructor().newInstance();
        Object emptyLoadingModList = loadingModListCls.getMethod(
                "of", List.class, List.class, List.class, List.class, Map.class)
                .invoke(null, List.of(), List.of(), List.of(), List.of(), Map.of());
        Field loadingModListField = fmlLoaderCls.getDeclaredField("loadingModList");
        loadingModListField.setAccessible(true);
        loadingModListField.set(null, emptyLoadingModList);

        Class.forName("net.minecraft.SharedConstants").getMethod("tryDetectVersion").invoke(null);
        NeoForge.EVENT_BUS.start();
        MachineDefinitions.beginRegistryPhase();
        Bootstrap.bootStrap();
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

    private static void ensureFailureReasons() {
        if (FailureReasonRegistry.find(BuiltinFailureReasons.UNKNOWN.id()) != null) return;
        boolean frozen = FailureReasonRegistry.isFrozen();
        if (frozen) FailureReasonRegistry.clearForTesting();
        BuiltinFailureReasons.register();
        if (frozen) FailureReasonRegistry.freeze();
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

    /**
     * Builds a {@link CraftingRuntime} over a freshly created test controller.
     * Convenience helper for tests that only need an empty runtime instance.
     */
    public static CraftingRuntime newCraftingRuntime() {
        MachineControllerBlockEntity controller = RuntimeTestFixtures.controller(MMCR.id("test_cube"));
        return new CraftingRuntime(controller, controller.componentRuntime());
    }

    /**
     * Builds a fresh {@link MachineControllerBlockEntity} backed by the {@code test_cube} machine.
     * Convenience helper for tests that need the controller itself (not just a runtime).
     */
    public static MachineControllerBlockEntity newController() {
        return RuntimeTestFixtures.controller(MMCR.id("test_cube"));
    }

    /**
     * Pushes a synthetic factory lane whose {@link CraftingRuntime#activeOutputs()} returns the
     * supplied list and whose {@link CraftingRuntime#active()} returns {@code true}.
     * Used to exercise the controller-level factory aggregation path without standing up a real
     * factory controller or starting a recipe.
     */
    public static void bindFactoryWithOutputs(MachineControllerBlockEntity controller,
                                               List<MachineOutput> outputs) {
        bindFactoryWithOutputs(controller, outputs, 1L);
    }

    public static void bindFactoryWithOutputs(MachineControllerBlockEntity controller,
                                              List<MachineOutput> outputs, long parallelism) {
        if (controller == null) throw new IllegalArgumentException("controller must not be null");
        try {
            MachineControllerRuntime controllerRuntime = controllerRuntimeField(controller);
            FactoryRuntime factoryRuntime = controllerRuntime.factoryRuntime();
            @SuppressWarnings("unchecked")
            List<FactoryRecipeThread> lanes =
                    (List<FactoryRecipeThread>) FACTORY_LANES_FIELD.get(factoryRuntime);
            CraftingRuntime runtime = syntheticCraftingRuntimeWithOutputs(controller, outputs, parallelism);
            FactoryRecipeThread lane = syntheticLaneFor(controller, runtime, lanes.size());
            lanes.add(lane);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError("Unable to bind factory outputs", exception);
        }
    }

    public static void bindCraftingWithOutputs(MachineControllerBlockEntity controller,
                                               List<MachineOutput> outputs, long parallelism) {
        if (controller == null) throw new IllegalArgumentException("controller must not be null");
        try {
            MachineControllerRuntime controllerRuntime = controllerRuntimeField(controller);
            configureCraftingRuntimeWithOutputs(controllerRuntime.craftingRuntime(), outputs, parallelism);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError("Unable to bind crafting outputs", exception);
        }
    }

    private static final sun.misc.Unsafe UNSAFE = resolveUnsafe();
    private static final Field CONTROLLER_RUNTIME_FIELD = fieldOnHierarchy(
            MachineControllerBlockEntity.class, "runtime");
    private static final Field FACTORY_LANES_FIELD = fieldOnHierarchy(FactoryRuntime.class, "lanes");
    private static final Field CRAFTING_ACTIVE_RECIPE_FIELD =
            fieldOnHierarchy(CraftingRuntime.class, "activeRecipe");
    private static final Field CRAFTING_EFFECTIVE_OUTPUTS_FIELD =
            fieldOnHierarchy(CraftingRuntime.class, "effectiveOutputs");
    private static final Field CRAFTING_EFFECTIVE_REQUIREMENTS_FIELD =
            fieldOnHierarchy(CraftingRuntime.class, "effectiveRequirements");
    private static final Field RECIPE_THREAD_RUNTIME_FIELD =
            fieldOnHierarchy(FactoryRecipeThread.class.getSuperclass(), "runtime");

    private static MachineControllerRuntime controllerRuntimeField(MachineControllerBlockEntity controller)
            throws ReflectiveOperationException {
        return (MachineControllerRuntime) CONTROLLER_RUNTIME_FIELD.get(controller);
    }

    private static CraftingRuntime syntheticCraftingRuntimeWithOutputs(MachineControllerBlockEntity controller,
                                                                       List<MachineOutput> outputs, long parallelism)
            throws ReflectiveOperationException {
        CraftingRuntime runtime = new CraftingRuntime(controller, controller.componentRuntime());
        configureCraftingRuntimeWithOutputs(runtime, outputs, parallelism);
        return runtime;
    }

    private static void configureCraftingRuntimeWithOutputs(CraftingRuntime runtime,
                                                             List<MachineOutput> outputs, long parallelism)
            throws ReflectiveOperationException {
        if (parallelism <= 0L) throw new IllegalArgumentException("parallelism must be positive");
        ActiveMachineRecipe placeholder = (ActiveMachineRecipe)
                UNSAFE.allocateInstance(ActiveMachineRecipe.class);
        placeholder.setMaxParallelism(parallelism);
        placeholder.setParallelism(parallelism);
        CRAFTING_ACTIVE_RECIPE_FIELD.set(runtime, placeholder);
        CRAFTING_EFFECTIVE_OUTPUTS_FIELD.set(runtime, List.copyOf(outputs));
    }

    public static void configureCraftingWithPresentation(CraftingRuntime runtime,
                                                         List<MachineOutput> outputs,
                                                         List<MachineRequirement> requirements,
                                                         long parallelism) {
        if (runtime == null) throw new IllegalArgumentException("runtime must not be null");
        try {
            configureCraftingRuntimeWithOutputs(runtime, outputs, parallelism);
            CRAFTING_EFFECTIVE_REQUIREMENTS_FIELD.set(runtime, List.copyOf(requirements));
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError("Unable to bind crafting presentation", exception);
        }
    }

    private static sun.misc.Unsafe resolveUnsafe() {
        try {
            Field unsafeField = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
            unsafeField.setAccessible(true);
            return (sun.misc.Unsafe) unsafeField.get(null);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError("Unable to resolve sun.misc.Unsafe", exception);
        }
    }

    private static FactoryRecipeThread syntheticLaneFor(MachineControllerBlockEntity controller,
                                                       CraftingRuntime runtime, int index)
            throws ReflectiveOperationException {
        String laneId = "test-lane-" + index;
        FactoryRecipeThread lane = FactoryRecipeThread.simple(controller, laneId);
        RECIPE_THREAD_RUNTIME_FIELD.set(lane, runtime);
        return lane;
    }

    private static Field fieldOnHierarchy(Class<?> type, String name) {
        Class<?> current = type;
        while (current != null) {
            try {
                Field field = current.getDeclaredField(name);
                field.setAccessible(true);
                return field;
            } catch (NoSuchFieldException ignored) {
                current = current.getSuperclass();
            }
        }
        throw new IllegalStateException("Field " + name + " not found on " + type);
    }

    private static void registerRuntimeTestContent() {
        DynamicContentReloadService.reload(candidate -> {
        });
        ModBlocks.registerMachineControllers(MachineRegistry.effectiveSnapshot().keySet());
        try {
            for (ResourceLocation machineId : MachineRegistry.effectiveSnapshot().keySet()) {
                if (!ModBlocks.hasControllerFor(machineId)) bindController(machineId);
            }
        } catch (Exception e) {
            throw new AssertionError("Unable to bind runtime machine controllers", e);
        }
        MachineRegistry.rebuildCompiledCache();
    }

    public static synchronized void bindControllerForTesting(ResourceLocation machineId) {
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

    public static void registerAllMachineDefinitions(MachineDefinitionRegistration event) {
        registerTestMachineDefinitions(event);
    }

    public static void registerAllMachineStructures(StructureRegistration event) {
        registerTestMachineStructures(event);
    }

    public static void registerAllRecipes(MachineRecipeRegistration event) {
        registerTestRecipes(event);
    }

    public static void registerTestMachineDefinitions(MachineDefinitionRegistration event) {
        for (String name : testMachineNames()) {
            ResourceLocation id = id(name);
            event.registerMachine(id, builder -> {
                builder.displayNameKey("machine.mmcr_test." + name);
                if (name.equals("test_cube")) builder.allowModifiers();
                return builder;
            });
        }
    }

    public static void registerTestMachineStructures(StructureRegistration event) {
        try {
            for (String name : testMachineNames()) bindController(id(name));
            bind(ModBlocks.CASING, Blocks.STONE);
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to bind GameTest machine blocks", exception);
        }
        for (String name : testMachineNames()) {
            ResourceLocation machineId = id(name);
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

    public static void registerTestRecipes(MachineRecipeRegistration event) {
        event.registerRecipe(MachineRecipeBuilder.recipe(
                ResourceLocation.parse("mmcr_test:datapack_static_override")).recipePool(id("iron_compressor"))
                .duration(20).inputItem(Items.COAL, 1).outputItem(Items.CHARCOAL, 1).build());
    }

    private static List<String> testMachineNames() {
        return List.of("test_cube", "test_machine_name", "runtime_test_machine", "runtime_test_machine_new",
                "controller_tick", "iron_compressor", "distillation_tower_test",
                "expandable_structure_stages", "expandable_structure_vertical_roll");
    }


    private static ResourceLocation id(String path) {
        return MMCR.id(path);
    }

    private static void bindController(ResourceLocation machineId) throws Exception {
        MachineControllerBlock block = bindControllerBlockEntity(machineId);
        String itemName = MachineControllerSpec.defaultsFor(machineId).id().getPath();
        DeferredHolder<Item, Item> itemHolder = ModItems.ITEMS.get(itemName);
        Item item = registerItem(itemHolder);
        bind(itemHolder, item);
        Item.BY_BLOCK.put(block, item);
    }

    private static MachineControllerBlock bindControllerBlockEntity(ResourceLocation machineId) throws Exception {
        MachineControllerBlock block = controllerBlock(machineId);
        bind(ModBlocks.controllerFor(machineId), block);
        if (!ModBlockEntities.controllerFor(machineId).isBound()) {
            ResourceLocation typeId = MachineControllerSpec.defaultsFor(machineId).id();
            MappedRegistry<BlockEntityType<?>> blockEntities = (MappedRegistry<BlockEntityType<?>>) BuiltInRegistries.BLOCK_ENTITY_TYPE;
            BlockEntityType<?> type;
            if (BuiltInRegistries.BLOCK_ENTITY_TYPE.containsKey(typeId)) {
                type = BuiltInRegistries.BLOCK_ENTITY_TYPE.get(typeId);
            } else {
                blockEntities.unfreeze();
                type = BlockEntityType.Builder.of(MachineControllerBlockEntity::new, block).build(null);
                Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, typeId, type);
                blockEntities.freeze();
            }
            bind(ModBlockEntities.controllerFor(machineId), type);
        }
        return block;
    }

    private static MachineControllerBlock controllerBlock(ResourceLocation machineId) {
        MappedRegistry<Block> blocks = (MappedRegistry<Block>) BuiltInRegistries.BLOCK;
        ResourceLocation id = MachineControllerSpec.defaultsFor(machineId).id();
        if (BuiltInRegistries.BLOCK.containsKey(id)) {
            return (MachineControllerBlock) BuiltInRegistries.BLOCK.get(id);
        }
        blocks.unfreeze();
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
        ResourceLocation id = MMCR.id(tier.idSuffix());
        if (BuiltInRegistries.BLOCK.containsKey(id)) {
            return (ParallelControllerBlock) BuiltInRegistries.BLOCK.get(id);
        }
        blocks.unfreeze();
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
        blockEntities.unfreeze();
        BlockEntityType<?> beType = BlockEntityType.Builder.of(
                (pos, state) -> new ParallelControllerBlockEntity(tier, pos, state), block).build(null);
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
        blocks.unfreeze();
        FactorySchedulerBlock block = new FactorySchedulerBlock(
                () -> ModBlockEntities.BES.get("factory_controller").get(),
                Blocks.IRON_BLOCK.properties());
        Registry.register(BuiltInRegistries.BLOCK, MMCR.id("factory_controller"), block);
        blocks.freeze();
        return block;
    }

    private static BlockEntityType<?> factoryControllerBlockEntityType(FactorySchedulerBlock block) {
        MappedRegistry<BlockEntityType<?>> blockEntities = (MappedRegistry<BlockEntityType<?>>) BuiltInRegistries.BLOCK_ENTITY_TYPE;
        blockEntities.unfreeze();
        BlockEntityType<?> beType = BlockEntityType.Builder.of(FactorySchedulerBlockEntity::new, block).build(null);
        Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, MMCR.id("factory_controller"), beType);
        blockEntities.freeze();
        return beType;
    }

    private static void bindSmartInterface() throws Exception {
        MappedRegistry<Block> blocks = (MappedRegistry<Block>) BuiltInRegistries.BLOCK;
        MappedRegistry<BlockEntityType<?>> blockEntities = (MappedRegistry<BlockEntityType<?>>) BuiltInRegistries.BLOCK_ENTITY_TYPE;
        blocks.unfreeze();
        blockEntities.unfreeze();
        SmartInterfaceBlock block = new SmartInterfaceBlock(
                () -> ModBlockEntities.SMART_INTERFACE.get(), Blocks.IRON_BLOCK.properties());
        Registry.register(BuiltInRegistries.BLOCK, MMCR.id("smart_interface"), block);
        bind(ModBlocks.SMART_INTERFACE, block);
        Item item = registerItem(ModItems.ITEMS.get("smart_interface"));
        bind(ModItems.ITEMS.get("smart_interface"), item);
        Item.BY_BLOCK.put(block, item);

        BlockEntityType<?> blockEntityType = BlockEntityType.Builder.of(SmartInterfaceBlockEntity::new, block).build(null);
        Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, MMCR.id("smart_interface"), blockEntityType);
        blockEntities.freeze();
        blocks.freeze();
        bind(ModBlockEntities.SMART_INTERFACE, blockEntityType);
    }

    private static void bindModuleBridge() throws Exception {
        MappedRegistry<Block> blocks = (MappedRegistry<Block>) BuiltInRegistries.BLOCK;
        MappedRegistry<BlockEntityType<?>> blockEntities = (MappedRegistry<BlockEntityType<?>>) BuiltInRegistries.BLOCK_ENTITY_TYPE;
        blocks.unfreeze();
        blockEntities.unfreeze();
        ModuleCouplerBlock block = new ModuleCouplerBlock(
                () -> ModBlockEntities.MODULE_BRIDGE.get(), Blocks.IRON_BLOCK.properties());
        Registry.register(BuiltInRegistries.BLOCK, MMCR.id("module_bridge"), block);
        bind(ModBlocks.MODULE_BRIDGE, block);
        Item item = registerItem(ModItems.ITEMS.get("module_bridge"));
        bind(ModItems.ITEMS.get("module_bridge"), item);
        Item.BY_BLOCK.put(block, item);

        BlockEntityType<?> blockEntityType = BlockEntityType.Builder.of(ModuleCouplerBlockEntity::new, block).build(null);
        Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, MMCR.id("module_bridge"), blockEntityType);
        blockEntities.freeze();
        blocks.freeze();
        bind(ModBlockEntities.MODULE_BRIDGE, blockEntityType);
    }

    private static void bindNetworkInterface() throws Exception {
        MappedRegistry<Block> blocks = (MappedRegistry<Block>) BuiltInRegistries.BLOCK;
        MappedRegistry<BlockEntityType<?>> blockEntities = (MappedRegistry<BlockEntityType<?>>) BuiltInRegistries.BLOCK_ENTITY_TYPE;
        blocks.unfreeze();
        blockEntities.unfreeze();
        NetworkInterfaceBlock block = new NetworkInterfaceBlock(
                () -> ModBlockEntities.NETWORK_INTERFACE.get(), Blocks.IRON_BLOCK.properties());
        Registry.register(BuiltInRegistries.BLOCK, MMCR.id("network_interface"), block);
        bind(ModBlocks.NETWORK_INTERFACE, block);
        Item item = registerItem(ModItems.ITEMS.get("network_interface"));
        bind(ModItems.ITEMS.get("network_interface"), item);
        Item.BY_BLOCK.put(block, item);

        BlockEntityType<?> blockEntityType = BlockEntityType.Builder.of(NetworkInterfaceBlockEntity::new, block).build(null);
        Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, MMCR.id("network_interface"), blockEntityType);
        blockEntities.freeze();
        blocks.freeze();
        bind(ModBlockEntities.NETWORK_INTERFACE, blockEntityType);
    }

    private static void bindDataStorage() throws Exception {
        MappedRegistry<Block> blocks = (MappedRegistry<Block>) BuiltInRegistries.BLOCK;
        MappedRegistry<BlockEntityType<?>> blockEntities = (MappedRegistry<BlockEntityType<?>>) BuiltInRegistries.BLOCK_ENTITY_TYPE;
        blocks.unfreeze();
        blockEntities.unfreeze();
        DataStorageBlock block = new DataStorageBlock(
                () -> ModBlockEntities.DATA_STORAGE.get(), Blocks.IRON_BLOCK.properties());
        Registry.register(BuiltInRegistries.BLOCK, MMCR.id("data_storage"), block);
        bind(ModBlocks.DATA_STORAGE, block);
        Item item = registerItem(ModItems.ITEMS.get("data_storage"));
        bind(ModItems.ITEMS.get("data_storage"), item);
        Item.BY_BLOCK.put(block, item);

        BlockEntityType<?> blockEntityType = BlockEntityType.Builder.of(DataStorageBlockEntity::new, block).build(null);
        Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, MMCR.id("data_storage"), blockEntityType);
        blockEntities.freeze();
        blocks.freeze();
        bind(ModBlockEntities.DATA_STORAGE, blockEntityType);
    }

    private static void bindUpgradeBuses() throws Exception {
        MappedRegistry<Block> blocks = (MappedRegistry<Block>) BuiltInRegistries.BLOCK;
        MappedRegistry<BlockEntityType<?>> blockEntities = (MappedRegistry<BlockEntityType<?>>) BuiltInRegistries.BLOCK_ENTITY_TYPE;
        blocks.unfreeze();
        blockEntities.unfreeze();
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
                blockEntityType = BuiltInRegistries.BLOCK_ENTITY_TYPE.get(MMCR.id(name));
            } else {
                blockEntityType = BlockEntityType.Builder.of(
                        (pos, state) -> new UpgradeBusBlockEntity(size, pos, state), block).build(null);
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
            return BuiltInRegistries.ITEM.get(itemHolder.getId());
        }
        items.unfreeze();
        Item item = registeredItemSupplier(itemHolder).get();
        Registry.register(BuiltInRegistries.ITEM, itemHolder.getId(), item);
        items.freeze();
        return item;
    }

    private static void bindPortBlocks() throws Exception {
        MappedRegistry<Block> blocks = (MappedRegistry<Block>) BuiltInRegistries.BLOCK;
        MappedRegistry<BlockEntityType<?>> blockEntities = (MappedRegistry<BlockEntityType<?>>) BuiltInRegistries.BLOCK_ENTITY_TYPE;
        blocks.unfreeze();
        blockEntities.unfreeze();
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
                        BlockEntityType.Builder.of((BlockEntityType.BlockEntitySupplier) kind.entityFactory(), block)
                                .build(null));
            }
            bind(ModBlockEntities.BES.get(kind.id()), BuiltInRegistries.BLOCK_ENTITY_TYPE.get(MMCR.id(kind.id())));
        }
        blockEntities.freeze();
        blocks.freeze();
    }

}

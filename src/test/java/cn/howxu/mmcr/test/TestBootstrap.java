package cn.howxu.mmcr.test;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.machine.BlockArray;
import cn.howxu.mmcr.api.machine.DynamicMachine;
import cn.howxu.mmcr.api.machine.MachineControllerSpec;
import cn.howxu.mmcr.api.machine.MachineDefinitions;
import cn.howxu.mmcr.registry.ModBlocks;
import cn.howxu.mmcr.registry.ModItems;
import cn.howxu.mmcr.internal.block.MachineControllerBlock;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.registries.DeferredHolder;
import org.nibelungorum.BuiltinMachines;

import java.lang.reflect.Field;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
                ClassLoader.class, String[].class, distCls, boolean.class, java.nio.file.Path.class);
        fmlCtor.setAccessible(true);
        Object fmlLoader = fmlCtor.newInstance(
                Thread.currentThread().getContextClassLoader(), new String[0],
                distCls.getField("CLIENT").get(null), false, java.nio.file.Path.of("."));

        var lmlCtor = loadingModListCls.getDeclaredConstructor(
                List.class, List.class, List.class, List.class, Map.class);
        lmlCtor.setAccessible(true);
        Object emptyLoadingModList = lmlCtor.newInstance(
                List.of(), List.of(), List.of(), List.of(), Map.of());
        Field loadingModListField = fmlLoaderCls.getDeclaredField("loadingModList");
        loadingModListField.setAccessible(true);
        loadingModListField.set(fmlLoader, emptyLoadingModList);

        Class.forName("net.minecraft.SharedConstants").getMethod("tryDetectVersion").invoke(null);
        BuiltinMachines.register();
        MachineDefinitions.addBuiltinSupplier(() ->
                new DynamicMachine(id("test_cube"), "Test", new BlockArray(Map.of())));
        MachineDefinitions.addBuiltinSupplier(() ->
                new DynamicMachine(id("controller_tick"), "Controller Tick", new BlockArray(Map.of())));
        MachineDefinitions.addBuiltinSupplier(() ->
                new DynamicMachine(id("iron_compressor"), "Iron Compressor", new BlockArray(Map.of())));
        MachineDefinitions.bootstrapBuiltins();
        Bootstrap.bootStrap();
        bind(ModBlocks.CASING, Blocks.STONE);
        bindController(MMCR.id("blast_furnace"));
        bindController(id("alloy_furnace"));
        bindController(id("cracker"));
        bindController(id("reactor"));
        bindController(id("test_cube"));
        bindController(id("controller_tick"));
        bindController(id("iron_compressor"));
        bind(ModBlocks.BLOCKS.get("item_input_bus"), Blocks.CHEST);
        bind(ModBlocks.BLOCKS.get("item_output_bus"), Blocks.CHEST);
        bind(ModBlocks.BLOCKS.get("fluid_input_hatch"), Blocks.BARREL);
        bind(ModBlocks.BLOCKS.get("fluid_output_hatch"), Blocks.BARREL);
        bind(ModBlocks.BLOCKS.get("energy_input_hatch"), Blocks.COPPER_BLOCK);
        bind(ModBlocks.BLOCKS.get("energy_output_hatch"), Blocks.COPPER_BLOCK);
        registerRuntimeBuiltins();
        initialized = true;
    }

    public static void restoreMachineDefinitions() {
        BuiltinMachines.register();
        MachineDefinitions.addBuiltinSupplier(() ->
                new DynamicMachine(id("test_cube"), "Test", new BlockArray(Map.of())));
        MachineDefinitions.addBuiltinSupplier(() ->
                new DynamicMachine(id("controller_tick"), "Controller Tick", new BlockArray(Map.of())));
        MachineDefinitions.addBuiltinSupplier(() ->
                new DynamicMachine(id("iron_compressor"), "Iron Compressor", new BlockArray(Map.of())));
        MachineDefinitions.bootstrapBuiltins();
    }

    public static void registerRuntimeBuiltins() {
        MMCR.registerRuntimeBuiltins();
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MMCR.MODID, path);
    }

    private static void bindController(Identifier machineId) throws Exception {
        MachineControllerBlock block = controllerBlock(machineId);
        bind(ModBlocks.controllerFor(machineId), block);
        String itemName = MachineControllerSpec.defaultsFor(machineId).id().getPath();
        DeferredHolder<Item, Item> itemHolder = ModItems.ITEMS.get(itemName);
        Item item = blockItem(block);
        bind(itemHolder, item);
        Item.BY_BLOCK.put(block, item);
        reserveControllerItem(item, machineId);
    }

    private static void bind(Object deferredHolder, Object value) throws Exception {
        Field holder = deferredHolder.getClass().getSuperclass().getDeclaredField("holder");
        holder.setAccessible(true);
        holder.set(deferredHolder, Holder.direct(value));
    }

    private static MachineControllerBlock controllerBlock(Identifier machineId) throws Exception {
        Field unsafeField = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
        unsafeField.setAccessible(true);
        sun.misc.Unsafe unsafe = (sun.misc.Unsafe) unsafeField.get(null);
        MachineControllerBlock block = (MachineControllerBlock) unsafe.allocateInstance(MachineControllerBlock.class);
        Field machineIdField = MachineControllerBlock.class.getDeclaredField("machineId");
        machineIdField.setAccessible(true);
        machineIdField.set(block, machineId);
        return block;
    }

    private static BlockItem blockItem(MachineControllerBlock block) throws Exception {
        Field unsafeField = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
        unsafeField.setAccessible(true);
        sun.misc.Unsafe unsafe = (sun.misc.Unsafe) unsafeField.get(null);
        BlockItem item = (BlockItem) unsafe.allocateInstance(BlockItem.class);
        Field blockField = BlockItem.class.getDeclaredField("block");
        blockField.setAccessible(true);
        blockField.set(item, block);
        return item;
    }

    private static void reserveControllerItem(Item item, Identifier machineId) throws Exception {
        Field field = ModItems.class.getDeclaredField("controllerMachineIds");
        field.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<Item, Identifier> controllerMachineIds = (Map<Item, Identifier>) field.get(null);
        Map<Item, Identifier> ids = new LinkedHashMap<>(controllerMachineIds);
        ids.put(item, machineId);
        field.set(null, Map.copyOf(ids));
    }

}

package cn.howxu.mmcr.test;

import cn.howxu.mmcr.api.machine.MachineDefinitions;
import cn.howxu.mmcr.registry.ModBlocks;
import net.minecraft.core.Holder;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import org.nibelungorum.BuiltinMachines;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

public final class TestBootstrap {
    private static boolean initialized;

    private TestBootstrap() {
    }

    public static synchronized void bootstrap() throws Exception {
        if (initialized) {
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
        MachineDefinitions.bootstrapBuiltins();
        Bootstrap.bootStrap();
        bind(ModBlocks.CASING, Blocks.STONE);
        bind(ModBlocks.controllerFor(cn.howxu.mmcr.MMCR.id("blast_furnace")), Blocks.IRON_BLOCK);
        bind(ModBlocks.BLOCKS.get("io_port_item_basic"), Blocks.CHEST);
        bind(ModBlocks.BLOCKS.get("io_port_fluid_basic"), Blocks.BARREL);
        bind(ModBlocks.BLOCKS.get("io_port_energy_basic"), Blocks.COPPER_BLOCK);
        initialized = true;
    }

    private static void bind(Object deferredHolder, Block block) throws Exception {
        Field holder = deferredHolder.getClass().getSuperclass().getDeclaredField("holder");
        holder.setAccessible(true);
        holder.set(deferredHolder, Holder.direct(block));
    }
}

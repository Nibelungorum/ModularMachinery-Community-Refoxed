package cn.howxu.mmcr.api.machine;

import cn.howxu.mmcr.LevelStub;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.block.Blocks;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SuppressWarnings("deprecation")
class BlockArrayTest {

    @BeforeAll static void bootstrapMinecraft() throws Exception {
        Class<?> fmlLoaderCls = Class.forName("net.neoforged.fml.loading.FMLLoader");
        Class<?> distCls = Class.forName("net.neoforged.api.distmarker.Dist");
        Class<?> loadingModListCls = Class.forName("net.neoforged.fml.loading.LoadingModList");
        Constructor<?> fmlCtor = fmlLoaderCls.getDeclaredConstructor(
                ClassLoader.class, String[].class, distCls, boolean.class, Path.class);
        fmlCtor.setAccessible(true);
        Object client = distCls.getField("CLIENT").get(null);
        Object fmlLoader = fmlCtor.newInstance(
                Thread.currentThread().getContextClassLoader(),
                new String[0],
                client,
                false,
                Path.of("."));
        Constructor<?> lmlCtor = loadingModListCls.getDeclaredConstructor(
                List.class, List.class, List.class, List.class, java.util.Map.class);
        lmlCtor.setAccessible(true);
        Object emptyLoadingModList = lmlCtor.newInstance(
                List.of(), List.of(), List.of(), List.of(), java.util.Map.of());
        java.lang.reflect.Field loadingModListField = fmlLoaderCls.getDeclaredField("loadingModList");
        loadingModListField.setAccessible(true);
        loadingModListField.set(fmlLoader, emptyLoadingModList);
        Class<?> sharedConstantsCls = Class.forName("net.minecraft.SharedConstants");
        sharedConstantsCls.getMethod("tryDetectVersion").invoke(null);
        Bootstrap.bootStrap();
    }

    @Test void empty_has_zero_dims() {
        var arr = new BlockArray(Map.of());
        assertThat(arr.width()).isZero();
        assertThat(arr.height()).isZero();
        assertThat(arr.length()).isZero();
        assertThat(arr.isEmpty()).isTrue();
    }

    @Test void single_block_has_1x1x1_dims() {
        var arr = new BlockArray(Map.of(
                new BlockPos(0, 0, 0), new BlockPredicate.OfBlock(Blocks.STONE)));
        assertThat(arr.width()).isEqualTo(1);
        assertThat(arr.height()).isEqualTo(1);
        assertThat(arr.length()).isEqualTo(1);
        assertThat(arr.isEmpty()).isFalse();
    }

    @Test void multi_block_dims_correct() {
        Map<BlockPos, BlockPredicate> m = new HashMap<>();
        for (int x = 0; x < 3; x++)
            for (int y = 0; y < 2; y++)
                for (int z = 0; z < 4; z++)
                    m.put(new BlockPos(x, y, z), new BlockPredicate.OfBlock(Blocks.STONE));
        var arr = new BlockArray(m);
        assertThat(arr.width()).isEqualTo(3);
        assertThat(arr.height()).isEqualTo(2);
        assertThat(arr.length()).isEqualTo(4);
    }

    @Test void offset_positions_use_inclusive_extent() {
        var arr = new BlockArray(Map.of(
                new BlockPos(-2, 4, 10), new BlockPredicate.Any(),
                new BlockPos(1, 6, 14), new BlockPredicate.Any()));
        assertThat(arr.width()).isEqualTo(4);
        assertThat(arr.height()).isEqualTo(3);
        assertThat(arr.length()).isEqualTo(5);
    }

    @Test void get_returns_predicate_at_pos() {
        var pred = new BlockPredicate.OfBlock(Blocks.STONE);
        var arr = new BlockArray(Map.of(new BlockPos(0, 0, 0), pred));
        assertThat(arr.get(new BlockPos(0, 0, 0))).isEqualTo(pred);
        assertThat(arr.get(new BlockPos(1, 0, 0))).isNull();
    }

    @Test void matcher_matches_perfect_structure() {
        Map<BlockPos, BlockPredicate> m = new HashMap<>();
        for (int x = 0; x < 3; x++)
            for (int z = 0; z < 3; z++)
                m.put(new BlockPos(x, 0, z), new BlockPredicate.OfBlock(Blocks.STONE));
        var arr = new BlockArray(m);

        var level = LevelStub.create(Blocks.STONE, 3, 1, 3, new BlockPos(-1, 0, -1));
        assertThat(StructureMatcher.matches(arr, level, new BlockPos(1, 0, 1), Direction.NORTH)).isTrue();
    }

    @Test void matcher_rejects_wrong_block() {
        var arr = new BlockArray(Map.of(
                new BlockPos(0, 0, 0), new BlockPredicate.OfBlock(Blocks.STONE),
                new BlockPos(0, 0, 1), new BlockPredicate.OfBlock(Blocks.DIRT)));
        var level = LevelStub.create(Map.of(
                new BlockPos(0, 0, 0), Blocks.STONE,
                new BlockPos(0, 0, 1), Blocks.COBBLESTONE));

        assertThat(StructureMatcher.matches(arr, level, BlockPos.ZERO, Direction.SOUTH)).isFalse();
    }

    @Test void matcher_rotates_pattern_for_horizontal_facings() {
        var arr = new BlockArray(Map.of(
                new BlockPos(1, 0, 0), new BlockPredicate.OfBlock(Blocks.STONE),
                new BlockPos(0, 0, 1), new BlockPredicate.OfBlock(Blocks.DIRT)));
        var ctrl = new BlockPos(10, 2, 10);

        assertThat(StructureMatcher.matches(arr, LevelStub.create(Map.of(
                ctrl.offset(1, 0, 0), Blocks.STONE,
                ctrl.offset(0, 0, 1), Blocks.DIRT)), ctrl, Direction.SOUTH)).isTrue();
        assertThat(StructureMatcher.matches(arr, LevelStub.create(Map.of(
                ctrl.offset(-1, 0, 0), Blocks.STONE,
                ctrl.offset(0, 0, -1), Blocks.DIRT)), ctrl, Direction.NORTH)).isTrue();
        assertThat(StructureMatcher.matches(arr, LevelStub.create(Map.of(
                ctrl.offset(0, 0, -1), Blocks.STONE,
                ctrl.offset(1, 0, 0), Blocks.DIRT)), ctrl, Direction.EAST)).isTrue();
        assertThat(StructureMatcher.matches(arr, LevelStub.create(Map.of(
                ctrl.offset(0, 0, 1), Blocks.STONE,
                ctrl.offset(-1, 0, 0), Blocks.DIRT)), ctrl, Direction.WEST)).isTrue();
    }
}

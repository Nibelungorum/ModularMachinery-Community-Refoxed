package cn.howxu.mmcr.api.machine;

import net.minecraft.server.Bootstrap;
import cn.howxu.mmcr.test.TestBootstrap;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Blocks;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SuppressWarnings("deprecation")
class BlockPredicateTest {

    @BeforeAll static void bootstrapMinecraft() throws Exception {
        TestBootstrap.bootstrap();
        bindHolderTag(Blocks.DIRT.builtInRegistryHolder(), BlockTags.DIRT);
    }

    private static void bindHolderTag(net.minecraft.core.Holder<?> holder, net.minecraft.tags.TagKey<?> tag) throws Exception {
        java.lang.reflect.Method bindTags = Class.forName("net.minecraft.core.Holder$Reference")
                .getDeclaredMethod("bindTags", java.util.Collection.class);
        bindTags.setAccessible(true);
        bindTags.invoke(holder, java.util.Set.of(tag));
    }

    @Test void air_matches_only_air() {
        var p = new BlockPredicate.Air();
        assertThat(p.matches(Blocks.AIR.defaultBlockState())).isTrue();
        assertThat(p.matches(Blocks.STONE.defaultBlockState())).isFalse();
    }

    @Test void any_matches_anything() {
        var p = new BlockPredicate.Any();
        assertThat(p.matches(Blocks.AIR.defaultBlockState())).isTrue();
        assertThat(p.matches(Blocks.STONE.defaultBlockState())).isTrue();
    }

    @Test void ofBlock_matches_only_that_block() {
        var p = new BlockPredicate.OfBlock(Blocks.STONE);
        assertThat(p.matches(Blocks.STONE.defaultBlockState())).isTrue();
        assertThat(p.matches(Blocks.COBBLESTONE.defaultBlockState())).isFalse();
    }

    @Test void ofBlockState_matches_exact_state() {
        var s = Blocks.OAK_STAIRS.defaultBlockState();
        var p = new BlockPredicate.OfBlockState(s);
        assertThat(p.matches(s)).isTrue();
        assertThat(p.matches(Blocks.STONE.defaultBlockState())).isFalse();
    }

    @Test void ofTag_matches_any_in_tag() {
        var p = new BlockPredicate.OfTag(BlockTags.DIRT);
        assertThat(p.matches(Blocks.DIRT.defaultBlockState())).isTrue();
        assertThat(p.matches(Blocks.STONE.defaultBlockState())).isFalse();
    }

    @Test void anyOf_matches_if_any_child_matches() {
        var p = new BlockPredicate.AnyOf(List.of(
                new BlockPredicate.OfBlock(Blocks.STONE),
                new BlockPredicate.OfBlock(Blocks.DIRT)));
        assertThat(p.matches(Blocks.STONE.defaultBlockState())).isTrue();
        assertThat(p.matches(Blocks.AIR.defaultBlockState())).isFalse();
    }
}

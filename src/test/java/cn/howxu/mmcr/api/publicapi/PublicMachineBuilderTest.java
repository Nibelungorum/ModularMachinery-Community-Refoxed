package cn.howxu.mmcr.api.publicapi;

import cn.howxu.mmcr.test.TestBootstrap;
import cn.howxu.mmcr.api.publicapi.machine.BlockPredicate;
import cn.howxu.mmcr.api.publicapi.machine.PatternBuilder;
import cn.howxu.mmcr.api.publicapi.machine.PatternDefinition;
import cn.howxu.mmcr.internal.api.PublicMachineAdapter;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Blocks;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Verifies public startup machine builder value primitives.
 *
 * @author howxu &lt;dev@howxu.cn&gt;
 */
class PublicMachineBuilderTest {

    @BeforeAll static void bootstrapMinecraft() throws Exception {
        TestBootstrap.bootstrap();
    }

    @Test
    void pattern_builder_creates_immutable_3x3x1_pattern_with_empty_cells_bindings_and_controller() {
        BlockPredicate casing = BlockPredicate.block(Blocks.STONE);
        BlockPredicate controller = BlockPredicate.block(Blocks.FURNACE);

        PatternDefinition pattern = PatternBuilder.pattern()
                .layer("CCC", "C C", "CFC")
                .where('C', casing)
                .where('F', controller)
                .controller('F')
                .build();

        assertThat(pattern.width()).isEqualTo(3);
        assertThat(pattern.height()).isEqualTo(3);
        assertThat(pattern.depth()).isEqualTo(1);
        assertThat(pattern.layers()).containsExactly(List.of("CCC", "C C", "CFC"));
        assertThat(pattern.predicates()).containsExactlyInAnyOrderEntriesOf(Map.of('C', casing, 'F', controller));
        assertThat(pattern.controllerSymbol()).isEqualTo('F');

        assertThatThrownBy(() -> pattern.layers().add(List.of("XXX")))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> pattern.layers().getFirst().add("XXX"))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> pattern.predicates().put('X', casing))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void builder_rejects_invalid_pattern_declarations() {
        BlockPredicate casing = BlockPredicate.block(Blocks.STONE);
        BlockPredicate controller = BlockPredicate.block(Blocks.FURNACE);

        assertThatThrownBy(() -> PatternBuilder.pattern().layer("CC", "CCC"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("same width");
        assertThatThrownBy(() -> PatternBuilder.pattern().build())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("layer");
        assertThatThrownBy(() -> PatternBuilder.pattern()
                .layer("CC", "CF")
                .where('C', casing)
                .controller('F')
                .build())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Unbound");
        assertThatThrownBy(() -> PatternBuilder.pattern()
                .layer("CF")
                .where('C', casing)
                .where('F', controller)
                .controller('C')
                .controller('F'))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("controller");
        assertThatThrownBy(() -> PatternBuilder.pattern().controller('F').controller('F'))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("controller");
        assertThatThrownBy(() -> PatternBuilder.pattern().where('C', null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("predicate");
    }

    @Test
    void block_predicates_are_immutable_and_validate_inputs() {
        BlockPredicate stone = BlockPredicate.block(Blocks.STONE);
        BlockPredicate dirt = BlockPredicate.block(Blocks.DIRT);
        BlockPredicate any = BlockPredicate.any(stone, dirt);
        BlockPredicate tag = BlockPredicate.tag(BlockTags.DIRT);

        assertThat(stone).isNotNull();
        assertThat(any.alternatives()).containsExactly(stone, dirt);
        assertThat(tag.tag()).contains(BlockTags.DIRT);
        assertThat(any.tag()).isEmpty();
        assertThatThrownBy(() -> any.alternatives().add(stone))
                .isInstanceOf(UnsupportedOperationException.class);

        assertThatThrownBy(() -> BlockPredicate.block(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("block");
        assertThatThrownBy(() -> BlockPredicate.any())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("alternative");
        assertThatThrownBy(() -> BlockPredicate.anyOf(Arrays.asList(stone, null)))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("predicate");
    }

    @Test
    void pattern_definition_rejects_direct_invalid_construction() {
        BlockPredicate casing = BlockPredicate.block(Blocks.STONE);

        assertThatThrownBy(() -> new PatternDefinition(List.of(List.of("CC", "C")), Map.of('C', casing), 'C', 2, 2, 1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("width");
        assertThatThrownBy(() -> new PatternDefinition(List.of(List.of("CX")), Map.of('C', casing), 'C', 2, 1, 1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unbound");
        assertThatThrownBy(() -> new PatternDefinition(List.of(List.of("CC")), Map.of('C', casing), 'C', 2, 1, 1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("exactly one controller");
    }

    @Test
    void public_pattern_converts_to_internal_block_array_without_exposing_internal_types() {
        PatternDefinition pattern = PatternBuilder.pattern()
                .layer("CCC", "C C", "CFC")
                .where('C', BlockPredicate.any(BlockPredicate.block(Blocks.STONE), BlockPredicate.tag(BlockTags.DIRT)))
                .where('F', BlockPredicate.block(Blocks.FURNACE))
                .controller('F')
                .build();

        cn.howxu.mmcr.api.machine.BlockArray converted = PublicMachineAdapter.toBlockArray(pattern);

        assertThat(converted.get(BlockPos.ZERO))
                .isInstanceOf(cn.howxu.mmcr.api.machine.BlockPredicate.OfBlock.class)
                .extracting(predicate -> ((cn.howxu.mmcr.api.machine.BlockPredicate.OfBlock) predicate).block())
                .isEqualTo(Blocks.FURNACE);
        assertThat(converted.get(new BlockPos(0, -1, 0))).isNull();
        assertThat(converted.get(new BlockPos(-1, -2, 0)))
                .isInstanceOf(cn.howxu.mmcr.api.machine.BlockPredicate.AnyOf.class)
                .extracting(predicate -> ((cn.howxu.mmcr.api.machine.BlockPredicate.AnyOf) predicate).children())
                .asList()
                .hasSize(2);
    }
}

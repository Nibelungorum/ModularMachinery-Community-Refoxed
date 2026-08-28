package cn.howxu.mmcr.internal.tile;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.data.DataValue;
import cn.howxu.mmcr.registry.ModBlockEntities;
import cn.howxu.mmcr.registry.ModBlocks;
import cn.howxu.mmcr.test.TestBootstrap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/** Verifies data storage block persistence and ownership.
 * @author howxu <dev@howxu.cn>
 */
class DataStorageBlockEntityTest {
    private static final HolderLookup.Provider LOOKUP = HolderLookup.Provider.create(Stream.empty());

    @BeforeAll
    static void bootstrapMinecraft() throws Exception {
        TestBootstrap.bootstrap();
    }

    @Test
    void typed_values_round_trip_through_block_entity_persistence() {
        DataStorageBlockEntity source = create(new BlockPos(1, 0, 0));
        source.storage().set("decimal", DataValue.of(new BigDecimal("1.2300")));
        source.storage().set("enabled", DataValue.of(true));
        source.claimController(BlockPos.ZERO, MMCR.id("test_cube"));

        TagValueOutput output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, LOOKUP);
        source.saveAdditional(output);

        DataStorageBlockEntity restored = create(new BlockPos(1, 0, 0));
        restored.loadAdditional(TagValueInput.create(ProblemReporter.DISCARDING, LOOKUP, output.buildResult()));

        assertThat(restored.storage().get("decimal")).contains(DataValue.of(new BigDecimal("1.2300")));
        assertThat(restored.storage().get("decimal")).get().isEqualTo(DataValue.of(new BigDecimal("1.2300")));
        assertThat(restored.storage().get("enabled")).contains(DataValue.of(true));
        assertThat(restored.controllerPosition()).contains(BlockPos.ZERO);
    }

    @Test
    void storage_accepts_only_one_controller_owner() {
        DataStorageBlockEntity storage = create(new BlockPos(1, 0, 0));

        assertThat(storage.claimController(BlockPos.ZERO, MMCR.id("test_cube"))).isTrue();
        assertThat(storage.claimController(new BlockPos(10, 0, 0), MMCR.id("test_cube"))).isFalse();
        assertThat(storage.controllerPosition()).contains(BlockPos.ZERO);
        assertThat(storage.releaseController(BlockPos.ZERO)).isTrue();
        assertThat(storage.controllerPosition()).isEmpty();
    }

    private static DataStorageBlockEntity create(BlockPos pos) {
        BlockEntity entity = ModBlockEntities.DATA_STORAGE.get().create(pos,
                ModBlocks.DATA_STORAGE.get().defaultBlockState());
        assertThat(entity).isInstanceOf(DataStorageBlockEntity.class);
        return (DataStorageBlockEntity) entity;
    }
}

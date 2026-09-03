package cn.howxu.mmcr.internal.tile;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.data.DataValue;
import cn.howxu.mmcr.registry.ModBlockEntities;
import cn.howxu.mmcr.registry.ModBlocks;
import cn.howxu.mmcr.test.TestBootstrap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
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
    void recursive_values_round_trip_and_skip_malformed_nested_children() {
        DataStorageBlockEntity source = create(new BlockPos(1, 0, 0));
        DataValue mixedList = DataValue.list(List.of(
                DataValue.of("text"),
                DataValue.of(7),
                DataValue.map(Map.of("nested", DataValue.of(false)))
        ));
        source.storage().set("nested", DataValue.map(Map.of(
                "mixed", mixedList,
                "emptyList", DataValue.list(List.of()),
                "emptyMap", DataValue.map(Map.of())
        )));
        source.storage().set("legacy", DataValue.of(42));

        TagValueOutput output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, LOOKUP);
        source.saveAdditional(output);
        CompoundTag serialized = output.buildResult();
        ListTag values = serialized.getListOrEmpty("Values");
        CompoundTag malformed = new CompoundTag();
        malformed.putString("Key", "malformed");
        malformed.putString("Type", "MAP");
        ListTag malformedEntries = new ListTag();
        CompoundTag blankKeyChild = new CompoundTag();
        blankKeyChild.putString("Key", " ");
        blankKeyChild.putString("Type", "STRING");
        blankKeyChild.putString("Value", "ignored");
        malformedEntries.add(blankKeyChild);
        CompoundTag invalidTypeChild = new CompoundTag();
        invalidTypeChild.putString("Key", "invalidType");
        invalidTypeChild.putString("Type", "NOT_A_TYPE");
        malformedEntries.add(invalidTypeChild);
        CompoundTag invalidScalarChild = new CompoundTag();
        invalidScalarChild.putString("Key", "invalidScalar");
        invalidScalarChild.putString("Type", "BIG_INTEGER");
        invalidScalarChild.putString("Value", "not-a-number");
        malformedEntries.add(invalidScalarChild);
        CompoundTag validChild = new CompoundTag();
        validChild.putString("Key", "survivor");
        validChild.putString("Type", "STRING");
        validChild.putString("Value", "ok");
        malformedEntries.add(validChild);
        malformed.put("Entries", malformedEntries);
        values.add(malformed);

        DataStorageBlockEntity restored = create(new BlockPos(1, 0, 0));
        restored.loadAdditional(TagValueInput.create(ProblemReporter.DISCARDING, LOOKUP, serialized));

        assertThat(restored.storage().get("nested")).contains(DataValue.map(Map.of(
                "mixed", mixedList,
                "emptyList", DataValue.list(List.of()),
                "emptyMap", DataValue.map(Map.of())
        )));
        assertThat(restored.storage().get("legacy")).contains(DataValue.of(42));
        assertThat(restored.storage().get("malformed"))
                .contains(DataValue.map(Map.of("survivor", DataValue.of("ok"))));
    }

    @Test
    void malformed_scalar_values_are_skipped_while_valid_siblings_remain() {
        CompoundTag serialized = new CompoundTag();
        ListTag values = new ListTag();
        CompoundTag valid = new CompoundTag();
        valid.putString("Key", "valid");
        valid.putString("Type", "STRING");
        valid.putString("Value", "kept");
        values.add(valid);

        for (String type : List.of("BOOLEAN", "STRING", "INT", "LONG", "FLOAT", "DOUBLE")) {
            CompoundTag missing = new CompoundTag();
            missing.putString("Key", "missing" + type);
            missing.putString("Type", type);
            values.add(missing);

            CompoundTag wrongType = new CompoundTag();
            wrongType.putString("Key", "wrong" + type);
            wrongType.putString("Type", type);
            if (type.equals("STRING")) {
                wrongType.putInt("Value", 1);
            } else {
                wrongType.putString("Value", "not-a-" + type);
            }
            values.add(wrongType);
        }
        serialized.put("Values", values);

        DataStorageBlockEntity restored = create(new BlockPos(1, 0, 0));
        restored.loadAdditional(TagValueInput.create(ProblemReporter.DISCARDING, LOOKUP, serialized));

        assertThat(restored.storage().values()).containsOnlyKeys("valid");
        assertThat(restored.storage().get("valid")).contains(DataValue.of("kept"));
    }

    @Test
    void malformed_nested_collections_are_skipped_while_valid_siblings_remain() {
        CompoundTag serialized = new CompoundTag();
        ListTag values = new ListTag();
        CompoundTag valid = new CompoundTag();
        valid.putString("Key", "valid");
        valid.putString("Type", "STRING");
        valid.putString("Value", "kept");
        values.add(valid);

        CompoundTag missingList = new CompoundTag();
        missingList.putString("Key", "missingList");
        missingList.putString("Type", "LIST");
        values.add(missingList);

        CompoundTag wrongList = new CompoundTag();
        wrongList.putString("Key", "wrongList");
        wrongList.putString("Type", "LIST");
        wrongList.putString("Values", "not-a-list");
        values.add(wrongList);

        CompoundTag missingMap = new CompoundTag();
        missingMap.putString("Key", "missingMap");
        missingMap.putString("Type", "MAP");
        values.add(missingMap);

        CompoundTag wrongMap = new CompoundTag();
        wrongMap.putString("Key", "wrongMap");
        wrongMap.putString("Type", "MAP");
        wrongMap.putString("Entries", "not-a-list");
        values.add(wrongMap);
        serialized.put("Values", values);

        DataStorageBlockEntity restored = create(new BlockPos(1, 0, 0));
        restored.loadAdditional(TagValueInput.create(ProblemReporter.DISCARDING, LOOKUP, serialized));

        assertThat(restored.storage().values()).containsOnlyKeys("valid");
        assertThat(restored.storage().get("valid")).contains(DataValue.of("kept"));
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

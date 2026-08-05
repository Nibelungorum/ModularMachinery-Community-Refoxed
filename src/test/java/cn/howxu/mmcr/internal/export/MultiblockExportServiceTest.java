package cn.howxu.mmcr.internal.export;

import cn.howxu.mmcr.api.machine.BlockRotator;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MultiblockExportServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void normalizeOffsetRotatesBackToCapturedFace() {
        BlockPos worldOffset = new BlockPos(2, 1, -3);

        for (Direction face : Direction.Plane.HORIZONTAL) {
            BlockPos normalized = MultiblockExportService.normalizeOffset(worldOffset, face);

            assertThat(BlockRotator.rotateYCCWSouthUntil(normalized, face)).isEqualTo(worldOffset);
        }
    }

    @Test
    void renderJavaUsesLookupsAndReusesBlockVariables() {
        Identifier casing = Identifier.fromNamespaceAndPath("mmcr", "basic_casing");
        Identifier controller = Identifier.fromNamespaceAndPath("mmcr", "blast_furnace_controller");

        String java = MultiblockExportService.renderJava(List.of(
                new MultiblockExportService.SnapshotEntry(BlockPos.ZERO, controller, false),
                new MultiblockExportService.SnapshotEntry(new BlockPos(1, 0, 0), casing, false),
                new MultiblockExportService.SnapshotEntry(new BlockPos(2, 0, 0), casing, false),
                new MultiblockExportService.SnapshotEntry(new BlockPos(0, 1, 0), Identifier.fromNamespaceAndPath("minecraft", "air"), true)
        ), Direction.SOUTH);

        assertThat(java).contains("import cn.howxu.mmcr.api.machine.BlockArray;");
        assertThat(java).contains("import java.util.LinkedHashMap;");
        assertThat(java).contains("import java.util.Map;");
        assertThat(java).contains("Map<BlockPos, BlockPredicate> blocks = new LinkedHashMap<>();");
        assertThat(java).contains("Block blastFurnaceController = BuiltInRegistries.BLOCK.getValue(Identifier.parse(\"mmcr:blast_furnace_controller\")); // mmcr:blast_furnace_controller");
        assertThat(java).contains("Block basicCasing = BuiltInRegistries.BLOCK.getValue(Identifier.parse(\"mmcr:basic_casing\")); // mmcr:basic_casing");
        assertThat(java).contains("blocks.put(new BlockPos(0, 0, 0), new BlockPredicate.OfBlock(blastFurnaceController));");
        assertThat(java).contains("blocks.put(new BlockPos(1, 0, 0), new BlockPredicate.OfBlock(basicCasing));");
        assertThat(java).contains("blocks.put(new BlockPos(2, 0, 0), new BlockPredicate.OfBlock(basicCasing));");
        assertThat(java).doesNotContain("minecraft:air");
        assertThat(java).contains("BlockArray pattern = new BlockArray(Map.copyOf(blocks));");
    }

    @Test
    void nextExportPathIncrementsWhenNameExists() throws Exception {
        LocalDateTime timestamp = LocalDateTime.of(2026, 8, 5, 12, 30, 15);
        Path first = tempDir.resolve("2026-08-05-12-30-15-多方块导出-1.txt");
        Files.writeString(first, "existing");

        assertThat(MultiblockExportService.nextExportPath(tempDir, timestamp))
                .isEqualTo(tempDir.resolve("2026-08-05-12-30-15-多方块导出-2.txt"));
    }
}

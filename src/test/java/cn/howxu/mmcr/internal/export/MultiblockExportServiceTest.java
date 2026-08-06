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
    void normalizeOffsetRotatesBackToCapturedFaceForHorizontalFaces() {
        BlockPos worldOffset = new BlockPos(2, 1, -3);

        for (Direction face : Direction.Plane.HORIZONTAL) {
            BlockPos normalized = MultiblockExportService.normalizeOffset(worldOffset, face);

            assertThat(BlockRotator.rotateSouthTo(normalized, face)).isEqualTo(worldOffset);
        }
    }

    @Test
    void normalizeOffsetKeepsVerticalCapturesInWorldAxes() {
        BlockPos worldOffset = new BlockPos(2, 1, -3);

        assertThat(MultiblockExportService.normalizeOffset(worldOffset, Direction.UP)).isEqualTo(worldOffset);
        assertThat(MultiblockExportService.normalizeOffset(worldOffset, Direction.DOWN)).isEqualTo(worldOffset);
    }

    @Test
    void renderJavaUsesPatternBuilderAndInlineRegistryLookups() {
        Identifier casing = Identifier.fromNamespaceAndPath("mmcr", "basic_casing");
        Identifier controller = Identifier.fromNamespaceAndPath("mmcr", "blast_furnace_controller");

        String java = MultiblockExportService.renderJava(List.of(
                new MultiblockExportService.SnapshotEntry(BlockPos.ZERO, controller, false),
                new MultiblockExportService.SnapshotEntry(new BlockPos(-1, 0, -1), casing, false),
                new MultiblockExportService.SnapshotEntry(new BlockPos(1, 0, -1), casing, false),
                new MultiblockExportService.SnapshotEntry(new BlockPos(-1, 0, 1), casing, false),
                new MultiblockExportService.SnapshotEntry(new BlockPos(1, 0, 1), casing, false),
                new MultiblockExportService.SnapshotEntry(new BlockPos(0, 1, 0), Identifier.fromNamespaceAndPath("minecraft", "air"), true)
        ), Direction.SOUTH);

        assertThat(java).contains("import cn.howxu.mmcr.api.machine.BlockArray;");
        assertThat(java).contains("import cn.howxu.mmcr.api.machine.BlockPredicate;");
        assertThat(java).contains("import net.minecraft.core.registries.BuiltInRegistries;");
        assertThat(java).contains("import net.minecraft.resources.Identifier;");
        assertThat(java).doesNotContain("import net.minecraft.core.BlockPos;");
        assertThat(java).doesNotContain("import java.util.LinkedHashMap;");
        assertThat(java).doesNotContain("Map<BlockPos, BlockPredicate> blocks");
        assertThat(java).doesNotContain("blocks.put");
        assertThat(java).doesNotContain("Block basicCasing");
        assertThat(java).contains("BlockArray pattern = BlockArray.builder()");
        assertThat(java).contains(".pattern(\"X X\")");
        assertThat(java).contains(".pattern(\" C \")");
        assertThat(java).contains(".set('C', new BlockPredicate.OfBlock(BuiltInRegistries.BLOCK.getValue(Identifier.parse(\"mmcr:blast_furnace_controller\"))))");
        assertThat(java).contains(".set('X', new BlockPredicate.OfBlock(BuiltInRegistries.BLOCK.getValue(Identifier.parse(\"mmcr:basic_casing\"))))");
        assertThat(java).doesNotContain("minecraft:air");
        assertThat(java).contains(".build();");
    }

    @Test
    void renderJavaKeepsCurrentFormatForUpFacingCapture() {
        Identifier casing = Identifier.fromNamespaceAndPath("mmcr", "basic_casing");
        Identifier controller = Identifier.fromNamespaceAndPath("mmcr", "blast_furnace_controller");

        String java = MultiblockExportService.renderJava(List.of(
                new MultiblockExportService.SnapshotEntry(BlockPos.ZERO, controller, false),
                new MultiblockExportService.SnapshotEntry(
                        BlockRotator.rotateSouthTo(new BlockPos(0, 0, 1), Direction.UP), casing, false),
                new MultiblockExportService.SnapshotEntry(
                        BlockRotator.rotateSouthTo(new BlockPos(1, 0, 0), Direction.UP), casing, false)
        ), Direction.UP);

        assertThat(java).contains("BlockArray pattern = BlockArray.builder()");
        assertThat(java).contains(".pattern(\"CX\")");
        assertThat(java).contains(".pattern(\"X \")");
        assertThat(java).contains(".set('C', new BlockPredicate.OfBlock(BuiltInRegistries.BLOCK.getValue(Identifier.parse(\"mmcr:blast_furnace_controller\"))))");
        assertThat(java).contains(".set('X', new BlockPredicate.OfBlock(BuiltInRegistries.BLOCK.getValue(Identifier.parse(\"mmcr:basic_casing\"))))");
    }

    @Test
    void renderJavaExportsZSlicesInsteadOfYLayersForTallStructuresFacingUp() {
        assertTallStructureExport(Direction.UP);
    }

    @Test
    void renderJavaExportsZSlicesInsteadOfYLayersForTallStructuresFacingDown() {
        assertTallStructureExport(Direction.DOWN);
    }

    private static void assertTallStructureExport(Direction controllerFace) {
        Identifier a = Identifier.fromNamespaceAndPath("minecraft", "polished_andesite");
        Identifier x = Identifier.fromNamespaceAndPath("minecraft", "polished_diorite");
        Identifier b = Identifier.fromNamespaceAndPath("minecraft", "waxed_copper_block");
        Identifier d = Identifier.fromNamespaceAndPath("minecraft", "blue_ice");
        Identifier e = Identifier.fromNamespaceAndPath("minecraft", "crying_obsidian");

        String[] yLayers = {
                "AAA|AAA|AAA",
                "XBX|B B|XBX",
                "XBX|B B|XBX",
                "XDX|DED|XDX"
        };
        List<MultiblockExportService.SnapshotEntry> entries = new java.util.ArrayList<>();
        for (int y = 0; y < yLayers.length; y++) {
            String[] rows = yLayers[y].split("\\|");
            for (int z = 0; z < rows.length; z++) {
                for (int xi = 0; xi < rows[z].length(); xi++) {
                    char c = rows[z].charAt(xi);
                    if (c == ' ') continue;
                    Identifier id = switch (c) {
                        case 'A' -> a;
                        case 'X' -> x;
                        case 'B' -> b;
                        case 'D' -> d;
                        case 'E' -> e;
                        default -> throw new IllegalStateException("Unexpected symbol: " + c);
                    };
                    entries.add(new MultiblockExportService.SnapshotEntry(new BlockPos(xi - 1, y - 3, z - 1), id, false));
                }
            }
        }

        String java = MultiblockExportService.renderJava(entries, controllerFace);

        assertThat(java).contains(".pattern(\"AAA\", \"XBX\", \"XBX\", \"XDX\")");
        assertThat(java).contains(".pattern(\"AAA\", \"B B\", \"B B\", \"DED\")");
        assertThat(java).contains(".pattern(\"AAA\", \"XBX\", \"XBX\", \"XDX\")");
        assertThat(java).doesNotContain(".pattern(\"AAA\", \"AAA\", \"AAA\")");
    }

    @Test
    void renderJavaUsesBuiltinRegistryForVanillaBlocks() {
        String java = MultiblockExportService.renderJava(List.of(
                new MultiblockExportService.SnapshotEntry(BlockPos.ZERO, Identifier.fromNamespaceAndPath("minecraft", "stone"), false)
        ), Direction.SOUTH);

        assertThat(java).doesNotContain("import net.minecraft.world.level.block.Blocks;");
        assertThat(java).doesNotContain("Blocks.STONE");
        assertThat(java).contains(".set('X', new BlockPredicate.OfBlock(BuiltInRegistries.BLOCK.getValue(Identifier.parse(\"minecraft:stone\"))))");
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

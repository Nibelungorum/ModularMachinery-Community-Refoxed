package cn.howxu.mmcr.internal.export;

import cn.howxu.mmcr.api.machine.BlockRotator;
import cn.howxu.mmcr.api.machine.BlockPredicate;
import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.compat.kubejs.KubeJSApi;
import cn.howxu.mmcr.internal.block.MachineControllerBlock;
import cn.howxu.mmcr.registry.ModBlocks;
import cn.howxu.mmcr.test.TestBootstrap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.StairsShape;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;

import java.util.ArrayList;
import static org.assertj.core.api.Assertions.assertThat;

class MultiblockExportServiceTest {

    @BeforeAll
    static void bootstrap() throws Exception {
        TestBootstrap.bootstrap();
    }

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
    void normalizeOffsetRotatesBackToCapturedFaceForVerticalFaces() {
        BlockPos worldOffset = new BlockPos(2, 1, -3);

        assertThat(BlockRotator.rotateSouthTo(
                MultiblockExportService.normalizeOffset(worldOffset, Direction.UP), Direction.UP))
                .isEqualTo(worldOffset);
        assertThat(BlockRotator.rotateSouthTo(
                MultiblockExportService.normalizeOffset(worldOffset, Direction.DOWN), Direction.DOWN))
                .isEqualTo(worldOffset);
    }

    @Test
    void renderJavaUsesPublicApiFragment() {
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

        assertThat(java).contains(".pattern(p -> p");
        assertThat(java).contains(".layer(\"X X\")");
        assertThat(java).contains(".layer(\" C \")");
        assertThat(java).contains(".where('X', new BlockPredicate.OfBlock(BuiltInRegistries.BLOCK.getValue(Identifier.parse(\"mmcr:basic_casing\"))))");
        assertThat(java).doesNotContain("import ");
        assertThat(java).doesNotContain("BlockArray pattern");
        assertThat(java).doesNotContain(".build()");
        assertThat(java).doesNotContain(".where('C'");
        assertThat(java).doesNotContain(".controller(");
        assertThat(java).doesNotContain("minecraft:air");
    }

    @Test
    void renderersUseUnicodeSymbolsAfterAsciiSymbolsAreExhausted() {
        List<MultiblockExportService.SnapshotEntry> entries = new ArrayList<>();
        entries.add(new MultiblockExportService.SnapshotEntry(
                BlockPos.ZERO, Blocks.CRAFTING_TABLE.defaultBlockState(), false, true));
        BuiltInRegistries.BLOCK.entrySet().stream()
                .filter(entry -> entry.getValue() != Blocks.AIR)
                .limit(63)
                .forEach(entry -> entries.add(new MultiblockExportService.SnapshotEntry(
                        new BlockPos(entries.size(), 0, 0), entry.getValue().defaultBlockState(), false)));

        String java = MultiblockExportService.renderJava(entries, Direction.SOUTH);
        String kubeJs = MultiblockExportService.renderKubeJS(entries, Direction.SOUTH);

        assertThat(java).contains("'\u4e00'");
        assertThat(kubeJs).contains(".set('\u4e00'");
    }

    @Test
    void renderJavaSeparatesLayersAndPredicatesWithNewlines() {
        Identifier casing = Identifier.fromNamespaceAndPath("mmcr", "basic_casing");
        Identifier controller = Identifier.fromNamespaceAndPath("mmcr", "blast_furnace_controller");

        String java = MultiblockExportService.renderJava(List.of(
                new MultiblockExportService.SnapshotEntry(BlockPos.ZERO, controller, false, true),
                new MultiblockExportService.SnapshotEntry(new BlockPos(0, 0, 1), casing, false)
        ), Direction.SOUTH);

        assertThat(java).contains(".pattern(p -> p" + System.lineSeparator() + "        .layer(");
        assertThat(java).contains(".layer(\"C\")" + System.lineSeparator()
                + "        .layer(\"X\")" + System.lineSeparator()
                + "        .where(");
        assertThat(java).doesNotContain(")        .where(");
        assertThat(java).endsWith(".where('X', new BlockPredicate.OfBlock(BuiltInRegistries.BLOCK.getValue(Identifier.parse(\"mmcr:basic_casing\"))))"
                + System.lineSeparator() + ")" + System.lineSeparator());
    }

    @Test
    void renderersNormalizeDefaultStateAndShareSymbols() {
        var xAxisState = Blocks.OAK_LOG.defaultBlockState()
                .setValue(BlockStateProperties.AXIS, Direction.Axis.X);
        var zAxisState = Blocks.OAK_LOG.defaultBlockState()
                .setValue(BlockStateProperties.AXIS, Direction.Axis.Z);
        var defaultState = Blocks.OAK_LOG.defaultBlockState();
        List<MultiblockExportService.SnapshotEntry> entries = List.of(
                new MultiblockExportService.SnapshotEntry(BlockPos.ZERO, Blocks.CRAFTING_TABLE.defaultBlockState(), false, true),
                new MultiblockExportService.SnapshotEntry(new BlockPos(1, 0, 0), xAxisState, false),
                new MultiblockExportService.SnapshotEntry(new BlockPos(2, 0, 0), zAxisState, false),
                new MultiblockExportService.SnapshotEntry(new BlockPos(3, 0, 0), defaultState, false));

        String java = MultiblockExportService.renderJava(entries, Direction.SOUTH);
        String kubeJs = MultiblockExportService.renderKubeJS(entries, Direction.SOUTH);

        assertThat(java).contains(".layer(\"CXAB\")");
        assertThat(java).contains(".where('X', new BlockPredicate.OfBlockState")
                .contains(".where('A', new BlockPredicate.OfBlockState")
                .contains("getStateDefinition().getProperty(\"axis\")")
                .contains("getValue(\"x\")")
                .contains("getValue(\"z\")")
                .contains(".where('B', new BlockPredicate.OfBlock(BuiltInRegistries.BLOCK.getValue(Identifier.parse(\"minecraft:oak_log\"))))");
        assertThat(kubeJs).contains(".pattern(\"CXAB\")")
                .contains(".set('X', api.state('minecraft:oak_log[axis=x]'))")
                .contains(".set('A', api.state('minecraft:oak_log[axis=z]'))")
                .contains(".set('B', api.block('minecraft:oak_log'))");
        assertThat(java).contains(".where('X', new BlockPredicate.OfBlockState");
        assertThat(kubeJs).contains(".set('X', api.state('minecraft:oak_log[axis=x]'))");
    }

    @Test
    void renderersPreserveAllPropertiesForNonDefaultStairState() {
        var stairs = Blocks.OAK_STAIRS.defaultBlockState()
                .setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.WEST)
                .setValue(BlockStateProperties.HALF, Half.TOP)
                .setValue(BlockStateProperties.STAIRS_SHAPE, StairsShape.INNER_LEFT)
                .setValue(BlockStateProperties.WATERLOGGED, true);

        String java = MultiblockExportService.renderJava(List.of(
                new MultiblockExportService.SnapshotEntry(BlockPos.ZERO, stairs, false)), Direction.SOUTH);
        String kubeJs = MultiblockExportService.renderKubeJS(List.of(
                new MultiblockExportService.SnapshotEntry(BlockPos.ZERO, stairs, false)), Direction.SOUTH);

        assertThat(java).contains("getValue(\"west\")")
                .contains("getValue(\"top\")")
                .contains("getValue(\"inner_left\")")
                .contains("getValue(\"true\")");
        assertThat(kubeJs).contains("api.state('minecraft:oak_stairs[facing=west,half=top,shape=inner_left,waterlogged=true]')");
    }

    @Test
    void renderJavaSerializesArbitraryRuntimePropertyNamesAndValues() {
        var state = ModBlocks.controllerFor(MMCR.id("test_cube")).get().defaultBlockState()
                .setValue(MachineControllerBlock.FORMED, true);

        String java = MultiblockExportService.renderJava(List.of(
                new MultiblockExportService.SnapshotEntry(BlockPos.ZERO, Blocks.CRAFTING_TABLE.defaultBlockState(), false, true),
                new MultiblockExportService.SnapshotEntry(new BlockPos(1, 0, 0), state, false, false)), Direction.SOUTH);

        assertThat(java).contains("getStateDefinition().getProperty(\"formed\")")
                .contains("getValue(\"true\")")
                .doesNotContain("BlockStateProperties.FORMED");
    }

    @Test
    void renderKubeJsSerializesDefaultStateAsBlock() {
        String kubeJs = MultiblockExportService.renderKubeJS(List.of(
                new MultiblockExportService.SnapshotEntry(BlockPos.ZERO, Blocks.CRAFTING_TABLE.defaultBlockState(), false)),
                Direction.SOUTH);

        assertThat(kubeJs).contains(".set('X', api.block('minecraft:crafting_table'))")
                .doesNotContain("api.state('minecraft:crafting_table')")
                .doesNotContain("minecraft:crafting_table[]");
        assertThat(new KubeJSApi().state("minecraft:crafting_table"))
                .isEqualTo(new BlockPredicate.OfBlockState(Blocks.CRAFTING_TABLE.defaultBlockState()));
    }

    @Test
    void renderKubeJsUsesRuntimePropertySerializedNameForRegisteredState() {
        var state = Blocks.OAK_LOG.defaultBlockState().setValue(BlockStateProperties.AXIS, Direction.Axis.X);

        String kubeJs = MultiblockExportService.renderKubeJS(List.of(
                new MultiblockExportService.SnapshotEntry(BlockPos.ZERO, state, false)), Direction.SOUTH);

        assertThat(kubeJs).contains("api.state('minecraft:oak_log[axis=x]')")
                .doesNotContain("api.state('minecraft:oak_log[axis=X]')");
    }

    @Test
    void controllerSymbolUsesTheMarkedStateIdentity() {
        var controllerState = Blocks.OAK_LOG.defaultBlockState()
                .setValue(BlockStateProperties.AXIS, Direction.Axis.X);
        var otherState = Blocks.OAK_LOG.defaultBlockState()
                .setValue(BlockStateProperties.AXIS, Direction.Axis.Z);

        String java = MultiblockExportService.renderJava(List.of(
                new MultiblockExportService.SnapshotEntry(new BlockPos(-1, 0, 0), controllerState, false, true),
                new MultiblockExportService.SnapshotEntry(BlockPos.ZERO, otherState, false, false)),
                Direction.SOUTH);

        assertThat(java).contains(".where('X', new BlockPredicate.OfBlockState")
                .contains("getValue(\"z\")")
                .doesNotContain(".where('C'");
    }

    @Test
    void renderKubeJsUsesPatternAndSetsIncludingController() {
        Identifier casing = Identifier.fromNamespaceAndPath("mmcr", "basic_casing");
        Identifier controller = Identifier.fromNamespaceAndPath("mmcr", "blast_furnace_controller");

        String kubeJs = MultiblockExportService.renderKubeJS(List.of(
                new MultiblockExportService.SnapshotEntry(BlockPos.ZERO, controller, false),
                new MultiblockExportService.SnapshotEntry(new BlockPos(-1, 0, -1), casing, false),
                new MultiblockExportService.SnapshotEntry(new BlockPos(1, 0, -1), casing, false),
                new MultiblockExportService.SnapshotEntry(new BlockPos(-1, 0, 1), casing, false),
                new MultiblockExportService.SnapshotEntry(new BlockPos(1, 0, 1), casing, false),
                new MultiblockExportService.SnapshotEntry(new BlockPos(0, 1, 0), Identifier.fromNamespaceAndPath("minecraft", "air"), true)
        ), Direction.SOUTH);

        assertThat(kubeJs).contains(".pattern(\"X X\")");
        assertThat(kubeJs).contains(".pattern(\" C \")");
        assertThat(kubeJs).contains(".set('X', api.block('mmcr:basic_casing'))");
        assertThat(kubeJs).contains(".set('C', api.block('mmcr:blast_furnace_controller'))");
        assertThat(kubeJs).doesNotContain("BlockArray");
        assertThat(kubeJs).doesNotContain("import ");
        assertThat(kubeJs).doesNotContain(".layer(");
    }

    @Test
    void renderKubeJsKeepsEachZSliceInSeparatePatternCall() {
        Identifier controller = Identifier.fromNamespaceAndPath("mmcr", "blast_furnace_controller");
        Identifier casing = Identifier.fromNamespaceAndPath("mmcr", "basic_casing");

        String kubeJs = MultiblockExportService.renderKubeJS(List.of(
                new MultiblockExportService.SnapshotEntry(BlockPos.ZERO, controller, false),
                new MultiblockExportService.SnapshotEntry(new BlockPos(0, 0, 1), casing, false),
                new MultiblockExportService.SnapshotEntry(new BlockPos(1, 0, 1), casing, false)
        ), Direction.SOUTH);

        assertThat(kubeJs).contains(".pattern(\"C \")");
        assertThat(kubeJs).contains(".pattern(\"XX\")");
        assertThat(kubeJs.indexOf(".pattern(\"C \")"))
                .isLessThan(kubeJs.indexOf(".pattern(\"XX\")"));
        assertThat(kubeJs).doesNotContain(".pattern(\"C\", \"XX\")");
    }

    @Test
    void renderersUseMarkedControllerEvenWithNonStandardBlockId() {
        Identifier controller = Identifier.fromNamespaceAndPath("mmcr", "machine_core");
        Identifier casing = Identifier.fromNamespaceAndPath("mmcr", "basic_casing");
        List<MultiblockExportService.SnapshotEntry> entries = List.of(
                new MultiblockExportService.SnapshotEntry(BlockPos.ZERO, controller, false, true),
                new MultiblockExportService.SnapshotEntry(new BlockPos(1, 0, 0), casing, false)
        );

        String java = MultiblockExportService.renderJava(entries, Direction.SOUTH);
        String kubeJs = MultiblockExportService.renderKubeJS(entries, Direction.SOUTH);

        assertThat(java).doesNotContain(".where('C'");
        assertThat(kubeJs).contains(".set('C', api.block('mmcr:machine_core'))");
    }

    @Test
    void renderersAssignSymbolsDeterministicallyFromSortedEntries() {
        Identifier first = Identifier.fromNamespaceAndPath("test", "first_block");
        Identifier second = Identifier.fromNamespaceAndPath("test", "second_block");
        List<MultiblockExportService.SnapshotEntry> entries = List.of(
                new MultiblockExportService.SnapshotEntry(new BlockPos(0, 0, 0), first, false),
                new MultiblockExportService.SnapshotEntry(new BlockPos(1, 0, 0), second, false)
        );
        List<MultiblockExportService.SnapshotEntry> reversed = List.of(entries.get(1), entries.get(0));

        assertThat(MultiblockExportService.renderJava(entries, Direction.SOUTH))
                .isEqualTo(MultiblockExportService.renderJava(reversed, Direction.SOUTH));
        assertThat(MultiblockExportService.renderKubeJS(entries, Direction.SOUTH))
                .isEqualTo(MultiblockExportService.renderKubeJS(reversed, Direction.SOUTH));
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

        assertThat(java).contains(".layer(\"CX\")");
        assertThat(java).contains(".layer(\"X \")");
    }

    @Test
    void renderJavaExportsLyingFlatTemplateForUpFacingCapture() {
        assertTallStructureExport(Direction.UP, Direction.SOUTH);
    }

    @Test
    void renderJavaExportsLyingFlatTemplateForDownFacingCapture() {
        assertTallStructureExport(Direction.DOWN, Direction.SOUTH);
    }

    private static void assertTallStructureExport(Direction controllerFace, Direction rollFacing) {
        String[] rawSlices = {
                "AAA|AAA|AAA",
                "XBX|B B|XBX",
                "XDX|D D|XDX",
                "XEX|ECE|XEX"
        };
        List<MultiblockExportService.SnapshotEntry> entries = new ArrayList<>();
        for (int z = 0; z < rawSlices.length; z++) {
            String[] rows = rawSlices[z].split("\\|");
            for (int y = 0; y < rows.length; y++) {
                for (int x = 0; x < rows[y].length(); x++) {
                    char c = rows[y].charAt(x);
                    if (c == ' ') continue;
                    BlockPos raw = new BlockPos(x - 1, y - 1, z - 3);
                    if (c == 'C') raw = BlockPos.ZERO;
                    BlockPos world = BlockRotator.rotateSouthTo(raw, controllerFace, rollFacing);
                    entries.add(new MultiblockExportService.SnapshotEntry(world, idFor(c), false));
                }
            }
        }

        String java = MultiblockExportService.renderJava(entries, controllerFace, rollFacing);

        assertThat(java).contains(".layer(\"AAA\", \"AAA\", \"AAA\")");
        assertThat(java).contains(".layer(\"XBX\", \"B B\", \"XBX\")");
        assertThat(java).contains(".layer(\"XDX\", \"D D\", \"XDX\")");
        assertThat(java).contains(".layer(\"XEX\", \"ECE\", \"XEX\")");
        assertThat(java).doesNotContain(".layer(\"AAA\", \"XBX\", \"XDX\", \"XEX\")");
    }

    private static Identifier idFor(char c) {
        return switch (c) {
            case 'A' -> Identifier.fromNamespaceAndPath("minecraft", "polished_andesite");
            case 'X' -> Identifier.fromNamespaceAndPath("minecraft", "polished_diorite");
            case 'B' -> Identifier.fromNamespaceAndPath("minecraft", "waxed_copper_block");
            case 'D' -> Identifier.fromNamespaceAndPath("minecraft", "blue_ice");
            case 'E' -> Identifier.fromNamespaceAndPath("minecraft", "crying_obsidian");
            case 'C' -> Identifier.fromNamespaceAndPath("mmcr", "cracker_controller");
            default -> throw new IllegalStateException("Unexpected symbol: " + c);
        };
    }

    @Test
    void renderJavaUsesBuiltinRegistryForVanillaBlocks() {
        String java = MultiblockExportService.renderJava(List.of(
                new MultiblockExportService.SnapshotEntry(BlockPos.ZERO, Identifier.fromNamespaceAndPath("minecraft", "stone"), false)
        ), Direction.SOUTH);

        assertThat(java).doesNotContain("import net.minecraft.world.level.block.Blocks;");
        assertThat(java).doesNotContain("Blocks.STONE");
        assertThat(java).contains(".where('X', new BlockPredicate.OfBlock(BuiltInRegistries.BLOCK.getValue(Identifier.parse(\"minecraft:stone\"))))");
    }

    @Test
    void nextExportPathIncrementsWhenNameExists() throws Exception {
        LocalDateTime timestamp = LocalDateTime.of(2026, 8, 5, 12, 30, 15);
        Path exportDir = tempDir.resolve("mmcr_structure_export");
        Files.createDirectories(exportDir);
        Path first = exportDir.resolve("2026-08-05-12-30-15-多方块导出-1.txt");
        Files.writeString(first, "existing");

        assertThat(MultiblockExportService.nextExportPath(tempDir, timestamp))
                .isEqualTo(exportDir.resolve("2026-08-05-12-30-15-多方块导出-2.txt"));
    }
}

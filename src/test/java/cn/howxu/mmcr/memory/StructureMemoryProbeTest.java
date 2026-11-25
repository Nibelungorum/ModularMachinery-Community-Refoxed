package cn.howxu.mmcr.memory;

import cn.howxu.mmcr.api.machine.BlockArray;
import cn.howxu.mmcr.api.machine.BlockArrayCache;
import cn.howxu.mmcr.api.machine.BlockPredicate;
import cn.howxu.mmcr.api.machine.BlockRotator;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Measurement probe for the structure-memory plan.
 *
 * <p>Builds a fully solid synthetic pattern through the production {@link BlockArray#builder()} path,
 * which fills both the predicate table and the per-position symbol table, then materialises the three
 * rotated copies the way {@link BlockArrayCache} does at registration time. Deltas are reported per
 * entry so the lotus-rituals structure set (2,702,791 non-air cells) can be extrapolated.
 *
 * <p>Phase 1 invariant: a rotated facing must be materially cheaper than the south-facing original,
 * because the symbol table is now shared through a rotation-aware view instead of being copied.
 * Before Phase 1 the copy factor measured 2.95x for three copies; afterwards it must drop below 2.25x.
 */
class StructureMemoryProbeTest {

    private static final int SIZE = 48;
    private static final long ENTRIES = (long) SIZE * SIZE * SIZE;
    private static final long LOTUS_NON_AIR = 2_702_791L;
    private static final Direction[] ROTATIONS = {Direction.EAST, Direction.NORTH, Direction.WEST};

    private static long used() {
        Runtime runtime = Runtime.getRuntime();
        for (int i = 0; i < 6; i++) {
            System.gc();
            try {
                Thread.sleep(80L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        return runtime.totalMemory() - runtime.freeMemory();
    }

    private static String[] slice() {
        String row = "X".repeat(SIZE);
        String[] slice = new String[SIZE];
        for (int y = 0; y < SIZE; y++) {
            slice[y] = row;
        }
        slice[0] = "C" + "X".repeat(SIZE - 1);
        return slice;
    }

    private static BlockArray build() {
        BlockArray.Builder builder = BlockArray.builder();
        for (int z = 0; z < SIZE; z++) {
            builder.pattern(slice());
        }
        return builder
                .set('X', new BlockPredicate.Any())
                .set('C', new BlockPredicate.Any())
                .controller('C')
                .build();
    }

    /**
     * Pattern with the measured lotus profile: 379-wide rows, ~6 runs per row, ~15 solid cells per row
     * (2,702,791 solid cells over 255,505 rows). A uniform pattern understates the per-cell cost, so the
     * projection uses this density instead.
     */
    private static BlockArray buildLotusDensity() {
        int width = 379;
        int height = 32;
        int depth = 32;
        BlockArray.Builder builder = BlockArray.builder();
        int rowIndex = 0;
        for (int z = 0; z < depth; z++) {
            String[] slice = new String[height];
            for (int y = 0; y < height; y++) {
                char[] row = new char[width];
                java.util.Arrays.fill(row, ' ');
                for (int run = 0; run < 6; run++) {
                    // Ascending x, like a real row scan; mmcr's packer walks rows left to right.
                    int start = 4 + run * 62 + (rowIndex % 3);
                    int length = 2 + (run % 2);
                    for (int i = 0; i < length; i++) {
                        row[start + i] = (char) ('A' + run);
                    }
                }
                slice[y] = new String(row);
                rowIndex++;
            }
            builder.pattern(slice);
        }
        for (char symbol = 'A'; symbol <= 'F'; symbol++) {
            builder.set(symbol, new BlockPredicate.Any());
        }
        builder.set('C', new BlockPredicate.Any()); // A..F covers 'C' already; keep the controller bound
        builder.controller('C');
        return builder.build();
    }

    @Test
    void lotus_density_projection() throws IOException {
        long before = used();
        BlockArray base = buildLotusDensity();
        long after = used();

        long bytes = after - before;
        int cells = base.pattern().size();
        double perEntry = bytes / (double) cells;
        double projectedMb = perEntry * LOTUS_NON_AIR * 2 / 1048576.0;

        String report = """
                === lotus-density projection ===
                synthetic pattern        : 379 x 32 x 32, 6 runs/row, ~15 solid cells/row
                cells                    : %,d
                retained (predicates+symbols): %,.2f MB => %.2f B/entry
                projected for lotus      : %,.1f MB  (%,d solid cells, both tables)
                """.formatted(cells, bytes / 1048576.0, perEntry, projectedMb, LOTUS_NON_AIR);

        Path out = Path.of("build", "structure-memory-lotus-projection.txt");
        Files.createDirectories(out.getParent());
        Files.writeString(out, report, StandardCharsets.UTF_8);
        System.out.println(report);

        assertThat(cells).isPositive();
        assertThat(base.pattern().get(BlockPos.ZERO)).isNotNull().isInstanceOf(BlockPredicate.Any.class);
    }

    @Test
    void rotated_facings_share_the_symbol_table() throws IOException {
        long before = used();

        BlockArray base = build();
        long afterBase = used();

        List<BlockArray> rotated = new ArrayList<>(ROTATIONS.length);
        for (Direction facing : ROTATIONS) {
            rotated.add(BlockArrayCache.get(base, facing));
        }
        long afterRotated = used();

        long singleBytes = afterBase - before;
        long rotatedBytes = afterRotated - afterBase;
        double perEntry = singleBytes / (double) ENTRIES;
        double perEntryRotated = rotatedBytes / (double) (ENTRIES * ROTATIONS.length);
        double copyFactor = rotatedBytes / (double) Math.max(1L, singleBytes);
        double perFacingLotus = perEntry * LOTUS_NON_AIR / 1048576.0;
        double rotatedFacingLotus = perEntryRotated * LOTUS_NON_AIR / 1048576.0;
        double fourFacingLotus = (perEntry + 3 * perEntryRotated) * LOTUS_NON_AIR / 1048576.0;

        String report = """
                === structure memory probe ===
                synthetic pattern             : %d x %d x %d = %,d fully solid cells
                south-facing BlockArray       : %,.1f MB  => %.1f B/entry (predicates + symbols)
                three rotated facings         : %,.2f MB  => %.2f B/entry each (one view object)
                three-copy factor             : %.3fx   (2.95x before Phase 1, 1.49x after Phase 1)
                --- extrapolation to lotus (%,d non-air cells) ---
                south-facing                  : %,.0f MB
                each rotated facing           : %,.2f MB
                four facings                  : %,.0f MB   (1,912 MB before Phase 1)
                """.formatted(SIZE, SIZE, SIZE, ENTRIES,
                singleBytes / 1048576.0, perEntry,
                rotatedBytes / 1048576.0, perEntryRotated,
                copyFactor,
                LOTUS_NON_AIR, perFacingLotus, perFacingLotus * copyFactor / ROTATIONS.length, fourFacingLotus);

        Path out = Path.of("build", "structure-memory-probe.txt");
        Files.createDirectories(out.getParent());
        Files.writeString(out, report, StandardCharsets.UTF_8);
        System.out.println(report);

        assertThat(base.pattern()).hasSize((int) ENTRIES);
        assertThat(rotated).hasSize(ROTATIONS.length);
        assertThat(singleBytes).isPositive();
        // Phase 2: a rotated facing is a view over the shared tables, so three of them must cost less
        // than one materialised copy (a single full copy measured 1.0x, three measured 2.95x/1.49x).
        assertThat(copyFactor).isLessThan(1.0);
        // The views must still answer with the same content the south-facing table holds.
        for (Direction facing : ROTATIONS) {
            BlockArray view = BlockArrayCache.get(base, facing);
            assertThat(view.pattern()).hasSameSizeAs(base.pattern());
            BlockPos probe = new BlockPos(1, 1, 1);
            assertThat(view.get(BlockRotator.rotateSouthTo(probe, facing))).isEqualTo(base.get(probe));
            assertThat(view.pattern().get(BlockRotator.rotateSouthTo(probe, facing))).isEqualTo(base.get(probe));
        }
    }
}

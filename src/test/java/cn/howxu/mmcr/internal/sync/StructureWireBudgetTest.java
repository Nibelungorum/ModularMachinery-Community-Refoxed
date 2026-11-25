package cn.howxu.mmcr.internal.sync;

import cn.howxu.mmcr.api.machine.BlockArray;
import cn.howxu.mmcr.api.machine.BlockPredicate;
import cn.howxu.mmcr.api.machine.MachineStructureDefinition;
import cn.howxu.mmcr.test.TestBootstrap;
import com.mojang.serialization.Lifecycle;
import io.netty.buffer.Unpooled;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.registries.VanillaRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.item.enchantment.Enchantment;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Measures the encoded size of one structure declaration at lotus scale.
 *
 * <p>Vanilla rejects a single {@code minecraft:custom_payload} above 1 MiB, which is exactly the
 * {@code EncoderException: Failed to encode packet} a client reports on join. This test pins the number
 * so the chunking budget is derived from measurement instead of a guess.
 */
class StructureWireBudgetTest {

    private static final int MAX_PAYLOAD_BYTES = 1_048_576;
    /** Lotus's largest structure has 558,517 solid cells; 12k keeps the test fast and scales linearly. */
    private static final int ROWS = 32;
    private static final int RUNS_PER_ROW = 6;

    private static RegistryAccess registries;

    @BeforeAll
    static void bootstrap() throws Exception {
        TestBootstrap.bootstrap();
        MappedRegistry<Enchantment> enchantments = new MappedRegistry<>(Registries.ENCHANTMENT, Lifecycle.stable());
        VanillaRegistries.createLookup().lookupOrThrow(Registries.ENCHANTMENT).listElements()
                .forEach(holder -> Registry.register(enchantments, holder.key().location(), holder.value()));
        enchantments.freeze();
        List<Registry<?>> activeRegistries = new ArrayList<>();
        RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY).registries()
                .forEach(entry -> activeRegistries.add(entry.value()));
        activeRegistries.add(enchantments);
        registries = new RegistryAccess.ImmutableRegistryAccess(activeRegistries);
    }

    private static BlockArray lotusDensityPattern() {
        BlockArray.Builder builder = BlockArray.builder();
        int rowIndex = 0;
        for (int z = 0; z < ROWS; z++) {
            String[] slice = new String[ROWS];
            for (int y = 0; y < ROWS; y++) {
                char[] row = new char[379];
                java.util.Arrays.fill(row, ' ');
                for (int run = 0; run < RUNS_PER_ROW; run++) {
                    int start = 4 + run * 62;
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
        builder.noController();
        return builder.build();
    }

    @Test
    void one_lotus_scale_structure_exceeds_the_vanilla_payload_cap() throws Exception {
        BlockArray pattern = lotusDensityPattern();
        int cells = pattern.pattern().size();
        long runs = (long) ROWS * ROWS * RUNS_PER_ROW;

        var definition = new MachineStructureDefinition(
                cn.howxu.mmcr.MMCR.id("wire_budget"),
                java.util.List.of(MachineStructureDefinition.Declaration.full(pattern)));

        RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), registries);
        MachineStructureSyncCodec.encode(buf, definition);
        int encoded = buf.writerIndex();
        buf.release();

        double bytesPerCell = encoded / (double) cells;
        double bytesPerRun = encoded / (double) runs;
        double lotusOneMachine = bytesPerCell * 558_517;
        double lotusAllMachines = bytesPerCell * 2_702_791;

        String report = """
                === structure wire budget ===
                pattern cells            : %,d
                runs                     : %,d
                encoded structure bytes  : %,d  (%.2f B/cell, %.1f B/run)
                vanilla payload cap      : %,d
                --- projection ---
                largest lotus machine    : %,.0f bytes  (%,.0f KB)
                all eleven machines      : %,.0f bytes  (%,.0f KB)
                """.formatted(cells, runs, encoded, bytesPerCell, bytesPerRun, MAX_PAYLOAD_BYTES,
                lotusOneMachine, lotusOneMachine / 1024, lotusAllMachines, lotusAllMachines / 1024);

        Path out = Path.of("build", "structure-wire-budget.txt");
        Files.createDirectories(out.getParent());
        Files.writeString(out, report, StandardCharsets.UTF_8);
        System.out.println(report);

        assertThat(encoded).isPositive();
        // The whole point: one big machine already blows the cap, so the sync has to be chunked.
        assertThat(lotusOneMachine).isGreaterThan(MAX_PAYLOAD_BYTES);
    }
}

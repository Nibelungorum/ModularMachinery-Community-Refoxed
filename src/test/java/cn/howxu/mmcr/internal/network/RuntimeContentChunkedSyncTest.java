package cn.howxu.mmcr.internal.network;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.machine.BlockArray;
import cn.howxu.mmcr.api.machine.BlockPredicate;
import cn.howxu.mmcr.api.machine.MachineStructureDefinition;
import cn.howxu.mmcr.internal.sync.RuntimeContentSnapshot;
import cn.howxu.mmcr.test.TestBootstrap;
import com.mojang.serialization.Lifecycle;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.registries.VanillaRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.enchantment.Enchantment;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Replays the exact bytes a client receives for a large machine, end to end.
 *
 * <p>Regression guard for {@code EncoderException: Failed to encode packet
 * 'clientbound/minecraft:custom_payload'}: the pattern here is big enough that one unsliced payload would
 * exceed vanilla's 1 MiB cap, so this test fails if slicing regresses. Every packet the plan produces is
 * encoded through the real codec, decoded back through the real codec (which feeds the client buffer), and
 * the reconstructed structure is compared with the original.
 */
class RuntimeContentChunkedSyncTest {

    private static final int VANILLA_PAYLOAD_CAP = 1_048_576;
    private static final int SLICES = 96;
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

    @BeforeEach
    void clearBuffers() {
        ClientStructureChunkBuffer.reset();
    }

    /** Wide rows with several runs each, the shape a real ritual wall takes. */
    private static BlockArray bigPattern() {
        BlockArray.Builder builder = BlockArray.builder();
        for (int z = 0; z < SLICES; z++) {
            String[] slice = new String[SLICES];
            for (int y = 0; y < SLICES; y++) {
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
            }
            builder.pattern(slice);
        }
        for (char symbol = 'A'; symbol <= 'F'; symbol++) {
            builder.set(symbol, new BlockPredicate.Any());
        }
        builder.noController();
        return builder.build();
    }

    private static int encodeSize(RegistryFriendlyByteBuf buf) {
        int size = buf.writerIndex();
        buf.release();
        return size;
    }

    @Test
    void large_structure_is_sliced_and_reassembles_on_the_client() {
        ResourceLocation machineId = MMCR.id("chunked_big_machine");
        BlockArray pattern = bigPattern();
        MachineStructureDefinition definition = new MachineStructureDefinition(
                machineId, List.of(MachineStructureDefinition.Declaration.full(pattern)));
        RuntimeContentSnapshot snapshot = new RuntimeContentSnapshot(
                Map.of(machineId, definition), Map.of(), Map.of(), Map.of(),
                Map.of(machineId, List.of(machineId)), 42L);

        // An unsliced payload would have blown the cap; prove that precondition holds for this fixture.
        RegistryFriendlyByteBuf unsliced = new RegistryFriendlyByteBuf(Unpooled.buffer(), registries);
        cn.howxu.mmcr.internal.sync.MachineStructureSyncCodec.encode(unsliced, definition);
        assertThat(encodeSize(unsliced)).isGreaterThan(VANILLA_PAYLOAD_CAP);

        // A small slice size forces the multi-slice path; production uses 400 KB slices.
        RuntimeContentSync.StructureSyncPlan plan =
                RuntimeContentSync.planStructurePackets(snapshot, registries, 64_000);
        assertThat(plan.chunks()).hasSizeGreaterThan(1);
        assertThat(plan.metadata().chunkedStructureIds()).containsExactly(machineId);
        assertThat(plan.metadata().snapshot().structures()).isEmpty();

        // Every packet the server would send must fit the vanilla cap, encoded through the real codec.
        for (PktStructureChunkPayload chunk : plan.chunks()) {
            RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), registries);
            PktStructureChunkPayload.STREAM_CODEC.encode(buf, chunk);
            int size = buf.writerIndex();
            assertThat(size).isLessThan(VANILLA_PAYLOAD_CAP);
            buf.readerIndex(0);
            PktStructureChunkPayload decoded = PktStructureChunkPayload.STREAM_CODEC.decode(buf);
            buf.release();
            assertThat(decoded.machineId()).isEqualTo(chunk.machineId());
            assertThat(decoded.chunkIndex()).isEqualTo(chunk.chunkIndex());
            assertThat(decoded.data()).isEqualTo(chunk.data());
        }

        RegistryFriendlyByteBuf metadataBuf = new RegistryFriendlyByteBuf(Unpooled.buffer(), registries);
        PktRuntimeContentPayload.STREAM_CODEC.encode(metadataBuf, plan.metadata());
        assertThat(metadataBuf.writerIndex()).isLessThan(VANILLA_PAYLOAD_CAP);
        metadataBuf.readerIndex(0);
        PktRuntimeContentPayload metadata = PktRuntimeContentPayload.STREAM_CODEC.decode(metadataBuf);
        metadataBuf.release();
        assertThat(metadata.snapshot().contentVersion()).isEqualTo(42L);
        assertThat(metadata.chunkedStructureIds()).containsExactly(machineId);

        Map<ResourceLocation, MachineStructureDefinition> rebuilt = ClientStructureChunkBuffer.assemble(
                42L, metadata.chunkedStructureIds(),
                cn.howxu.mmcr.internal.sync.MachineStructureSyncCodec.maximumBlockPatternCount());

        assertThat(rebuilt).isNotNull();
        MachineStructureDefinition restored = rebuilt.get(machineId);
        assertThat(restored.machineId()).isEqualTo(machineId);
        assertThat(restored.declarations()).hasSize(1);
        assertThat(restored.declarations().getFirst().pattern().pattern().size())
                .isEqualTo(pattern.pattern().size());
        assertThat(restored.declarations().getFirst().pattern().get(new BlockPos(4, 0, 0)))
                .isEqualTo(pattern.get(new BlockPos(4, 0, 0)));
    }

    @Test
    void assemble_returns_null_until_every_slice_arrived() {
        ResourceLocation machineId = MMCR.id("chunked_partial");
        MachineStructureDefinition definition = new MachineStructureDefinition(
                machineId, List.of(MachineStructureDefinition.Declaration.full(bigPattern())));
        RuntimeContentSnapshot snapshot = new RuntimeContentSnapshot(
                Map.of(machineId, definition), Map.of(), Map.of(), Map.of(),
                Map.of(machineId, List.of(machineId)), 7L);

        RuntimeContentSync.StructureSyncPlan plan =
                RuntimeContentSync.planStructurePackets(snapshot, registries, 64_000);
        List<PktStructureChunkPayload> chunks = new ArrayList<>(plan.chunks());
        assertThat(chunks.size()).isGreaterThan(1);

        for (int index = 0; index < chunks.size() - 1; index++) {
            RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), registries);
            PktStructureChunkPayload.STREAM_CODEC.encode(buf, chunks.get(index));
            buf.readerIndex(0);
            PktStructureChunkPayload.STREAM_CODEC.decode(buf);
            buf.release();
        }

        assertThat(ClientStructureChunkBuffer.assemble(7L, List.of(machineId), 600_000)).isNull();

        PktStructureChunkPayload last = chunks.get(chunks.size() - 1);
        RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), registries);
        PktStructureChunkPayload.STREAM_CODEC.encode(buf, last);
        buf.readerIndex(0);
        PktStructureChunkPayload.STREAM_CODEC.decode(buf);
        buf.release();

        assertThat(ClientStructureChunkBuffer.assemble(7L, List.of(machineId), 600_000)).isNotNull();
    }

    @Test
    void stale_version_slices_are_discarded() {
        ResourceLocation machineId = MMCR.id("chunked_stale");
        var transfer = new StructureChunkAssembler.Transfer(1L);
        transfer.accept(machineId, 0, 1, new byte[]{1, 2, 3});

        ClientStructureChunkBuffer.reset();
        assertThat(ClientStructureChunkBuffer.assemble(2L, List.of(machineId), 600_000)).isNull();
    }

    /**
     * A structure that cannot be serialized (here: a symbol bound to a block from a mod that is not
     * installed) must be dropped on its own. Vanilla wraps any exception thrown during the join path as
     * "multiplayer.disconnect.invalid_player_data", so letting it escape would look like corrupt player data
     * and kick the client.
     */
    @Test
    void unserializable_structure_is_skipped_and_the_rest_syncs() {
        ResourceLocation good = MMCR.id("chunked_good_machine");
        ResourceLocation broken = MMCR.id("chunked_broken_machine");

        BlockArray.Builder brokenBuilder = BlockArray.builder();
        brokenBuilder.pattern("X");
        brokenBuilder.set('X', new BlockPredicate.DeferredBlock(() -> null));
        brokenBuilder.noController();
        MachineStructureDefinition brokenDefinition = new MachineStructureDefinition(
                broken, List.of(MachineStructureDefinition.Declaration.full(brokenBuilder.build())));

        MachineStructureDefinition goodDefinition = new MachineStructureDefinition(
                good, List.of(MachineStructureDefinition.Declaration.full(bigPattern())));

        RuntimeContentSnapshot snapshot = new RuntimeContentSnapshot(
                Map.of(good, goodDefinition, broken, brokenDefinition),
                Map.of(), Map.of(), Map.of(),
                Map.of(good, List.of(good), broken, List.of(broken)), 99L);

        RuntimeContentSync.StructureSyncPlan plan = RuntimeContentSync.planStructurePackets(snapshot, registries);

        assertThat(plan.metadata().chunkedStructureIds()).containsExactly(good);
        assertThat(plan.chunks()).isNotEmpty()
                .allSatisfy(chunk -> assertThat(chunk.machineId()).isEqualTo(good));
    }
}

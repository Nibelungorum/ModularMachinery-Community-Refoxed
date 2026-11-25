package cn.howxu.mmcr.internal.network;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.machine.BlockArray;
import cn.howxu.mmcr.api.machine.BlockPredicate;
import cn.howxu.mmcr.api.machine.MachineControllerSpec;
import cn.howxu.mmcr.api.machine.MachineStructureDefinition;
import cn.howxu.mmcr.internal.sync.RuntimeContentSnapshot;
import cn.howxu.mmcr.test.TestBootstrap;
import io.netty.buffer.Unpooled;
import io.netty.util.DefaultAttributeMap;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * @author HowXu {@literal <dev@howxu.cn>}
 */
class RuntimeContentChunkedSyncTest {
    private static final Identifier ID = MMCR.id("chunked_machine");
    private static RegistryAccess registries;

    @BeforeAll
    static void bootstrap() throws Exception {
        TestBootstrap.bootstrap();
        registries = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
    }

    private static MachineStructureDefinition definition(Identifier id, BlockPredicate wall) {
        var pattern = BlockArray.builder().noController().pattern("WWW. W").set('W', wall)
                .set('.', new BlockPredicate.Air()).build();
        return new MachineStructureDefinition(id, List.of(MachineStructureDefinition.Declaration.full(pattern)));
    }

    private static RuntimeContentSnapshot snapshot(long version) {
        return new RuntimeContentSnapshot(Map.of(ID, definition(ID, new BlockPredicate.Any())), Map.of(),
                Map.of(ID, MachineControllerSpec.defaultsFor(ID)), Map.of(), Map.of(ID, List.of(ID)), version);
    }

    private static <T> T roundTrip(StreamCodec<RegistryFriendlyByteBuf, T> codec, T value) {
        var buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), registries);
        try {
            codec.encode(buf, value);
            T decoded = codec.decode(buf);
            assertThat(buf.isReadable()).isFalse();
            return decoded;
        } finally {
            buf.release();
        }
    }

    @Test
    void reassemblesWholeStructureAndMetadataFromTheSameConnectionAndVersion() {
        var connection = new DefaultAttributeMap();
        var original = snapshot(2);
        var plan = RuntimeContentSync.planStructurePackets(original, registries, 12);
        assertThat(plan.metadata().snapshot().structures()).isEmpty();
        assertThat(plan.chunks()).hasSizeGreaterThan(1);
        var stale = RuntimeContentSync.planStructurePackets(snapshot(1), registries, 12);
        ClientStructureChunkBuffer.accept(connection, stale.chunks().getFirst());
        for (var chunk : plan.chunks()) {
            ClientStructureChunkBuffer.accept(connection, roundTrip(PktStructureChunkPayload.STREAM_CODEC, chunk));
        }
        var metadata = roundTrip(PktRuntimeContentPayload.STREAM_CODEC, plan.metadata());
        ClientStructureChunkBuffer.accept(connection, stale.chunks().getFirst());
        assertThat(ClientStructureChunkBuffer.finish(connection, stale.metadata(), registries)).isNull();
        assertThat(ClientStructureChunkBuffer.finish(connection, new PktRuntimeContentPayload(RuntimeContentSnapshot.empty()),
                registries)).isNull();
        assertThat(ClientStructureChunkBuffer.finish(new DefaultAttributeMap(), metadata, registries)).isNull();
        assertThat(ClientStructureChunkBuffer.finish(connection, metadata, registries)).isEqualTo(original);
        assertThat(ClientStructureChunkBuffer.finish(connection, metadata, registries)).isNull();
    }

    @Test
    void partialTransferIsDiscardedAndAFreshSnapshotCanBeApplied() {
        var connection = new DefaultAttributeMap();
        var original = snapshot(2);
        var plan = RuntimeContentSync.planStructurePackets(original, registries, 12);
        ClientStructureChunkBuffer.accept(connection, plan.chunks().getFirst());
        assertThat(ClientStructureChunkBuffer.finish(connection, plan.metadata(), registries)).isNull();
        plan.chunks().forEach(chunk -> ClientStructureChunkBuffer.accept(connection, chunk));
        assertThat(ClientStructureChunkBuffer.finish(connection, plan.metadata(), registries)).isEqualTo(original);
    }

    @Test
    void chunkedStructuresUseTheBlockBudgetTransmittedInMetadata() {
        var connection = new DefaultAttributeMap();
        var plan = RuntimeContentSync.planStructurePackets(snapshot(1), registries, 12);
        plan.chunks().forEach(chunk -> ClientStructureChunkBuffer.accept(connection, chunk));
        var metadata = roundTrip(PktRuntimeContentPayload.STREAM_CODEC,
                new PktRuntimeContentPayload(plan.metadata().snapshot(), List.of(ID), 2));
        assertThatThrownBy(() -> ClientStructureChunkBuffer.finish(connection, metadata, registries))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("block pattern count");
    }

    @Test
    void oneUnencodableMachineDoesNotPreventTheOthersFromSyncing() {
        var good = definition(ID, new BlockPredicate.Any());
        var brokenId = MMCR.id("missing_block");
        var broken = definition(brokenId, new BlockPredicate.DeferredBlock(() -> null));
        var source = new RuntimeContentSnapshot(Map.of(ID, good, brokenId, broken), Map.of(), Map.of(), Map.of(),
                Map.of(ID, List.of(ID), brokenId, List.of(brokenId)), 1);
        var connection = new DefaultAttributeMap();
        var plan = RuntimeContentSync.planStructurePackets(source, registries, PktStructureChunkPayload.MAX_CHUNK_BYTES);
        plan.chunks().forEach(chunk -> ClientStructureChunkBuffer.accept(connection, chunk));
        var restored = ClientStructureChunkBuffer.finish(connection, plan.metadata(), registries);
        assertThat(restored.structures()).containsOnlyKeys(ID).containsEntry(ID, good);
        assertThat(restored.machineRecipePools()).isEqualTo(source.machineRecipePools());
    }
}

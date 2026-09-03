package cn.howxu.mmcr.internal.tile;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.network.MachineReference;
import cn.howxu.mmcr.registry.ModBlockEntities;
import cn.howxu.mmcr.registry.ModBlocks;
import cn.howxu.mmcr.test.TestBootstrap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/** Verifies network-interface persistence and ownership boundaries.
 * @author howxu <dev@howxu.cn>
 */
class NetworkInterfaceBlockEntityTest {
    private static final HolderLookup.Provider LOOKUP = HolderLookup.Provider.create(Stream.empty());

    @BeforeAll
    static void bootstrapMinecraft() throws Exception {
        TestBootstrap.bootstrap();
    }

    @Test
    void owner_and_connections_round_trip_all_global_identity_fields_in_sequence_order() {
        NetworkInterfaceBlockEntity source = create(new BlockPos(8, 4, 2));
        GlobalPos owner = global("mmcr:owner_dimension", new BlockPos(1, 2, 3));
        GlobalPos firstEndpoint = global("mmcr:first_endpoint_dimension", new BlockPos(4, 5, 6));
        GlobalPos secondEndpoint = global("mmcr:second_endpoint_dimension", new BlockPos(-7, 8, 9));
        MachineReference firstMachine = new MachineReference(MMCR.id("first_machine"), 17L);
        MachineReference secondMachine = new MachineReference(MMCR.id("second_machine"), -23L);

        assertThat(source.claimOwner(owner)).isTrue();
        assertThat(source.addConnection(new NetworkInterfaceBlockEntity.Connection(firstEndpoint, firstMachine, 4L))).isTrue();
        assertThat(source.addConnection(new NetworkInterfaceBlockEntity.Connection(secondEndpoint, secondMachine, 9L))).isTrue();

        TagValueOutput output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, LOOKUP);
        source.saveAdditional(output);

        NetworkInterfaceBlockEntity restored = create(new BlockPos(8, 4, 2));
        restored.loadAdditional(TagValueInput.create(ProblemReporter.DISCARDING, LOOKUP, output.buildResult()));

        assertThat(restored.owner()).contains(owner);
        assertThat(restored.connections()).containsExactly(
                new NetworkInterfaceBlockEntity.Connection(firstEndpoint, firstMachine, 4L),
                new NetworkInterfaceBlockEntity.Connection(secondEndpoint, secondMachine, 9L));
    }

    @Test
    void malformed_connections_are_ignored_while_valid_siblings_load() {
        CompoundTag serialized = new CompoundTag();
        ListTag connections = new ListTag();
        connections.add(connection("mmcr:valid_dimension", new BlockPos(1, 2, 3), "mmcr:valid_machine", 31L, 2L));

        CompoundTag missingEndpointDimension = connection("", new BlockPos(4, 5, 6), "mmcr:missing_endpoint", 32L, 3L);
        connections.add(missingEndpointDimension);
        connections.add(connection("mmcr:invalid_machine_dimension", new BlockPos(7, 8, 9), "", 33L, 4L));
        connections.add(connection("mmcr:negative_sequence", new BlockPos(10, 11, 12), "mmcr:negative", 34L, -1L));
        serialized.put("connections", connections);

        NetworkInterfaceBlockEntity restored = create(BlockPos.ZERO);
        restored.loadAdditional(TagValueInput.create(ProblemReporter.DISCARDING, LOOKUP, serialized));

        assertThat(restored.connections()).containsExactly(
                new NetworkInterfaceBlockEntity.Connection(
                        global("mmcr:valid_dimension", new BlockPos(1, 2, 3)),
                        new MachineReference(MMCR.id("valid_machine"), 31L), 2L));
    }

    @Test
    void owner_accepts_only_an_unowned_interface_or_the_same_owner() {
        NetworkInterfaceBlockEntity networkInterface = create(BlockPos.ZERO);
        GlobalPos firstOwner = global("mmcr:owner", BlockPos.ZERO);
        GlobalPos secondOwner = global("mmcr:other_owner", BlockPos.ZERO);

        assertThat(networkInterface.claimOwner(firstOwner)).isTrue();
        assertThat(networkInterface.claimOwner(firstOwner)).isTrue();
        assertThat(networkInterface.claimOwner(secondOwner)).isFalse();
        assertThat(networkInterface.owner()).contains(firstOwner);
        assertThat(networkInterface.releaseOwner(secondOwner)).isFalse();
        assertThat(networkInterface.releaseOwner(firstOwner)).isTrue();
        assertThat(networkInterface.owner()).isEmpty();
    }

    @Test
    void a_newly_created_interface_starts_without_persisted_owner_or_connections() {
        NetworkInterfaceBlockEntity source = create(BlockPos.ZERO);
        assertThat(source.claimOwner(global("mmcr:old_owner", BlockPos.ZERO))).isTrue();
        assertThat(source.addConnection(new NetworkInterfaceBlockEntity.Connection(
                global("mmcr:old_endpoint", new BlockPos(1, 0, 0)),
                new MachineReference(MMCR.id("old_machine"), 1L), 0L))).isTrue();

        NetworkInterfaceBlockEntity recreated = create(BlockPos.ZERO);

        assertThat(recreated.owner()).isEmpty();
        assertThat(recreated.connections()).isEmpty();
    }

    @Test
    void block_entity_removal_cleans_up_loaded_peer_connections_before_it_is_discarded() {
        boolean[] removedFromPeers = {false};
        NetworkInterfaceBlockEntity networkInterface = new NetworkInterfaceBlockEntity(BlockPos.ZERO,
                ModBlocks.NETWORK_INTERFACE.get().defaultBlockState()) {
            @Override
            public void removeFromLoadedPeers() {
                removedFromPeers[0] = true;
            }
        };

        networkInterface.preRemoveSideEffects(BlockPos.ZERO, networkInterface.getBlockState());

        assertThat(removedFromPeers[0]).isTrue();
    }

    private static NetworkInterfaceBlockEntity create(BlockPos pos) {
        BlockEntity entity = ModBlockEntities.NETWORK_INTERFACE.get().create(pos,
                ModBlocks.NETWORK_INTERFACE.get().defaultBlockState());
        assertThat(entity).isInstanceOf(NetworkInterfaceBlockEntity.class);
        return (NetworkInterfaceBlockEntity) entity;
    }

    private static GlobalPos global(String dimension, BlockPos pos) {
        return GlobalPos.of(ResourceKey.create(Registries.DIMENSION, Identifier.parse(dimension)), pos);
    }

    private static CompoundTag connection(String dimension, BlockPos endpoint, String machine,
                                          long hash, long sequence) {
        CompoundTag value = new CompoundTag();
        CompoundTag endpointValue = new CompoundTag();
        if (!dimension.isEmpty()) endpointValue.putString("dimension", dimension);
        endpointValue.putInt("x", endpoint.getX());
        endpointValue.putInt("y", endpoint.getY());
        endpointValue.putInt("z", endpoint.getZ());
        value.put("endpoint", endpointValue);
        if (!machine.isEmpty()) value.putString("machine", machine);
        value.putLong("hash", hash);
        value.putLong("sequence", sequence);
        return value;
    }
}

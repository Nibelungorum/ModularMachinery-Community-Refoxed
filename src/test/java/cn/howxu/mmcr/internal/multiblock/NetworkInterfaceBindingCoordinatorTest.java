package cn.howxu.mmcr.internal.multiblock;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.machine.BlockArray;
import cn.howxu.mmcr.api.machine.Machine;
import cn.howxu.mmcr.api.machine.MachineControllerSpec;
import cn.howxu.mmcr.api.machine.MachinePatternCompiler;
import cn.howxu.mmcr.api.machine.NetworkInterfaceSpec;
import cn.howxu.mmcr.api.network.MachineReference;
import cn.howxu.mmcr.internal.tile.MachineControllerBlockEntity;
import cn.howxu.mmcr.internal.tile.MachineControllerRuntime;
import cn.howxu.mmcr.internal.tile.NetworkInterfaceBlockEntity;
import cn.howxu.mmcr.registry.ModBlockEntities;
import cn.howxu.mmcr.registry.ModBlocks;
import cn.howxu.mmcr.test.RuntimeTestFixtures;
import cn.howxu.mmcr.test.TestBootstrap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.dedicated.DedicatedServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/** Verifies network-interface connection coordination decisions.
 * @author howxu <dev@howxu.cn>
 */
class NetworkInterfaceBindingCoordinatorTest {

    @BeforeAll
    static void bootstrapMinecraft() throws Exception {
        TestBootstrap.bootstrap();
    }

    @Test
    void connection_write_rejects_an_existing_endpoint_machine_key_without_replacing_it() {
        NetworkInterfaceBlockEntity network = createInterface(BlockPos.ZERO);
        GlobalPos endpoint = global("mmcr_test:target", new BlockPos(1, 2, 3));
        MachineReference machine = new MachineReference(MMCR.id("target"), 5L);
        NetworkInterfaceBlockEntity.Connection original = new NetworkInterfaceBlockEntity.Connection(endpoint, machine, 1L);

        assertThat(network.addConnection(original)).isTrue();
        assertThat(network.addConnection(new NetworkInterfaceBlockEntity.Connection(endpoint, machine, 2L))).isFalse();
        assertThat(network.connections()).containsExactly(original);
    }

    @Test
    void connections_returns_cached_immutable_snapshots_until_topology_changes() {
        NetworkInterfaceBlockEntity network = createInterface(BlockPos.ZERO);
        var first = new NetworkInterfaceBlockEntity.Connection(
                global("minecraft:overworld", new BlockPos(1, 0, 0)),
                new MachineReference(MMCR.id("first"), 1L), 1L);
        var second = new NetworkInterfaceBlockEntity.Connection(
                global("minecraft:overworld", new BlockPos(2, 0, 0)),
                new MachineReference(MMCR.id("second"), 2L), 2L);

        assertThat(network.connections()).isEmpty();
        network.addConnection(first);
        List<NetworkInterfaceBlockEntity.Connection> afterFirst = network.connections();
        assertThat(network.connections()).isSameAs(afterFirst);

        network.addConnection(second);
        assertThat(afterFirst).containsExactly(first);
        assertThat(network.connections()).containsExactly(first, second);

        network.removeConnection(first);
        assertThat(network.connections()).containsExactly(second);
    }

    @Test
    void connect_creates_reciprocal_records_and_rejects_duplicates_on_either_endpoint() throws Exception {
        Fixture fixture = fixture(2, 2, true, true);

        assertThat(connect(fixture)).isEqualTo(NetworkInterfaceBindingCoordinator.ConnectionResult.CONNECTED);
        assertThat(fixture.sourceNetwork.connections()).hasSize(1);
        assertThat(fixture.targetNetwork.connections()).hasSize(1);
        assertThat(connect(fixture)).isEqualTo(NetworkInterfaceBindingCoordinator.ConnectionResult.DUPLICATE);

        Fixture residual = fixture(2, 2, true, true);
        NetworkInterfaceBlockEntity.Connection oldReverse = new NetworkInterfaceBlockEntity.Connection(
                residual.sourceEndpoint, residual.sourceMachine, 19L);
        assertThat(residual.targetNetwork.addConnection(oldReverse)).isTrue();

        assertThat(connect(residual)).isEqualTo(NetworkInterfaceBindingCoordinator.ConnectionResult.DUPLICATE);
        assertThat(residual.targetNetwork.connections()).containsExactly(oldReverse);
        assertThat(residual.sourceNetwork.connections()).isEmpty();
    }

    @Test
    void connect_requires_mutual_allowlists_formed_owned_active_endpoints_and_both_capacities() throws Exception {
        assertThat(connect(fixture(2, 2, false, true))).isEqualTo(NetworkInterfaceBindingCoordinator.ConnectionResult.ALLOWLIST_REJECTED);
        assertThat(connect(fixture(2, 2, true, false))).isEqualTo(NetworkInterfaceBindingCoordinator.ConnectionResult.ALLOWLIST_REJECTED);

        Fixture invalidSource = fixture(2, 2, true, true);
        assertThat(invalidSource.sourceNetwork.releaseOwner(invalidSource.sourceOwner)).isTrue();
        assertThat(connect(invalidSource)).isEqualTo(NetworkInterfaceBindingCoordinator.ConnectionResult.INVALID_SOURCE);

        Fixture inactiveSource = fixture(2, 2, true, true);
        setField(inactiveSource.sourceController, "activeNetworkInterfacePositions", Set.of());
        assertThat(connect(inactiveSource)).isEqualTo(NetworkInterfaceBindingCoordinator.ConnectionResult.INVALID_SOURCE);

        Fixture inactiveTarget = fixture(2, 2, true, true);
        setField(inactiveTarget.targetController, "activeNetworkInterfacePositions", Set.of());
        assertThat(connect(inactiveTarget)).isEqualTo(NetworkInterfaceBindingCoordinator.ConnectionResult.INVALID_TARGET);

        Fixture invalidTarget = fixture(2, 2, true, true);
        assertThat(invalidTarget.targetNetwork.releaseOwner(invalidTarget.targetOwner)).isTrue();
        assertThat(connect(invalidTarget)).isEqualTo(NetworkInterfaceBindingCoordinator.ConnectionResult.INVALID_TARGET);

        assertThat(connect(fixture(0, 1, true, true))).isEqualTo(NetworkInterfaceBindingCoordinator.ConnectionResult.SOURCE_CAPACITY);
        assertThat(connect(fixture(1, 0, true, true))).isEqualTo(NetworkInterfaceBindingCoordinator.ConnectionResult.TARGET_CAPACITY);
    }

    @Test
    void reconcile_keeps_the_oldest_connections_within_the_global_machine_limit() throws Exception {
        Fixture fixture = fixture(1, 2, true, true);
        MachineReference first = new MachineReference(MMCR.id("first"), 1L);
        MachineReference second = new MachineReference(MMCR.id("second"), 2L);
        assertThat(fixture.sourceNetwork.addConnection(new NetworkInterfaceBlockEntity.Connection(
                global("mmcr_test:missing", BlockPos.ZERO), second, 2L))).isTrue();
        assertThat(fixture.sourceNetwork.addConnection(new NetworkInterfaceBlockEntity.Connection(
                global("mmcr_test:missing", BlockPos.ZERO), first, 1L))).isTrue();

        NetworkInterfaceBindingCoordinator.reconcile(fixture.server, fixture.sourceController);

        assertThat(fixture.sourceNetwork.connections()).containsExactly(
                new NetworkInterfaceBlockEntity.Connection(global("mmcr_test:missing", BlockPos.ZERO), first, 1L));
    }

    @Test
    void connect_rolls_back_only_its_own_first_write_and_classifies_write_failures_by_endpoint() throws Exception {
        Fixture targetFailure = fixture(2, 2, true, true);
        targetFailure.replaceTarget(new NetworkInterfaceBlockEntity(targetFailure.targetEndpoint.pos(),
                ModBlocks.NETWORK_INTERFACE.get().defaultBlockState()) {
            @Override public boolean addConnection(Connection connection) {
                return false;
            }
        });

        assertThat(connect(targetFailure)).isEqualTo(NetworkInterfaceBindingCoordinator.ConnectionResult.INVALID_TARGET);
        assertThat(targetFailure.sourceNetwork.connections()).isEmpty();

        Fixture sourceFailure = fixture(2, 2, true, true);
        sourceFailure.replaceSource(new NetworkInterfaceBlockEntity(sourceFailure.sourceEndpoint.pos(),
                ModBlocks.NETWORK_INTERFACE.get().defaultBlockState()) {
            @Override public boolean addConnection(Connection connection) {
                return false;
            }
        });

        assertThat(connect(sourceFailure)).isEqualTo(NetworkInterfaceBindingCoordinator.ConnectionResult.INVALID_SOURCE);
        assertThat(sourceFailure.targetNetwork.connections()).isEmpty();
    }

    @Test
    void heartbeat_removes_loaded_one_sided_invalid_hash_and_capacity_records_but_keeps_unloaded_targets() throws Exception {
        Fixture oneSided = fixture(2, 2, true, true);
        assertThat(oneSided.sourceNetwork.addConnection(new NetworkInterfaceBlockEntity.Connection(
                oneSided.targetEndpoint, oneSided.targetMachine, 1L))).isTrue();
        heartbeat(oneSided);
        assertThat(oneSided.sourceNetwork.connections()).isEmpty();

        Fixture invalidHash = fixture(2, 2, true, true);
        MachineReference stale = new MachineReference(invalidHash.targetMachine.type(), invalidHash.targetMachine.hash() + 1L);
        assertThat(invalidHash.sourceNetwork.addConnection(new NetworkInterfaceBlockEntity.Connection(
                invalidHash.targetEndpoint, stale, 1L))).isTrue();
        assertThat(invalidHash.targetNetwork.addConnection(new NetworkInterfaceBlockEntity.Connection(
                invalidHash.sourceEndpoint, invalidHash.sourceMachine, 1L))).isTrue();
        heartbeat(invalidHash);
        assertThat(invalidHash.sourceNetwork.connections()).isEmpty();
        assertThat(invalidHash.targetNetwork.connections()).isEmpty();

        Fixture capacityInvalid = fixture(2, 2, true, true);
        assertThat(connect(capacityInvalid)).isEqualTo(NetworkInterfaceBindingCoordinator.ConnectionResult.CONNECTED);
        publishFormed(capacityInvalid.targetController, machine(MMCR.id("target"), 0, Set.of(MMCR.id("source"))));
        heartbeat(capacityInvalid);
        assertThat(capacityInvalid.sourceNetwork.connections()).isEmpty();
        assertThat(capacityInvalid.targetNetwork.connections()).isEmpty();

        Fixture sourceCapacityInvalid = fixture(2, 2, true, true);
        assertThat(connect(sourceCapacityInvalid)).isEqualTo(NetworkInterfaceBindingCoordinator.ConnectionResult.CONNECTED);
        publishFormed(sourceCapacityInvalid.sourceController, machine(MMCR.id("source"), 0, Set.of(MMCR.id("target"))));
        heartbeat(sourceCapacityInvalid);
        assertThat(sourceCapacityInvalid.sourceNetwork.connections()).isEmpty();
        assertThat(sourceCapacityInvalid.targetNetwork.connections()).isEmpty();

        Fixture unloaded = fixture(2, 2, true, true);
        assertThat(connect(unloaded)).isEqualTo(NetworkInterfaceBindingCoordinator.ConnectionResult.CONNECTED);
        unloaded.level.unload(unloaded.targetEndpoint.pos());
        heartbeat(unloaded);
        assertThat(unloaded.sourceNetwork.connections()).hasSize(1);
    }

    @Test
    void heartbeat_removes_reciprocal_records_when_the_loaded_source_has_a_stale_owner() throws Exception {
        Fixture fixture = fixture(2, 2, true, true);
        assertThat(connect(fixture)).isEqualTo(NetworkInterfaceBindingCoordinator.ConnectionResult.CONNECTED);
        setField(fixture.sourceNetwork, "owner", fixture.targetOwner);

        heartbeat(fixture);

        assertThat(fixture.sourceNetwork.connections()).isEmpty();
        assertThat(fixture.targetNetwork.connections()).isEmpty();
    }

    private static NetworkInterfaceBlockEntity createInterface(BlockPos pos) {
        BlockEntity entity = ModBlockEntities.NETWORK_INTERFACE.get().create(pos,
                ModBlocks.NETWORK_INTERFACE.get().defaultBlockState());
        assertThat(entity).isInstanceOf(NetworkInterfaceBlockEntity.class);
        return (NetworkInterfaceBlockEntity) entity;
    }

    private static GlobalPos global(String dimension, BlockPos pos) {
        return GlobalPos.of(ResourceKey.create(Registries.DIMENSION, Identifier.parse(dimension)), pos);
    }

    private static NetworkInterfaceBindingCoordinator.ConnectionResult connect(Fixture fixture) {
        return NetworkInterfaceBindingCoordinator.connect(fixture.server, fixture.sourceEndpoint, fixture.sourceMachine,
                fixture.targetEndpoint, fixture.targetMachine);
    }

    private static void heartbeat(Fixture fixture) {
        StructureClaimRegistry.get(fixture.level).claim(fixture.sourceController.getBlockPos(), List.of());
        NetworkInterfaceBindingCoordinator.heartbeat(fixture.level);
    }

    private static Fixture fixture(int sourceCapacity, int targetCapacity, boolean sourceAllowsTarget,
                                   boolean targetAllowsSource) throws Exception {
        TestServerLevel level = allocate(TestServerLevel.class);
        level.blockEntities = new HashMap<>();
        level.blocks = new HashMap<>();
        level.unloaded = new java.util.HashSet<>();
        setField(Level.class, level, "dimension", Level.OVERWORLD);
        setField(ServerLevel.class, level, "players", List.of());
        MinecraftServer server = allocate(DedicatedServer.class);
        setField(MinecraftServer.class, server, "levels", Map.of(Level.OVERWORLD, level));
        level.server = server;

        BlockPos sourceControllerPos = new BlockPos(0, 64, 0);
        BlockPos sourceInterfacePos = new BlockPos(1, 64, 0);
        BlockPos targetControllerPos = new BlockPos(32, 64, 0);
        BlockPos targetInterfacePos = new BlockPos(33, 64, 0);
        Machine source = machine(MMCR.id("source"), sourceCapacity,
                sourceAllowsTarget ? Set.of(MMCR.id("target")) : Set.of());
        Machine target = machine(MMCR.id("target"), targetCapacity,
                targetAllowsSource ? Set.of(MMCR.id("source")) : Set.of());
        MachineControllerBlockEntity sourceController = controller(sourceControllerPos, source, level, sourceInterfacePos);
        MachineControllerBlockEntity targetController = controller(targetControllerPos, target, level, targetInterfacePos);
        NetworkInterfaceBlockEntity sourceNetwork = createInterface(sourceInterfacePos);
        NetworkInterfaceBlockEntity targetNetwork = createInterface(targetInterfacePos);
        sourceNetwork.setLevel(level);
        targetNetwork.setLevel(level);
        level.blockEntities.put(sourceInterfacePos, sourceNetwork);
        level.blockEntities.put(targetInterfacePos, targetNetwork);
        GlobalPos sourceOwner = global("minecraft:overworld", sourceControllerPos);
        GlobalPos targetOwner = global("minecraft:overworld", targetControllerPos);
        assertThat(sourceNetwork.claimOwner(sourceOwner)).isTrue();
        assertThat(targetNetwork.claimOwner(targetOwner)).isTrue();
        return new Fixture(server, level, sourceController, targetController, sourceNetwork, targetNetwork,
                GlobalPos.of(Level.OVERWORLD, sourceInterfacePos), GlobalPos.of(Level.OVERWORLD, targetInterfacePos),
                sourceOwner, targetOwner, sourceController.machineReference(), targetController.machineReference());
    }

    private static MachineControllerBlockEntity controller(BlockPos pos, Machine machine, TestServerLevel level,
                                                            BlockPos interfacePos) throws Exception {
        MachineControllerBlockEntity controller = RuntimeTestFixtures.controllerEntity(MMCR.id("test_cube"), pos);
        controller.setLevel(level);
        level.blocks.put(pos, controller.getBlockState());
        publishFormed(controller, machine);
        setField(controller, "activeNetworkInterfacePositions", Set.of(interfacePos));
        level.blockEntities.put(pos, controller);
        return controller;
    }

    private static void publishFormed(MachineControllerBlockEntity controller, Machine machine) throws Exception {
        Field runtimeField = MachineControllerBlockEntity.class.getDeclaredField("runtime");
        runtimeField.setAccessible(true);
        MachineControllerRuntime runtime = (MachineControllerRuntime) runtimeField.get(controller);
        Method publishFormationState = MachineControllerRuntime.class.getDeclaredMethod("publishFormationState",
                Machine.class, BlockArray.class, cn.howxu.mmcr.api.machine.CompiledMachinePattern.class,
                Direction.class, Direction.class, int.class);
        publishFormationState.setAccessible(true);
        publishFormationState.invoke(runtime, machine, machine.pattern(), MachinePatternCompiler.compile(machine),
                Direction.SOUTH, Direction.NORTH, 1);
    }

    private static Machine machine(Identifier id, int maxConnections, Set<Identifier> allowedMachines) {
        return new Machine() {
            @Override public Identifier registryName() { return id; }
            @Override public BlockArray pattern() { return new BlockArray(Map.of()); }
            @Override public MachineControllerSpec controller() { return MachineControllerSpec.defaultsFor(id); }
            @Override public NetworkInterfaceSpec networkInterface() {
                return new NetworkInterfaceSpec(1, maxConnections, allowedMachines);
            }
        };
    }

    @SuppressWarnings("unchecked")
    private static <T> T allocate(Class<T> type) throws Exception {
        Field field = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        return (T) ((sun.misc.Unsafe) field.get(null)).allocateInstance(type);
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        for (Class<?> type = target.getClass(); type != null; type = type.getSuperclass()) {
            try {
                setField(type, target, name, value);
                return;
            } catch (NoSuchFieldException ignored) {
                // Continue through the inheritance hierarchy.
            }
        }
        throw new NoSuchFieldException(name);
    }

    private static void setField(Class<?> type, Object target, String name, Object value) throws Exception {
        Field field = type.getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static final class Fixture {
        private final MinecraftServer server;
        private final TestServerLevel level;
        private final MachineControllerBlockEntity sourceController;
        private final MachineControllerBlockEntity targetController;
        private NetworkInterfaceBlockEntity sourceNetwork;
        private NetworkInterfaceBlockEntity targetNetwork;
        private final GlobalPos sourceEndpoint;
        private final GlobalPos targetEndpoint;
        private final GlobalPos sourceOwner;
        private final GlobalPos targetOwner;
        private final MachineReference sourceMachine;
        private final MachineReference targetMachine;

        private Fixture(MinecraftServer server, TestServerLevel level, MachineControllerBlockEntity sourceController,
                        MachineControllerBlockEntity targetController, NetworkInterfaceBlockEntity sourceNetwork,
                        NetworkInterfaceBlockEntity targetNetwork, GlobalPos sourceEndpoint, GlobalPos targetEndpoint,
                        GlobalPos sourceOwner, GlobalPos targetOwner, MachineReference sourceMachine,
                        MachineReference targetMachine) {
            this.server = server;
            this.level = level;
            this.sourceController = sourceController;
            this.targetController = targetController;
            this.sourceNetwork = sourceNetwork;
            this.targetNetwork = targetNetwork;
            this.sourceEndpoint = sourceEndpoint;
            this.targetEndpoint = targetEndpoint;
            this.sourceOwner = sourceOwner;
            this.targetOwner = targetOwner;
            this.sourceMachine = sourceMachine;
            this.targetMachine = targetMachine;
        }

        void replaceSource(NetworkInterfaceBlockEntity replacement) {
            replacement.setLevel(level);
            replacement.claimOwner(sourceOwner);
            level.blockEntities.put(sourceEndpoint.pos(), replacement);
            sourceNetwork = replacement;
        }

        void replaceTarget(NetworkInterfaceBlockEntity replacement) {
            replacement.setLevel(level);
            replacement.claimOwner(targetOwner);
            level.blockEntities.put(targetEndpoint.pos(), replacement);
            targetNetwork = replacement;
        }
    }

    private static class TestServerLevel extends ServerLevel {
        private Map<BlockPos, BlockEntity> blockEntities;
        private Map<BlockPos, BlockState> blocks;
        private Set<Long> unloaded;
        private MinecraftServer server;

        private TestServerLevel() {
            super(null, null, null, null, Level.OVERWORLD, null, false, 0L, List.of(), false);
        }

        @Override public MinecraftServer getServer() { return server; }
        @Override public BlockEntity getBlockEntity(BlockPos pos) { return blockEntities.get(pos); }
        @Override public boolean hasChunk(int chunkX, int chunkZ) {
            return !unloaded.contains(BlockPos.asLong(chunkX << 4, 0, chunkZ << 4));
        }
        void unload(BlockPos pos) {
            unloaded.add(BlockPos.asLong(pos.getX() >> 4 << 4, 0, pos.getZ() >> 4 << 4));
        }
        @Override public BlockState getBlockState(BlockPos pos) { return blocks.getOrDefault(pos, Blocks.AIR.defaultBlockState()); }
        @Override public void blockEntityChanged(BlockPos pos) { }
        @Override public void sendBlockUpdated(BlockPos pos, BlockState oldState, BlockState newState, int flags) { }
    }
}

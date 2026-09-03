package cn.howxu.mmcr.network;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.data.DataStorage;
import cn.howxu.mmcr.api.machine.BlockArray;
import cn.howxu.mmcr.api.machine.BlockPredicate;
import cn.howxu.mmcr.api.machine.Machine;
import cn.howxu.mmcr.api.machine.MachineRegistry;
import cn.howxu.mmcr.api.machine.MachineAppearanceSpec;
import cn.howxu.mmcr.api.machine.MachineControllerSpec;
import cn.howxu.mmcr.api.machine.NetworkInterfaceSpec;
import cn.howxu.mmcr.api.network.KeyCardBinding;
import cn.howxu.mmcr.api.network.MachineReference;
import cn.howxu.mmcr.api.network.NetworkApi;
import cn.howxu.mmcr.api.network.RequestBody;
import cn.howxu.mmcr.api.network.RequestProcess;
import cn.howxu.mmcr.internal.multiblock.NetworkInterfaceBindingCoordinator;
import cn.howxu.mmcr.internal.tile.MachineControllerBlockEntity;
import cn.howxu.mmcr.internal.tile.NetworkInterfaceBlockEntity;
import cn.howxu.mmcr.registry.ModBlocks;
import cn.howxu.mmcr.registry.ModDataComponents;
import cn.howxu.mmcr.registry.ModItems;
import com.mojang.authlib.GameProfile;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestInstance;
import net.minecraft.gametest.framework.TestData;
import net.minecraft.gametest.framework.TestEnvironmentDefinition;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/** Covers formed network-interface structures and their server lifecycle.
 * @author howxu <dev@howxu.cn>
 */
public final class NetworkInterfaceGameTests {
    private static final Identifier KEY_SOURCE_ID = MMCR.id("network_key_source");
    private static final Identifier KEY_TARGET_ID = MMCR.id("network_key_target");
    private static final Identifier MULTI_SOURCE_ID = MMCR.id("network_multi_source");
    private static final Identifier MULTI_TARGET_ID = MMCR.id("network_multi_target");
    private static final Identifier CAPACITY_SOURCE_ID = MMCR.id("network_capacity_source");
    private static final Identifier CAPACITY_TARGET_ID = MMCR.id("network_capacity_target");
    private static final Identifier CAPACITY_BAD_ID = MMCR.id("network_capacity_bad");
    private static final Identifier REPLACEMENT_SOURCE_ID = MMCR.id("network_replacement_source");
    private static final Identifier REPLACEMENT_TARGET_ID = MMCR.id("network_replacement_target");
    private static final Identifier REQUEST_SOURCE_ID = MMCR.id("network_request_source");
    private static final Identifier REQUEST_TARGET_ID = MMCR.id("network_request_target");
    private static final Identifier NULL_REQUEST_SOURCE_ID = MMCR.id("network_null_request_source");
    private static final Identifier NULL_REQUEST_TARGET_ID = MMCR.id("network_null_request_target");
    private static final Identifier SAME_SOURCE_ID = MMCR.id("network_same_source");
    private static final Identifier SAME_TARGET_ID = MMCR.id("network_same_target");
    private static final Identifier REQUEST_ID = MMCR.id("network_test_request");
    private static final Identifier FORMED_TEXTURE = MMCR.id("block/network_formed_casing");

    private NetworkInterfaceGameTests() {
    }

    public static void registerAll(RegisterGameTestsEvent event) {
        register(event, "network_interface_formed_max_count", 100,
                NetworkInterfaceGameTests::formedStructureClaimsSortedInterfacesAndCleansExcludedInterfaces);
        register(event, "network_interface_key_card_duplicate", 120,
                NetworkInterfaceGameTests::keyCardBindsFormedMachinesAndReportsDuplicate);
        register(event, "network_interface_key_card_multi_target", 120,
                NetworkInterfaceGameTests::keyCardBindsMultipleTargets);
        register(event, "network_interface_allowlist_capacity", 120,
                NetworkInterfaceGameTests::requiresMutualAllowlistAndTotalConnectionCapacity);
        register(event, "network_interface_replacement_heartbeat", 160,
                NetworkInterfaceGameTests::handlesReplacementAndHeartbeatCleanup);
        register(event, "network_interface_request_storages", 160,
                NetworkInterfaceGameTests::requestCallbackReceivesBothDataStorages);
        register(event, "network_interface_request_null_storages", 160,
                NetworkInterfaceGameTests::requestCallbackReceivesNullStorages);
        register(event, "network_interface_same_type_hashes", 160,
                NetworkInterfaceGameTests::sameTypeTargetsUseDistinctMachineHashes);
    }

    private static void formedStructureClaimsSortedInterfacesAndCleansExcludedInterfaces(GameTestHelper helper) {
        MachineFixture fixture = placeMachine(helper, new BlockPos(4, 1, 4), MMCR.id("network_sorted"),
                List.of(new BlockPos(-1, 0, 0), new BlockPos(0, 0, -1), new BlockPos(1, 0, 0)),
                false, new NetworkInterfaceSpec(2, 2, Set.of()), Map.of(), Map.of());
        helper.runAtTickTime(20, () -> {
            assertFormed(helper, fixture);
            List<BlockPos> sorted = fixture.interfaces().stream().map(BlockEntity::getBlockPos)
                    .sorted(Comparator.<BlockPos>comparingInt(BlockPos::getX)
                            .thenComparingInt(BlockPos::getY).thenComparingInt(BlockPos::getZ)).toList();
            Set<BlockPos> active = fixture.controller().activeNetworkInterfacePositions();
            helper.assertTrue(active.equals(Set.copyOf(sorted.subList(0, 2))),
                    "Formed machine activates the sorted network interfaces up to maxCount");
            helper.assertTrue(fixture.interfaces().get(0).owner().isPresent()
                            && fixture.interfaces().get(1).owner().isPresent()
                            && fixture.interfaces().get(2).owner().isEmpty(),
                    "Excluded network interface is left unowned");
            helper.assertTrue(fixture.interfaces().get(0).appearanceBaseTexture().equals(FORMED_TEXTURE)
                            && fixture.interfaces().get(1).appearanceBaseTexture().equals(FORMED_TEXTURE)
                            && fixture.interfaces().get(2).appearanceBaseTexture().equals(MMCR.id("block/basic_casing")),
                    "Only activated interfaces receive the formed port base texture");
            helper.succeed();
        });
    }

    private static void keyCardBindsFormedMachinesAndReportsDuplicate(GameTestHelper helper) {
        MachineFixture source = placeMachine(helper, new BlockPos(1, 1, 1), KEY_SOURCE_ID,
                List.of(new BlockPos(1, 0, 0)), false,
                new NetworkInterfaceSpec(1, 2, Set.of(KEY_TARGET_ID)), Map.of(), Map.of());
        MachineFixture target = placeMachine(helper, new BlockPos(10, 1, 1), KEY_TARGET_ID,
                List.of(new BlockPos(1, 0, 0)), false,
                new NetworkInterfaceSpec(1, 2, Set.of(KEY_SOURCE_ID)), Map.of(), Map.of());
        helper.runAtTickTime(20, () -> {
            assertFormed(helper, source);
            assertFormed(helper, target);
            TestPlayer player = new TestPlayer(helper.getLevel());
            ItemStack card = new ItemStack(ModItems.KEY_CARD.get());
            player.setShiftKeyDown(true);
            useOn(helper.getLevel(), player, card, source.interfaces().getFirst().getBlockPos());
            helper.assertTrue(card.get(ModDataComponents.KEY_CARD_BINDING.get())
                            .equals(new KeyCardBinding(global(source.interfaces().getFirst().getBlockPos()), source.controller().machineReference())),
                    "Key card selects a formed source interface and its machine reference");
            player.setShiftKeyDown(false);
            useOn(helper.getLevel(), player, card, target.interfaces().getFirst().getBlockPos());
            useOn(helper.getLevel(), player, card, target.interfaces().getFirst().getBlockPos());
            helper.assertTrue(source.interfaces().getFirst().connections().size() == 1
                            && target.interfaces().getFirst().connections().size() == 1,
                    "Key card creates one reciprocal connection");
            helper.assertTrue(player.messages().contains(Component.translatable(
                            "message.mmcr.key_card.result.duplicate")),
                    "Key card reports a duplicate connection without replacing it");
            helper.succeed();
        });
    }

    private static void keyCardBindsMultipleTargets(GameTestHelper helper) {
        MachineFixture source = placeMachine(helper, new BlockPos(1, 1, 1), MULTI_SOURCE_ID,
                List.of(new BlockPos(1, 0, 0)), false,
                new NetworkInterfaceSpec(1, 2, Set.of(MULTI_TARGET_ID)),
                Map.of(), Map.of());
        MachineFixture first = placeMachine(helper, new BlockPos(10, 1, 1), MULTI_TARGET_ID,
                List.of(new BlockPos(1, 0, 0)), false,
                new NetworkInterfaceSpec(1, 2, Set.of(MULTI_SOURCE_ID)), Map.of(), Map.of());
        MachineFixture second = placeMachine(helper, new BlockPos(19, 1, 1), MULTI_TARGET_ID,
                List.of(new BlockPos(1, 0, 0)), false,
                new NetworkInterfaceSpec(1, 2, Set.of(MULTI_SOURCE_ID)), Map.of(), Map.of());
        helper.runAtTickTime(20, () -> {
            assertFormed(helper, source);
            assertFormed(helper, first);
            assertFormed(helper, second);
            NetworkInterfaceBindingCoordinator.ConnectionResult firstResult = connect(source, first);
            NetworkInterfaceBindingCoordinator.ConnectionResult secondResult = connect(source, second);
                    helper.assertTrue(firstResult == NetworkInterfaceBindingCoordinator.ConnectionResult.CONNECTED
                            && secondResult == NetworkInterfaceBindingCoordinator.ConnectionResult.CONNECTED
                            && source.interfaces().getFirst().connections().size() == 2
                            && first.interfaces().getFirst().connections().size() == 1
                            && second.interfaces().getFirst().connections().size() == 1,
                    "One source interface can bind multiple allowed targets (" + firstResult + ", " + secondResult
                            + ")");
            helper.succeed();
        });
    }

    private static void requiresMutualAllowlistAndTotalConnectionCapacity(GameTestHelper helper) {
        MachineFixture source = placeMachine(helper, new BlockPos(1, 1, 1), CAPACITY_SOURCE_ID,
                List.of(new BlockPos(1, 0, 0)), false,
                new NetworkInterfaceSpec(1, 1, Set.of(CAPACITY_BAD_ID, CAPACITY_TARGET_ID)),
                Map.of(), Map.of());
        MachineFixture rejected = placeMachine(helper, new BlockPos(10, 1, 1), CAPACITY_BAD_ID,
                List.of(new BlockPos(1, 0, 0)), false,
                new NetworkInterfaceSpec(1, 2, Set.of()), Map.of(), Map.of());
        MachineFixture first = placeMachine(helper, new BlockPos(19, 1, 1), CAPACITY_TARGET_ID,
                List.of(new BlockPos(1, 0, 0)), false,
                new NetworkInterfaceSpec(1, 2, Set.of(CAPACITY_SOURCE_ID)), Map.of(), Map.of());
        MachineFixture second = placeMachine(helper, new BlockPos(28, 1, 1), CAPACITY_TARGET_ID,
                List.of(new BlockPos(1, 0, 0)), false,
                new NetworkInterfaceSpec(1, 2, Set.of(CAPACITY_SOURCE_ID)), Map.of(), Map.of());
        helper.runAtTickTime(20, () -> {
            assertFormed(helper, source);
            assertFormed(helper, rejected);
            assertFormed(helper, first);
            assertFormed(helper, second);
            NetworkInterfaceBindingCoordinator.ConnectionResult rejectedResult = connect(source, rejected);
            NetworkInterfaceBindingCoordinator.ConnectionResult firstResult = connect(source, first);
            NetworkInterfaceBindingCoordinator.ConnectionResult secondResult = connect(source, second);
            helper.assertTrue(rejectedResult == NetworkInterfaceBindingCoordinator.ConnectionResult.ALLOWLIST_REJECTED,
                    "Connection requires both machines to allow one another");
                    helper.assertTrue(firstResult == NetworkInterfaceBindingCoordinator.ConnectionResult.CONNECTED
                            && secondResult == NetworkInterfaceBindingCoordinator.ConnectionResult.SOURCE_CAPACITY,
                    "Connection count is enforced across all active source interfaces (" + firstResult + ", "
                            + secondResult + ", " + rejectedResult + ")");
            helper.succeed();
        });
    }

    private static void handlesReplacementAndHeartbeatCleanup(GameTestHelper helper) {
        MachineFixture source = placeMachine(helper, new BlockPos(1, 1, 1), REPLACEMENT_SOURCE_ID,
                List.of(new BlockPos(1, 0, 0)), false,
                new NetworkInterfaceSpec(1, 2, Set.of(REPLACEMENT_TARGET_ID)), Map.of(), Map.of());
        MachineFixture target = placeMachine(helper, new BlockPos(10, 1, 1), REPLACEMENT_TARGET_ID,
                List.of(new BlockPos(1, 0, 0)), false,
                new NetworkInterfaceSpec(1, 2, Set.of(REPLACEMENT_SOURCE_ID)), Map.of(), Map.of());
        helper.runAtTickTime(20, () -> {
            assertFormed(helper, source);
            assertFormed(helper, target);
            helper.assertTrue(connect(source, target) == NetworkInterfaceBindingCoordinator.ConnectionResult.CONNECTED,
                    "Formed interfaces connect before replacement");
            target.controller().setFormed(false);
            NetworkInterfaceBindingCoordinator.heartbeat(helper.getLevel());
            helper.assertTrue(source.interfaces().getFirst().connections().isEmpty()
                            && target.interfaces().getFirst().connections().isEmpty(),
                    "Heartbeat removes connections to a controller that is no longer formed");
            helper.getLevel().setBlock(target.interfaces().getFirst().getBlockPos(), Blocks.STONE.defaultBlockState(), 3);
            helper.assertTrue(source.interfaces().getFirst().connections().isEmpty(),
                    "Breaking an interface removes it from loaded peers");
            helper.getLevel().setBlock(target.interfaces().getFirst().getBlockPos(),
                    ModBlocks.NETWORK_INTERFACE.get().defaultBlockState(), 3);
            NetworkInterfaceBlockEntity replacement = (NetworkInterfaceBlockEntity) helper.getLevel().getBlockEntity(
                    target.interfaces().getFirst().getBlockPos());
            helper.assertTrue(replacement != null && !replacement.isRemoved(),
                    "Replacing an interface creates a fresh network interface block entity");
            helper.succeed();
        });
    }

    private static void requestCallbackReceivesBothDataStorages(GameTestHelper helper) {
        AtomicReference<DataStorage[]> observed = new AtomicReference<>();
        MachineFixture source = placeMachineWithStorage(helper, new BlockPos(1, 1, 1), REQUEST_SOURCE_ID,
                new NetworkInterfaceSpec(1, 2, Set.of(REQUEST_TARGET_ID)), Map.of(), Map.of());
        MachineFixture target = placeMachineWithStorage(helper, new BlockPos(10, 1, 1), REQUEST_TARGET_ID,
                new NetworkInterfaceSpec(1, 2, Set.of(REQUEST_SOURCE_ID)),
                Map.of(REQUEST_ID, (body, request, sender, receiver) -> observed.set(new DataStorage[]{sender, receiver})), Map.of());
        sendRequestAfterFormation(helper, source, target, () -> {
            DataStorage[] storages = observed.get();
            helper.assertTrue(storages != null && storages[0] == source.storage().storage()
                            && storages[1] == target.storage().storage(),
                    "Request callback receives both formed DataStorage objects");
            helper.succeed();
        });
    }

    private static void requestCallbackReceivesNullStorages(GameTestHelper helper) {
        AtomicReference<DataStorage[]> observed = new AtomicReference<>();
        MachineFixture source = placeMachine(helper, new BlockPos(1, 1, 1), NULL_REQUEST_SOURCE_ID,
                List.of(new BlockPos(1, 0, 0)), false,
                new NetworkInterfaceSpec(1, 2, Set.of(NULL_REQUEST_TARGET_ID)), Map.of(), Map.of());
        MachineFixture target = placeMachine(helper, new BlockPos(10, 1, 1), NULL_REQUEST_TARGET_ID,
                List.of(new BlockPos(1, 0, 0)), false,
                new NetworkInterfaceSpec(1, 2, Set.of(NULL_REQUEST_SOURCE_ID)),
                Map.of(REQUEST_ID, (body, request, sender, receiver) -> observed.set(new DataStorage[]{sender, receiver})), Map.of());
        sendRequestAfterFormation(helper, source, target, () -> {
            DataStorage[] storages = observed.get();
            helper.assertTrue(storages != null && storages[0] == null && storages[1] == null,
                    "Request callback receives null storages when neither machine has DataStorage");
            helper.succeed();
        });
    }

    private static void sameTypeTargetsUseDistinctMachineHashes(GameTestHelper helper) {
        MachineFixture source = placeMachine(helper, new BlockPos(1, 1, 1), SAME_SOURCE_ID,
                List.of(new BlockPos(1, 0, 0)), false,
                new NetworkInterfaceSpec(1, 2, Set.of(SAME_TARGET_ID)), Map.of(), Map.of());
        MachineFixture first = placeMachine(helper, new BlockPos(10, 1, 1), SAME_TARGET_ID,
                List.of(new BlockPos(1, 0, 0)), false,
                new NetworkInterfaceSpec(1, 2, Set.of(SAME_SOURCE_ID)), Map.of(), Map.of());
        MachineFixture second = placeMachine(helper, new BlockPos(19, 1, 1), SAME_TARGET_ID,
                List.of(new BlockPos(1, 0, 0)), false,
                new NetworkInterfaceSpec(1, 2, Set.of(SAME_SOURCE_ID)), Map.of(), Map.of());
        helper.runAtTickTime(20, () -> {
            assertFormed(helper, source);
            assertFormed(helper, first);
            assertFormed(helper, second);
            helper.assertTrue(first.controller().machineReference().hash()
                            != second.controller().machineReference().hash(),
                    "Same-type formed controllers receive different position-based hashes");
            helper.assertTrue(connect(source, first) == NetworkInterfaceBindingCoordinator.ConnectionResult.CONNECTED
                            && connect(source, second) == NetworkInterfaceBindingCoordinator.ConnectionResult.CONNECTED,
                    "Same-type targets can both be bound when their hashes differ");
            List<NetworkInterfaceBlockEntity.Connection> connections = source.interfaces().getFirst().connections();
            helper.assertTrue(connections.stream().anyMatch(connection -> connection.endpoint()
                            .equals(global(first.interfaces().getFirst().getBlockPos()))
                            && connection.machine().equals(first.controller().machineReference()))
                            && connections.stream().anyMatch(connection -> connection.endpoint()
                            .equals(global(second.interfaces().getFirst().getBlockPos()))
                            && connection.machine().equals(second.controller().machineReference())),
                    "Connections retain the full MachineReference instead of only the machine type");
            helper.succeed();
        });
    }

    private static void sendRequestAfterFormation(GameTestHelper helper, MachineFixture source, MachineFixture target,
                                                  Runnable assertion) {
        helper.runAtTickTime(20, () -> {
            assertFormed(helper, source);
            assertFormed(helper, target);
            helper.assertTrue(connect(source, target) == NetworkInterfaceBindingCoordinator.ConnectionResult.CONNECTED,
                    "Formed interfaces connect before dispatching a request");
            helper.runAtTickTime(30, () -> {
                NetworkApi.sendRequest(NetworkApi.interfaces(source.controller().behaviorContext()).getFirst(),
                        target.controller().machineReference(), REQUEST_ID, RequestBody.of(Map.of()));
                helper.runAtTickTime(50, assertion);
            });
        });
    }

    private static MachineFixture placeMachineWithStorage(GameTestHelper helper, BlockPos controllerPos,
                                                           Identifier id, NetworkInterfaceSpec network,
                                                           Map<Identifier, RequestProcess> processors,
                                                           Map<Identifier, cn.howxu.mmcr.api.network.RequestFailed> failures) {
        return placeMachine(helper, controllerPos, id,
                List.of(new BlockPos(1, 0, 0)), true, network, processors, failures);
    }

    private static MachineFixture placeMachine(GameTestHelper helper, BlockPos controllerPos, Identifier id,
                                                List<BlockPos> interfaceOffsets, boolean storage,
                                                NetworkInterfaceSpec network,
                                                Map<Identifier, RequestProcess> processors,
                                                Map<Identifier, cn.howxu.mmcr.api.network.RequestFailed> failures) {
        Map<BlockPos, BlockPredicate> pattern = new LinkedHashMap<>();
        for (BlockPos offset : interfaceOffsets) {
            helper.setBlock(controllerPos.offset(offset), ModBlocks.NETWORK_INTERFACE.get().defaultBlockState());
            pattern.put(offset, new BlockPredicate.OfBlock(ModBlocks.NETWORK_INTERFACE.get()));
        }
        BlockPos storageOffset = new BlockPos(-1, 0, 0);
        if (storage) {
            helper.setBlock(controllerPos.offset(storageOffset), ModBlocks.DATA_STORAGE.get().defaultBlockState());
            pattern.put(storageOffset, new BlockPredicate.OfBlock(ModBlocks.DATA_STORAGE.get()));
        }
        helper.setBlock(controllerPos, ModBlocks.controllerFor(MMCR.id("test_cube")).get().defaultBlockState()
                .setValue(cn.howxu.mmcr.internal.block.MachineControllerBlock.FACING, Direction.SOUTH));
        Machine machine = machine(id, new BlockArray(pattern), network, processors, failures);
        if (!MachineRegistry.containsStatic(id)) MachineRegistry.register(machine);
        MachineControllerBlockEntity controller = helper.getBlockEntity(controllerPos, MachineControllerBlockEntity.class);
        controller.setMachine(machine);
        controller.setStructureCheckIntervalForTesting(1);
        helper.runAtTickTime(5, () -> {
            controller.setMachine(machine);
            controller.setFormed(false);
            controller.requestImmediateStructureCheck();
        });
        forceChunk(helper.getLevel(), helper.absolutePos(controllerPos));
        for (BlockPos offset : interfaceOffsets) forceChunk(helper.getLevel(), helper.absolutePos(controllerPos.offset(offset)));
        if (storage) forceChunk(helper.getLevel(), helper.absolutePos(controllerPos.offset(storageOffset)));
        List<NetworkInterfaceBlockEntity> interfaces = new ArrayList<>(interfaceOffsets.stream()
                .map(offset -> helper.getBlockEntity(controllerPos.offset(offset), NetworkInterfaceBlockEntity.class)).toList());
        cn.howxu.mmcr.internal.tile.DataStorageBlockEntity dataStorage = storage
                ? helper.getBlockEntity(controllerPos.offset(storageOffset), cn.howxu.mmcr.internal.tile.DataStorageBlockEntity.class)
                : null;
        return new MachineFixture(controller, interfaces, dataStorage, machine);
    }

    private static void forceChunk(ServerLevel level, BlockPos position) {
        level.setChunkForced(position.getX() >> 4, position.getZ() >> 4, true);
    }

    private static Machine machine(Identifier id, BlockArray pattern, NetworkInterfaceSpec network,
                                   Map<Identifier, RequestProcess> processors,
                                   Map<Identifier, cn.howxu.mmcr.api.network.RequestFailed> failures) {
        return new Machine() {
            @Override public Identifier registryName() { return id; }
            @Override public BlockArray pattern() { return pattern; }
            @Override public MachineControllerSpec controller() { return MachineControllerSpec.defaultsFor(id); }
            @Override public MachineAppearanceSpec appearance() {
                return new MachineAppearanceSpec(MMCR.id("network_casing"), MMCR.id("block/network_controller"), FORMED_TEXTURE);
            }
            @Override public NetworkInterfaceSpec networkInterface() { return network; }
            @Override public Map<Identifier, RequestProcess> requestProcessors() { return processors; }
            @Override public Map<Identifier, cn.howxu.mmcr.api.network.RequestFailed> requestFailures() { return failures; }
        };
    }

    private static void assertFormed(GameTestHelper helper, MachineFixture fixture) {
        helper.assertTrue(fixture.controller().structureSnapshot().formed(),
                "Machine " + fixture.machine().registryName() + " forms from the placed structure");
    }

    private static NetworkInterfaceBindingCoordinator.ConnectionResult connect(MachineFixture source, MachineFixture target) {
        ServerLevel level = (ServerLevel) source.controller().getLevel();
        return NetworkInterfaceBindingCoordinator.connect(level.getServer(),
                global(source.interfaces().getFirst().getBlockPos()), source.controller().machineReference(),
                global(target.interfaces().getFirst().getBlockPos()), target.controller().machineReference());
    }

    private static InteractionResult useOn(ServerLevel level, TestPlayer player, ItemStack stack, BlockPos position) {
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(position), Direction.UP, position, false);
        return ModItems.KEY_CARD.get().useOn(new net.minecraft.world.item.context.UseOnContext(
                level, player, InteractionHand.MAIN_HAND, stack, hit));
    }

    private static GlobalPos global(BlockPos position) {
        return GlobalPos.of(Level.OVERWORLD, position);
    }

    private static void register(RegisterGameTestsEvent event, String name, int maxTicks,
                                  Consumer<GameTestHelper> test) {
        Holder<TestEnvironmentDefinition<?>> environment = Holder.direct(new TestEnvironmentDefinition.AllOf());
        TestData<Holder<TestEnvironmentDefinition<?>>> data = new TestData<>(environment,
                Identifier.fromNamespaceAndPath("minecraft", "empty"), maxTicks, 0, true,
                Rotation.NONE, false, 1, 1, false, 128);
        registerTest(event, MMCR.id(name), new SimpleGameTest(data, name, test));
    }

    private static void registerTest(RegisterGameTestsEvent event, Identifier id, GameTestInstance instance) {
        try {
            event.getClass().getMethod("registerTest", Identifier.class, GameTestInstance.class).invoke(event, id, instance);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Unable to register GameTest " + id, exception);
        }
    }

    private record MachineFixture(MachineControllerBlockEntity controller,
                                  List<NetworkInterfaceBlockEntity> interfaces,
                                  cn.howxu.mmcr.internal.tile.DataStorageBlockEntity storage,
                                  Machine machine) {
    }

    private static final class SimpleGameTest extends GameTestInstance {
        private final String name;
        private final Consumer<GameTestHelper> test;

        private SimpleGameTest(TestData<Holder<TestEnvironmentDefinition<?>>> data, String name,
                               Consumer<GameTestHelper> test) {
            super(data);
            this.name = name;
            this.test = test;
        }

        @Override
        public void run(GameTestHelper helper) {
            test.accept(helper);
        }

        @Override
        @SuppressWarnings({"rawtypes", "unchecked"})
        public MapCodec<? extends GameTestInstance> codec() {
            return (MapCodec) MapCodec.unit(this);
        }

        @Override
        protected MutableComponent typeDescription() {
            return Component.literal("MMCR " + name);
        }
    }

    private static final class TestPlayer extends ServerPlayer {
        private final List<Component> messages = new ArrayList<>();
        private boolean shift;

        private TestPlayer(ServerLevel level) {
            super(level.getServer(), level, new GameProfile(UUID.randomUUID(), "mmcr-network-test"),
                    ClientInformation.createDefault());
        }

        @Override public void setShiftKeyDown(boolean shiftKeyDown) { shift = shiftKeyDown; }
        @Override public boolean isShiftKeyDown() { return shift; }
        @Override public void sendSystemMessage(Component message) { messages.add(message); }
        private List<Component> messages() { return messages; }
    }
}

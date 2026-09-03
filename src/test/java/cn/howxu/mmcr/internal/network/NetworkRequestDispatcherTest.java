package cn.howxu.mmcr.internal.network;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.config.Config;
import cn.howxu.mmcr.api.machine.BlockArray;
import cn.howxu.mmcr.api.machine.Machine;
import cn.howxu.mmcr.api.machine.MachineControllerSpec;
import cn.howxu.mmcr.api.machine.MachinePatternCompiler;
import cn.howxu.mmcr.api.machine.NetworkInterfaceSpec;
import cn.howxu.mmcr.api.data.DataStorage;
import cn.howxu.mmcr.api.network.MachineReference;
import cn.howxu.mmcr.api.network.RequestBody;
import cn.howxu.mmcr.api.network.RequestFailureReason;
import cn.howxu.mmcr.api.publicapi.machine.MachineBuilder;
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
import net.minecraft.world.level.ChunkPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.dedicated.DedicatedServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Property;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Verifies request callback registration consumed by the dispatcher.
 *
 * @author howxu <dev@howxu.cn>
 */
class NetworkRequestDispatcherTest {
    @BeforeAll
    static void bootstrapMinecraft() throws Exception {
        TestBootstrap.bootstrap();
    }

    @Test
    void requestHandlersPreserveRegistrationOrderAndRejectDuplicates() {
        Identifier first = Identifier.parse("mmcr:first");
        Identifier second = Identifier.parse("mmcr:second");
        var builder = MachineBuilder.machine(Identifier.parse("mmcr:request_test"))
                .requestProcess(first, (body, request, sender, receiver) -> { })
                .requestProcess(second, (body, request, sender, receiver) -> { });

        assertEquals(List.of(first, second), List.copyOf(builder.build().requestProcessors().keySet()));
        assertThrows(IllegalArgumentException.class,
                () -> builder.requestProcess(first, (body, request, sender, receiver) -> { }));
    }

    @Test
    void pendingRequestKeepsTheSuppliedImmutableBodyInstance() {
        RequestBody body = RequestBody.of(Map.of());

        assertEquals(body, new PendingRequest(global(BlockPos.ZERO), global(BlockPos.ZERO), global(BlockPos.ZERO),
                new MachineReference(MMCR.id("target"), 1L), Identifier.parse("mmcr:request"), body, 0L).body());
    }

    @Test
    void queuedRequestsAreProcessedInFIFOOrder() throws Exception {
        Identifier requestId = Identifier.parse("mmcr:fifo");
        Fixture fixture = fixture(requestId, null, new boolean[1][]);
        List<RequestBody> bodies = List.of(
                RequestBody.of(Map.of("value", cn.howxu.mmcr.api.data.DataValue.of(1))),
                RequestBody.of(Map.of("value", cn.howxu.mmcr.api.data.DataValue.of(2))),
                RequestBody.of(Map.of("value", cn.howxu.mmcr.api.data.DataValue.of(3))));
        NetworkServerState state = NetworkServerState.get(fixture.server);
        for (RequestBody body : bodies) {
            state.enqueue(new PendingRequest(fixture.sourceEndpoint, fixture.targetEndpoint, fixture.sourceOwner,
                    fixture.targetMachine, requestId, body, 0L));
        }

        state.dispatch(fixture.server, 1L);

        assertEquals(bodies, fixture.processedBodies);
    }

    @Test
    void oneBodyInstanceCanBeDeliveredToMultipleTargets() throws Exception {
        Identifier requestId = Identifier.parse("mmcr:shared_body");
        RequestFailureReason[] failure = new RequestFailureReason[1];
        Fixture fixture = fixture(requestId, failure, new boolean[1][]);
        List<RequestBody> secondBodies = new ArrayList<>();
        BlockPos targetControllerPos = new BlockPos(64, 64, 0);
        BlockPos targetInterfacePos = new BlockPos(65, 64, 0);
        Machine secondMachine = machine(MMCR.id("target_two"), requestId, null, null, secondBodies, null, null, null, null);
        MachineControllerBlockEntity secondController = controller(targetControllerPos, secondMachine, fixture.level, targetInterfacePos);
        NetworkInterfaceBlockEntity secondNetwork = createInterface(targetInterfacePos);
        secondNetwork.setLevel(fixture.level);
        fixture.level.blockEntities.put(targetInterfacePos, secondNetwork);
        fixture.level.loadedChunks.add(chunkPos(targetControllerPos));
        fixture.level.loadedChunks.add(chunkPos(targetInterfacePos));
        secondNetwork.claimOwner(global(targetControllerPos));
        MachineReference secondReference = secondController.machineReference();
        NetworkInterfaceBlockEntity sourceNetwork = (NetworkInterfaceBlockEntity)
                fixture.level.blockEntities.get(fixture.sourceEndpoint.pos());
        sourceNetwork.addConnection(new NetworkInterfaceBlockEntity.Connection(global(targetInterfacePos), secondReference, 2L));
        secondNetwork.addConnection(new NetworkInterfaceBlockEntity.Connection(fixture.sourceEndpoint, fixture.sourceMachine, 2L));
        RequestBody body = RequestBody.of(Map.of("nested", cn.howxu.mmcr.api.data.DataValue.map(
                Map.of("value", cn.howxu.mmcr.api.data.DataValue.of(7)))));
        NetworkServerState state = NetworkServerState.get(fixture.server);
        state.enqueue(new PendingRequest(fixture.sourceEndpoint, fixture.targetEndpoint, fixture.sourceOwner,
                fixture.targetMachine, requestId, body, 0L));
        state.enqueue(new PendingRequest(fixture.sourceEndpoint, global(targetInterfacePos), fixture.sourceOwner,
                secondReference, requestId, body, 0L));

        state.dispatch(fixture.server, 1L);

        assertEquals(1, fixture.processedBodies.size(), fixture.failureReasons.toString());
        assertEquals(1, secondBodies.size(), fixture.failureReasons.toString());
        assertEquals(body, fixture.processedBodies.getFirst());
        assertEquals(body, secondBodies.getFirst());
        assertEquals(7, body.get("nested").orElseThrow().asMap().orElseThrow().get("value").intValue());
    }

    @Test
    void dispatcherProcessesAtMostTheConfiguredBudgetPerTick() throws Exception {
        Identifier requestId = Identifier.parse("mmcr:budget");
        Fixture fixture = fixture(requestId, null, new boolean[1][]);
        NetworkServerState state = NetworkServerState.get(fixture.server);
        for (int index = 0; index < Config.DEFAULT_MAX_REQUESTS_PER_TICK + 1; index++) {
            state.enqueue(new PendingRequest(fixture.sourceEndpoint, fixture.targetEndpoint, fixture.sourceOwner,
                    fixture.targetMachine, requestId, RequestBody.of(Map.of()), 0L));
        }

        state.dispatch(fixture.server, 1L);

        assertEquals(Config.DEFAULT_MAX_REQUESTS_PER_TICK, fixture.processedBodies.size());
    }

    @Test
    void unloadedTargetReportsTargetChunkFailure() throws Exception {
        Identifier requestId = Identifier.parse("mmcr:unloaded");
        RequestFailureReason[] failure = new RequestFailureReason[1];
        Fixture fixture = fixture(requestId, failure, null);
        fixture.level.loadedChunks.remove(chunkPos(fixture.targetEndpoint.pos()));

        new NetworkRequestDispatcher(fixture.server).dispatch(new PendingRequest(fixture.sourceEndpoint, fixture.targetEndpoint,
                fixture.sourceOwner, fixture.targetMachine, requestId, RequestBody.of(Map.of()), 0L));

        assertEquals(RequestFailureReason.TARGET_CHUNK_UNLOADED, failure[0]);
    }

    @Test
    void missingReciprocalConnectionReportsConnectionFailure() throws Exception {
        Identifier requestId = Identifier.parse("mmcr:reciprocal");
        RequestFailureReason[] failure = new RequestFailureReason[1];
        Fixture fixture = fixture(requestId, failure, null);
        ((NetworkInterfaceBlockEntity) fixture.level.blockEntities.get(fixture.targetEndpoint.pos()))
                .removeConnection(fixture.sourceEndpoint);

        new NetworkRequestDispatcher(fixture.server).dispatch(new PendingRequest(fixture.sourceEndpoint, fixture.targetEndpoint,
                fixture.sourceOwner, fixture.targetMachine, requestId, RequestBody.of(Map.of()), 0L));

        assertEquals(RequestFailureReason.CONNECTION_MISSING, failure[0]);
    }

    @Test
    void targetHashMismatchReportsHashFailure() throws Exception {
        Identifier requestId = Identifier.parse("mmcr:hash");
        RequestFailureReason[] failure = new RequestFailureReason[1];
        Fixture fixture = fixture(requestId, failure, null);

        new NetworkRequestDispatcher(fixture.server).dispatch(new PendingRequest(fixture.sourceEndpoint, fixture.targetEndpoint,
                fixture.sourceOwner, new MachineReference(fixture.targetMachine.type(), fixture.targetMachine.hash() + 1),
                requestId, RequestBody.of(Map.of()), 0L));

        assertEquals(RequestFailureReason.HASH_MISMATCH, failure[0]);
    }

    @Test
    void missingTargetProcessorReportsHandlerFailure() throws Exception {
        Identifier requestId = Identifier.parse("mmcr:missing_processor");
        RequestFailureReason[] failure = new RequestFailureReason[1];
        Fixture fixture = fixture(requestId, failure, null, null, false);

        new NetworkRequestDispatcher(fixture.server).dispatch(new PendingRequest(fixture.sourceEndpoint, fixture.targetEndpoint,
                fixture.sourceOwner, fixture.targetMachine, requestId, RequestBody.of(Map.of()), 0L));

        assertEquals(RequestFailureReason.TARGET_HANDLER_MISSING, failure[0]);
    }

    @Test
    void missingSourceInterfaceReportsFailureAndPassesNullableStorage() throws Exception {
        Identifier requestId = Identifier.parse("mmcr:request");
        RequestFailureReason[] failure = new RequestFailureReason[1];
        boolean[][] storage = new boolean[1][];
        Fixture fixture = fixture(requestId, failure, storage);

        fixture.level.blockEntities.remove(fixture.sourceEndpoint.pos());
        new NetworkRequestDispatcher(fixture.server).dispatch(new PendingRequest(fixture.sourceEndpoint, fixture.targetEndpoint,
                fixture.sourceOwner, fixture.targetMachine, requestId, RequestBody.of(Map.of()), 0L));

        assertEquals(RequestFailureReason.SOURCE_INTERFACE_MISSING, failure[0]);
        assertEquals(null, storage[0]);
    }

    @Test
    void processorReceivesNullWhenNeitherControllerHasDataStorage() throws Exception {
        Identifier requestId = Identifier.parse("mmcr:request");
        boolean[][] storage = new boolean[1][];
        Fixture fixture = fixture(requestId, null, storage);

        new NetworkRequestDispatcher(fixture.server).dispatch(new PendingRequest(fixture.sourceEndpoint, fixture.targetEndpoint,
                fixture.sourceOwner, fixture.targetMachine, requestId, RequestBody.of(Map.of()), 0L));

        assertEquals(List.of(true, true), List.of(storage[0][0], storage[0][1]));
    }

    @Test
    void processorReceivesTheFirstStorageFromBothControllers() throws Exception {
        Identifier requestId = Identifier.parse("mmcr:storage");
        DataStorage[][] storages = new DataStorage[1][];
        Fixture fixture = fixture(requestId, null, null, storages);

        new NetworkRequestDispatcher(fixture.server).dispatch(new PendingRequest(fixture.sourceEndpoint, fixture.targetEndpoint,
                fixture.sourceOwner, fixture.targetMachine, requestId, RequestBody.of(Map.of()), 0L));

        assertEquals(2, storages[0].length);
        org.junit.jupiter.api.Assertions.assertSame(storages[0][0], fixture.observedStorages[0][0]);
        org.junit.jupiter.api.Assertions.assertSame(storages[0][1], fixture.observedStorages[0][1]);
    }

    @Test
    void callbackExceptionsDoNotRetryOrBlockFollowingRequests() throws Exception {
        Identifier requestId = Identifier.parse("mmcr:callback_exception");
        Fixture fixture = fixture(requestId, null, null);
        fixture.processorExceptions.add(new IllegalStateException("processor"));
        NetworkServerState state = NetworkServerState.get(fixture.server);
        RequestBody first = RequestBody.of(Map.of("value", cn.howxu.mmcr.api.data.DataValue.of(1)));
        RequestBody second = RequestBody.of(Map.of("value", cn.howxu.mmcr.api.data.DataValue.of(2)));
        state.enqueue(new PendingRequest(fixture.sourceEndpoint, fixture.targetEndpoint, fixture.sourceOwner,
                fixture.targetMachine, requestId, first, 0L));
        state.enqueue(new PendingRequest(fixture.sourceEndpoint, fixture.targetEndpoint, fixture.sourceOwner,
                fixture.targetMachine, requestId, second, 0L));

        state.dispatch(fixture.server, 1L);

        assertEquals(List.of(first, second), fixture.processedBodies);
    }

    @Test
    void failureCallbackExceptionsDoNotRetryOrBlockFollowingFailures() throws Exception {
        Identifier requestId = Identifier.parse("mmcr:failure_exception");
        RequestFailureReason[] failure = new RequestFailureReason[1];
        Fixture fixture = fixture(requestId, failure, null);
        fixture.failureExceptions.add(new IllegalStateException("failure"));
        NetworkServerState state = NetworkServerState.get(fixture.server);
        PendingRequest request = new PendingRequest(fixture.sourceEndpoint, fixture.targetEndpoint, fixture.sourceOwner,
                fixture.targetMachine, requestId, RequestBody.of(Map.of()), 0L);
        ((NetworkInterfaceBlockEntity) fixture.level.blockEntities.get(fixture.targetEndpoint.pos()))
                .removeConnection(fixture.sourceEndpoint);
        state.enqueue(request);
        state.enqueue(request);

        state.dispatch(fixture.server, 1L);

        assertEquals(List.of(RequestFailureReason.CONNECTION_MISSING, RequestFailureReason.CONNECTION_MISSING), fixture.failureReasons);
    }

    @Test
    void missingSourceOwnerUsesSourceStructureFailure() throws Exception {
        Identifier requestId = Identifier.parse("mmcr:source_owner");
        RequestFailureReason[] failure = new RequestFailureReason[1];
        Fixture fixture = fixture(requestId, failure, null);
        setField(fixture.level.blockEntities.get(fixture.sourceEndpoint.pos()), "owner", null);

        new NetworkRequestDispatcher(fixture.server).dispatch(new PendingRequest(fixture.sourceEndpoint, fixture.targetEndpoint,
                fixture.sourceOwner, fixture.targetMachine, requestId, RequestBody.of(Map.of()), 0L));

        assertEquals(RequestFailureReason.SOURCE_STRUCTURE_INVALID, failure[0]);
    }

    @Test
    void sourceOwnerThatIsNotTheFormedControllerUsesSourceStructureFailure() throws Exception {
        Identifier requestId = Identifier.parse("mmcr:source_controller");
        RequestFailureReason[] failure = new RequestFailureReason[1];
        Fixture fixture = fixture(requestId, failure, null);
        setField(fixture.level.blockEntities.get(fixture.sourceEndpoint.pos()), "owner", global(new BlockPos(2, 64, 0)));

        new NetworkRequestDispatcher(fixture.server).dispatch(new PendingRequest(fixture.sourceEndpoint, fixture.targetEndpoint,
                fixture.sourceOwner, fixture.targetMachine, requestId, RequestBody.of(Map.of()), 0L));

        assertEquals(RequestFailureReason.SOURCE_STRUCTURE_INVALID, failure[0]);
    }

    @Test
    void sourceControllerThatIsNoLongerFormedUsesSourceStructureFailure() throws Exception {
        Identifier requestId = Identifier.parse("mmcr:source_unformed");
        RequestFailureReason[] failure = new RequestFailureReason[1];
        Fixture fixture = fixture(requestId, failure, null);
        setField(fixture.level.blockEntities.get(fixture.sourceOwner.pos()), "activeNetworkInterfacePositions", Set.of());

        new NetworkRequestDispatcher(fixture.server).dispatch(new PendingRequest(fixture.sourceEndpoint, fixture.targetEndpoint,
                fixture.sourceOwner, fixture.targetMachine, requestId, RequestBody.of(Map.of()), 0L));

        assertEquals(RequestFailureReason.SOURCE_STRUCTURE_INVALID, failure[0]);
    }

    @Test
    void overloadWarnsOnceUntilPendingWorkReturnsToBudget() throws Exception {
        Fixture fixture = fixture(Identifier.parse("mmcr:request"), null, new boolean[1][]);
        RecordingAppender appender = new RecordingAppender();
        LoggerContext context = (LoggerContext) LogManager.getContext(false);
        context.getConfiguration().getLoggerConfig(NetworkServerState.class.getName()).addAppender(appender,
                org.apache.logging.log4j.Level.WARN, null);
        context.updateLoggers();
        appender.start();
        try {
            enqueueOverBudget(fixture);
            enqueueOverBudget(fixture);
            assertEquals(1, overloadWarnings(appender));

            NetworkServerState.get(fixture.server).dispatch(fixture.server, 1L);
            NetworkServerState.get(fixture.server).dispatch(fixture.server, 2L);
            enqueueOverBudget(fixture);
            assertEquals(2, overloadWarnings(appender));
        } finally {
            context.getConfiguration().getLoggerConfig(NetworkServerState.class.getName()).removeAppender(appender.getName());
            appender.stop();
            context.updateLoggers();
            NetworkServerState.discard(fixture.server);
        }
    }

    private static void enqueueOverBudget(Fixture fixture) {
        NetworkServerState state = NetworkServerState.get(fixture.server);
        for (int index = 0; index <= Config.DEFAULT_MAX_REQUESTS_PER_TICK; index++) {
            state.enqueue(new PendingRequest(fixture.sourceEndpoint, fixture.targetEndpoint, fixture.sourceOwner,
                    fixture.targetMachine, Identifier.parse("mmcr:request"), RequestBody.of(Map.of()), 0L));
        }
    }

    private static long overloadWarnings(RecordingAppender appender) {
        return appender.events.stream().filter(event -> event.getMessage().getFormattedMessage()
                .startsWith("Machine network request queue overloaded:")).count();
    }

    private static Fixture fixture(Identifier requestId, RequestFailureReason[] failure, boolean[][] storage) throws Exception {
        return fixture(requestId, failure, storage, null, true);
    }

    private static Fixture fixture(Identifier requestId, RequestFailureReason[] failure, boolean[][] storage,
                                   DataStorage[][] storages) throws Exception {
        return fixture(requestId, failure, storage, storages, true);
    }

    private static Fixture fixture(Identifier requestId, RequestFailureReason[] failure, boolean[][] storage,
                                   DataStorage[][] storages, boolean targetProcessor) throws Exception {
        TestServerLevel level = allocate(TestServerLevel.class);
        level.blockEntities = new HashMap<>();
        level.blocks = new HashMap<>();
        level.loadedChunks = new java.util.HashSet<>();
        setField(Level.class, level, "dimension", Level.OVERWORLD);
        setField(ServerLevel.class, level, "players", List.of());
        MinecraftServer server = allocate(DedicatedServer.class);
        setField(MinecraftServer.class, server, "levels", Map.of(Level.OVERWORLD, level));
        level.server = server;

        BlockPos sourceControllerPos = new BlockPos(0, 64, 0);
        BlockPos sourceInterfacePos = new BlockPos(1, 64, 0);
        BlockPos targetControllerPos = new BlockPos(32, 64, 0);
        BlockPos targetInterfacePos = new BlockPos(33, 64, 0);
        level.loadedChunks.add(chunkPos(sourceControllerPos));
        level.loadedChunks.add(chunkPos(sourceInterfacePos));
        level.loadedChunks.add(chunkPos(targetControllerPos));
        level.loadedChunks.add(chunkPos(targetInterfacePos));
        List<RequestBody> processedBodies = new ArrayList<>();
        List<RequestFailureReason> failureReasons = new ArrayList<>();
        List<RuntimeException> failureExceptions = new ArrayList<>();
        List<RuntimeException> processorExceptions = new ArrayList<>();
        DataStorage[][] observedStorages = new DataStorage[1][];
        Machine source = machine(MMCR.id("source"), requestId, failure, null, null, failureReasons, failureExceptions, null, null);
        Machine target = machine(MMCR.id("target"), requestId, null, storage,
                targetProcessor ? processedBodies : null, null, null, processorExceptions, observedStorages);
        MachineControllerBlockEntity sourceController = controller(sourceControllerPos, source, level, sourceInterfacePos);
        MachineControllerBlockEntity targetController = controller(targetControllerPos, target, level, targetInterfacePos);
        NetworkInterfaceBlockEntity sourceNetwork = createInterface(sourceInterfacePos);
        NetworkInterfaceBlockEntity targetNetwork = createInterface(targetInterfacePos);
        sourceNetwork.setLevel(level);
        targetNetwork.setLevel(level);
        level.blockEntities.put(sourceInterfacePos, sourceNetwork);
        level.blockEntities.put(targetInterfacePos, targetNetwork);
        GlobalPos sourceOwner = global(sourceControllerPos);
        GlobalPos targetOwner = global(targetControllerPos);
        sourceNetwork.claimOwner(sourceOwner);
        targetNetwork.claimOwner(targetOwner);
        MachineReference sourceMachine = sourceController.machineReference();
        MachineReference targetMachine = targetController.machineReference();
        sourceNetwork.addConnection(new NetworkInterfaceBlockEntity.Connection(global(targetInterfacePos), targetMachine, 1L));
        targetNetwork.addConnection(new NetworkInterfaceBlockEntity.Connection(global(sourceInterfacePos), sourceMachine, 1L));
        if (storages != null) {
            DataStorage sourceStorage = new DataStorage();
            DataStorage targetStorage = new DataStorage();
            publishDataStorage(sourceController, sourceStorage);
            publishDataStorage(targetController, targetStorage);
            storages[0] = new DataStorage[]{sourceStorage, targetStorage};
        }
        return new Fixture(server, level, global(sourceInterfacePos), global(targetInterfacePos), sourceOwner, sourceMachine,
                targetMachine, processedBodies, failureReasons, failureExceptions, processorExceptions, observedStorages);
    }

    private static Machine machine(Identifier id, Identifier requestId, RequestFailureReason[] failure, boolean[][] storage,
                                   List<RequestBody> processedBodies, List<RequestFailureReason> failureReasons,
                                   List<RuntimeException> failureExceptions, List<RuntimeException> processorExceptions,
                                   DataStorage[][] observedStorages) {
        return new Machine() {
            @Override public Identifier registryName() { return id; }
            @Override public BlockArray pattern() { return new BlockArray(Map.of()); }
            @Override public MachineControllerSpec controller() { return MachineControllerSpec.defaultsFor(id); }
            @Override public NetworkInterfaceSpec networkInterface() { return new NetworkInterfaceSpec(1, 2,
                    Set.of(MMCR.id("source"), MMCR.id("target"), MMCR.id("target_two"))); }
            @Override public Map<Identifier, cn.howxu.mmcr.api.network.RequestFailed> requestFailures() {
                return failure == null ? Map.of() : Map.of(requestId, (body, request, sender, reason) -> {
                    if (failure != null) failure[0] = reason;
                    if (failureReasons != null) failureReasons.add(reason);
                    if (failureExceptions != null && !failureExceptions.isEmpty()) throw failureExceptions.remove(0);
                });
            }
            @Override public Map<Identifier, cn.howxu.mmcr.api.network.RequestProcess> requestProcessors() {
                return storage == null && processedBodies == null ? Map.of() : Map.of(requestId, (body, request, sender, receiver) -> {
                    if (processedBodies != null) processedBodies.add(body);
                    if (storage != null) storage[0] = new boolean[]{sender == null, receiver == null};
                    if (observedStorages != null) observedStorages[0] = new DataStorage[]{sender, receiver};
                    if (processorExceptions != null && !processorExceptions.isEmpty()) throw processorExceptions.remove(0);
                });
            }
        };
    }

    private static MachineControllerBlockEntity controller(BlockPos pos, Machine machine, TestServerLevel level,
                                                             BlockPos interfacePos) throws Exception {
        MachineControllerBlockEntity controller = RuntimeTestFixtures.controllerEntity(MMCR.id("test_cube"), pos);
        controller.setLevel(level);
        level.blocks.put(pos, controller.getBlockState());
        Field runtimeField = MachineControllerBlockEntity.class.getDeclaredField("runtime");
        runtimeField.setAccessible(true);
        MachineControllerRuntime runtime = (MachineControllerRuntime) runtimeField.get(controller);
        Method publish = MachineControllerRuntime.class.getDeclaredMethod("publishFormationState", Machine.class,
                BlockArray.class, cn.howxu.mmcr.api.machine.CompiledMachinePattern.class, Direction.class, Direction.class, int.class);
        publish.setAccessible(true);
        publish.invoke(runtime, machine, machine.pattern(), MachinePatternCompiler.compile(machine), Direction.SOUTH, Direction.NORTH, 1);
        setField(controller, "activeNetworkInterfacePositions", Set.of(interfacePos));
        level.blockEntities.put(pos, controller);
        return controller;
    }

    private static NetworkInterfaceBlockEntity createInterface(BlockPos pos) {
        return (NetworkInterfaceBlockEntity) ModBlockEntities.NETWORK_INTERFACE.get().create(pos,
                ModBlocks.NETWORK_INTERFACE.get().defaultBlockState());
    }

    private static void publishDataStorage(MachineControllerBlockEntity controller, DataStorage storage) throws Exception {
        Field runtimeField = MachineControllerBlockEntity.class.getDeclaredField("runtime");
        runtimeField.setAccessible(true);
        MachineControllerRuntime runtime = (MachineControllerRuntime) runtimeField.get(controller);
        Method publish = MachineControllerRuntime.class.getDeclaredMethod("publishDataStorages", Map.class);
        publish.setAccessible(true);
        publish.invoke(runtime, Map.of(BlockPos.ZERO, storage));
    }

    private static GlobalPos global(BlockPos pos) {
        return GlobalPos.of(Level.OVERWORLD, pos);
    }

    private static ChunkPos chunkPos(BlockPos pos) {
        return new ChunkPos(pos.getX() >> 4, pos.getZ() >> 4);
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
                Field field = type.getDeclaredField(name);
                field.setAccessible(true);
                field.set(target, value);
                return;
            } catch (NoSuchFieldException ignored) {
            }
        }
        throw new NoSuchFieldException(name);
    }

    private static void setField(Class<?> type, Object target, String name, Object value) throws Exception {
        Field field = type.getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    private record Fixture(MinecraftServer server, TestServerLevel level, GlobalPos sourceEndpoint, GlobalPos targetEndpoint,
                           GlobalPos sourceOwner, MachineReference sourceMachine, MachineReference targetMachine,
                           List<RequestBody> processedBodies, List<RequestFailureReason> failureReasons,
                           List<RuntimeException> failureExceptions, List<RuntimeException> processorExceptions,
                           DataStorage[][] observedStorages) {
    }

    private static final class RecordingAppender extends AbstractAppender {
        private final List<LogEvent> events = new java.util.ArrayList<>();

        private RecordingAppender() {
            super("network-request-overload-test", null, null, true, Property.EMPTY_ARRAY);
        }

        @Override
        public void append(LogEvent event) {
            events.add(event.toImmutable());
        }
    }

    private static class TestServerLevel extends ServerLevel {
        private Map<BlockPos, BlockEntity> blockEntities;
        private Map<BlockPos, BlockState> blocks;
        private java.util.Set<ChunkPos> loadedChunks;
        private MinecraftServer server;

        private TestServerLevel() {
            super(null, null, null, null, Level.OVERWORLD, null, false, 0L, List.of(), false);
        }

        @Override public MinecraftServer getServer() { return server; }
        @Override public long getGameTime() { return 0L; }
        @Override public BlockEntity getBlockEntity(BlockPos pos) { return blockEntities.get(pos); }
        @Override public boolean hasChunk(int chunkX, int chunkZ) { return loadedChunks.contains(new ChunkPos(chunkX, chunkZ)); }
        @Override public BlockState getBlockState(BlockPos pos) { return blocks.getOrDefault(pos, Blocks.AIR.defaultBlockState()); }
        @Override public void blockEntityChanged(BlockPos pos) { }
        @Override public void sendBlockUpdated(BlockPos pos, BlockState oldState, BlockState newState, int flags) { }
    }
}

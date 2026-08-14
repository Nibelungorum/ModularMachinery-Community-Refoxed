package cn.howxu.mmcr.internal.multiblock;

import cn.howxu.mmcr.api.machine.BlockArray;
import cn.howxu.mmcr.api.machine.BlockPredicate;
import cn.howxu.mmcr.api.machine.DynamicMachine;
import cn.howxu.mmcr.api.machine.Machine;
import cn.howxu.mmcr.api.machine.MachinePatternCompiler;
import cn.howxu.mmcr.api.machine.MachineRole;
import cn.howxu.mmcr.internal.block.MachineControllerBlock;
import cn.howxu.mmcr.internal.tile.MachineControllerBlockEntity;
import cn.howxu.mmcr.internal.tile.ModuleCouplerBlockEntity;
import cn.howxu.mmcr.registry.ModBlocks;
import cn.howxu.mmcr.test.TestBootstrap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author howxu <dev@howxu.cn>
 */
class ModuleConnectionCoordinatorTest {
    private static final Identifier HOST_ID = Identifier.fromNamespaceAndPath("mmcr_test", "host");
    private static final Identifier MODULE_ID = Identifier.fromNamespaceAndPath("mmcr_test", "module");
    private static final Identifier OTHER_MODULE_ID = Identifier.fromNamespaceAndPath("mmcr_test", "other_module");

    @BeforeAll
    static void bootstrapMinecraft() throws Exception {
        TestBootstrap.bootstrap();
    }

    @Test
    void validate_rejects_connection_decision_table_failures() throws Exception {
        FormationFixture valid = formedFixture(HOST_ID, MODULE_ID, true, true, false, null);
        assertThat(ModuleConnectionCoordinator.validate(valid.coupler())).isTrue();

        assertThat(ModuleConnectionCoordinator.validate(formedFixture(HOST_ID, MODULE_ID, false, true, false, null).coupler())).isFalse();
        assertThat(ModuleConnectionCoordinator.validate(formedFixture(HOST_ID, MODULE_ID, true, false, false, null).coupler())).isFalse();
        assertThat(ModuleConnectionCoordinator.validate(formedFixture(HOST_ID, MODULE_ID, true, true, false,
                valid.couplerPos().east()).coupler())).isFalse();
        assertThat(ModuleConnectionCoordinator.validate(formedFixture(HOST_ID, OTHER_MODULE_ID, true, true, false, null).coupler())).isFalse();
        assertThat(ModuleConnectionCoordinator.validate(formedFixture(HOST_ID, MODULE_ID, true, true, true, null).coupler())).isFalse();
        assertThat(ModuleConnectionCoordinator.validate(formedFixture(HOST_ID, MODULE_ID, true, true, false, null, false).coupler())).isFalse();

        FormationFixture occupied = formedFixture(HOST_ID, MODULE_ID, true, true, false, null);
        occupied.coupler().setConnection(GlobalPos.of(Level.OVERWORLD, occupied.hostPos().west()),
                GlobalPos.of(Level.OVERWORLD, occupied.modulePos().east()));

        assertThat(ModuleConnectionCoordinator.validate(occupied.coupler())).isFalse();
    }

    @Test
    void refresh_connects_valid_formed_host_and_module_with_overlapping_normal_blocks() throws Exception {
        FormationFixture fixture = formedFixture(HOST_ID, MODULE_ID, true, true, false, null);

        ModuleConnectionCoordinator.refresh(fixture.level(), fixture.couplerPos());

        assertThat(fixture.coupler().connectedHost()).contains(GlobalPos.of(Level.OVERWORLD, fixture.hostPos()));
        assertThat(fixture.coupler().connectedModule()).contains(GlobalPos.of(Level.OVERWORLD, fixture.modulePos()));
    }

    @Test
    void refresh_clears_host_connections_when_interfaces_overlap_but_leaves_module_formed() throws Exception {
        FormationFixture fixture = formedFixture(HOST_ID, MODULE_ID, true, true, true, null);
        fixture.coupler().setConnection(GlobalPos.of(Level.OVERWORLD, fixture.hostPos()),
                GlobalPos.of(Level.OVERWORLD, fixture.modulePos()));

        ModuleConnectionCoordinator.refresh(fixture.level(), fixture.couplerPos());

        assertThat(fixture.coupler().connectedHost()).isEmpty();
        assertThat(fixture.coupler().connectedModule()).isEmpty();
        assertThat(fixture.host().isFormed()).isFalse();
        assertThat(fixture.module().isFormed()).isTrue();
    }

    @Test
    void one_coupler_cannot_establish_a_second_connection() throws Exception {
        FormationFixture fixture = formedFixture(HOST_ID, MODULE_ID, true, true, false, null);
        fixture.coupler().setConnection(GlobalPos.of(Level.OVERWORLD, fixture.hostPos().west()),
                GlobalPos.of(Level.OVERWORLD, fixture.modulePos().east()));

        ModuleConnectionCoordinator.refresh(fixture.level(), fixture.couplerPos());

        assertThat(fixture.coupler().connectedHost()).contains(GlobalPos.of(Level.OVERWORLD, fixture.hostPos().west()));
        assertThat(fixture.coupler().connectedModule()).contains(GlobalPos.of(Level.OVERWORLD, fixture.modulePos().east()));
    }

    @Test
    void refresh_queue_deduplicates_by_level_and_coupler_coordinate_and_ticks_pending_entries() throws Exception {
        FormationFixture fixture = formedFixture(HOST_ID, MODULE_ID, true, true, false, null);

        ModuleConnectionRefreshQueue.enqueue(fixture.level(), fixture.couplerPos());
        ModuleConnectionRefreshQueue.enqueue(fixture.level(), fixture.couplerPos());
        ModuleConnectionCoordinator.tick(fixture.level());

        assertThat(fixture.coupler().connectedHost()).contains(GlobalPos.of(Level.OVERWORLD, fixture.hostPos()));
        assertThat(fixture.coupler().connectedModule()).contains(GlobalPos.of(Level.OVERWORLD, fixture.modulePos()));
    }

    private static FormationFixture formedFixture(Identifier hostId, Identifier moduleId, boolean hostFormed,
                                                  boolean moduleFormed, boolean sharedInterface,
                                                  BlockPos moduleCouplerOverride) throws Exception {
        return formedFixture(hostId, moduleId, hostFormed, moduleFormed, sharedInterface, moduleCouplerOverride, true);
    }

    private static FormationFixture formedFixture(Identifier hostId, Identifier moduleId, boolean hostFormed,
                                                  boolean moduleFormed, boolean sharedInterface,
                                                  BlockPos moduleCouplerOverride, boolean chunksLoaded) throws Exception {
        BlockPos couplerPos = new BlockPos(10, 64, 10);
        BlockPos hostPos = new BlockPos(10, 64, 8);
        BlockPos modulePos = new BlockPos(10, 64, 12);
        BlockPos sharedNormal = new BlockPos(11, 64, 10);
        BlockPos hostInterface = sharedInterface ? new BlockPos(12, 64, 10) : new BlockPos(12, 64, 9);
        BlockPos moduleInterface = new BlockPos(12, 64, 10);
        BlockPos moduleCoupler = moduleCouplerOverride == null ? couplerPos : moduleCouplerOverride;

        DynamicMachine hostMachine = machine(hostId, MachineRole.HOST, Set.of(MODULE_ID), hostPos, couplerPos, sharedNormal, hostInterface);
        DynamicMachine moduleMachine = machine(moduleId, MachineRole.MODULE, Set.of(), modulePos, moduleCoupler, sharedNormal, moduleInterface);
        MachineControllerBlockEntity host = controller(hostPos, hostMachine, hostFormed);
        MachineControllerBlockEntity module = controller(modulePos, moduleMachine, moduleFormed);
        ModuleCouplerBlockEntity coupler = new ModuleCouplerBlockEntity(couplerPos, ModBlocks.MODULE_BRIDGE.get().defaultBlockState());
        Map<BlockPos, Block> blocks = new LinkedHashMap<>();
        blocks.put(hostPos, controllerBlock(hostId));
        blocks.put(modulePos, controllerBlock(moduleId));
        blocks.put(couplerPos, ModBlocks.MODULE_BRIDGE.get());
        blocks.put(moduleCoupler, ModBlocks.MODULE_BRIDGE.get());
        blocks.put(sharedNormal, Blocks.STONE);
        blocks.put(hostInterface, ModBlocks.SMART_INTERFACE.get());
        blocks.put(moduleInterface, ModBlocks.SMART_INTERFACE.get());
        TestServerLevel level = serverLevel(blocks, List.of(host, module, coupler), chunksLoaded);
        setField(BlockEntity.class, host, "level", level);
        setField(BlockEntity.class, module, "level", level);
        setField(BlockEntity.class, coupler, "level", level);
        if (hostFormed) StructureClaimRegistry.get(level).claim(hostPos, List.of());
        if (moduleFormed) StructureClaimRegistry.get(level).claim(modulePos, List.of());
        return new FormationFixture(level, host, module, coupler, hostPos, modulePos, couplerPos);
    }

    private static DynamicMachine machine(Identifier id, MachineRole role, Set<Identifier> acceptedModules,
                                          BlockPos controllerPos, BlockPos couplerWorldPos,
                                          BlockPos normalWorldPos, BlockPos interfaceWorldPos) {
        Map<BlockPos, BlockPredicate> pattern = new LinkedHashMap<>();
        pattern.put(couplerWorldPos.subtract(controllerPos), BlockPredicate.machineCoupler());
        pattern.put(normalWorldPos.subtract(controllerPos), new BlockPredicate.OfBlock(Blocks.STONE));
        pattern.put(interfaceWorldPos.subtract(controllerPos), new BlockPredicate.OfBlock(ModBlocks.SMART_INTERFACE.get()));
        return new DynamicMachine(id, id.toString(), new BlockArray(pattern)).withRole(role, acceptedModules);
    }

    private static MachineControllerBlockEntity controller(BlockPos pos, Machine machine, boolean formed) throws Exception {
        MachineControllerBlockEntity controller = (MachineControllerBlockEntity) unsafe().allocateInstance(MachineControllerBlockEntity.class);
        setField(MachineControllerBlockEntity.class, controller, "machine", machine);
        setField(MachineControllerBlockEntity.class, controller, "foundMachine", formed ? machine : null);
        setField(MachineControllerBlockEntity.class, controller, "foundPattern",
                formed ? MachinePatternCompiler.compile(machine).rotatedPattern(Direction.SOUTH) : null);
        setField(MachineControllerBlockEntity.class, controller, "foundCompiledPattern",
                formed ? MachinePatternCompiler.compile(machine) : null);
        setField(MachineControllerBlockEntity.class, controller, "controllerFacing", formed ? Direction.SOUTH : null);
        setField(MachineControllerBlockEntity.class, controller, "matchedRollFacing", Direction.SOUTH);
        setField(MachineControllerBlockEntity.class, controller, "components", new java.util.ArrayList<>());
        setField(MachineControllerBlockEntity.class, controller, "foundModifiers", new LinkedHashMap<>());
        setField(MachineControllerBlockEntity.class, controller, "foundLevels", Map.of());
        setField(MachineControllerBlockEntity.class, controller, "linkedPortPositions", new java.util.HashSet<>());
        setField(BlockEntity.class, controller, "worldPosition", pos);
        setField(BlockEntity.class, controller, "blockState", controllerBlock(machine.registryName()).defaultBlockState()
                .setValue(MachineControllerBlock.FACING, Direction.SOUTH)
                .setValue(MachineControllerBlock.ROLL_FACING, Direction.NORTH)
                .setValue(MachineControllerBlock.FORMED, formed)
                .setValue(MachineControllerBlock.ACTIVE, false));
        return controller;
    }

    private static MachineControllerBlock controllerBlock(Identifier machineId) throws Exception {
        MachineControllerBlock block = (MachineControllerBlock) unsafe().allocateInstance(MachineControllerBlock.class);
        setField(MachineControllerBlock.class, block, "machineId", machineId);
        setField(net.minecraft.world.level.block.state.BlockBehaviour.class, block, "properties", Blocks.IRON_BLOCK.properties());
        var builder = new net.minecraft.world.level.block.state.StateDefinition.Builder<Block, BlockState>(block);
        builder.add(MachineControllerBlock.FACING, MachineControllerBlock.ROLL_FACING,
                MachineControllerBlock.FORMED, MachineControllerBlock.ACTIVE);
        var stateDefinition = builder.create(Block::defaultBlockState, BlockState::new);
        setField(Block.class, block, "stateDefinition", stateDefinition);
        setField(Block.class, block, "defaultBlockState", stateDefinition.any()
                .setValue(MachineControllerBlock.FACING, Direction.SOUTH)
                .setValue(MachineControllerBlock.ROLL_FACING, Direction.NORTH)
                .setValue(MachineControllerBlock.FORMED, false)
                .setValue(MachineControllerBlock.ACTIVE, false));
        return block;
    }

    private static TestServerLevel serverLevel(Map<BlockPos, Block> blocks, List<BlockEntity> blockEntities,
                                               boolean chunksLoaded) throws Exception {
        TestServerLevel level = (TestServerLevel) unsafe().allocateInstance(TestServerLevel.class);
        setField(TestServerLevel.class, level, "blocks", new HashMap<>(blocks.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, entry -> entry.getValue().defaultBlockState()))));
        setField(TestServerLevel.class, level, "blockEntities", blockEntities.stream()
                .collect(Collectors.toMap(BlockEntity::getBlockPos, entity -> entity)));
        setField(TestServerLevel.class, level, "chunksLoaded", chunksLoaded);
        setField(Level.class, level, "dimension", Level.OVERWORLD);
        return level;
    }

    private static sun.misc.Unsafe unsafe() throws ReflectiveOperationException {
        Field unsafeField = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
        unsafeField.setAccessible(true);
        return (sun.misc.Unsafe) unsafeField.get(null);
    }

    private static void setField(Class<?> declaringClass, Object target, String name, Object value) throws ReflectiveOperationException {
        Field field = declaringClass.getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    private record FormationFixture(TestServerLevel level, MachineControllerBlockEntity host,
                                    MachineControllerBlockEntity module, ModuleCouplerBlockEntity coupler,
                                    BlockPos hostPos, BlockPos modulePos, BlockPos couplerPos) {
    }

    private static class TestServerLevel extends ServerLevel {
        private Map<BlockPos, BlockState> blocks;
        private Map<BlockPos, BlockEntity> blockEntities;
        private boolean chunksLoaded;

        private TestServerLevel() {
            super(null, null, null, null, null, null, false, 0L, List.of(), false);
        }

        @Override public BlockState getBlockState(BlockPos pos) {
            return blocks.getOrDefault(pos, Blocks.AIR.defaultBlockState());
        }

        @Override public BlockEntity getBlockEntity(BlockPos pos) {
            return blockEntities.get(pos);
        }

        @Override public boolean setBlock(BlockPos pos, BlockState state, int flags) {
            blocks.put(pos, state);
            BlockEntity blockEntity = blockEntities.get(pos);
            if (blockEntity != null) {
                try {
                    setField(BlockEntity.class, blockEntity, "blockState", state);
                } catch (ReflectiveOperationException e) {
                    throw new AssertionError("Unable to update block entity state", e);
                }
            }
            return true;
        }

        @Override public void sendBlockUpdated(BlockPos pos, BlockState oldState, BlockState newState, int flags) {
        }

        @Override public boolean hasChunk(int chunkX, int chunkZ) {
            return chunksLoaded;
        }

        @Override public void blockEntityChanged(BlockPos pos) {
        }

        @Override public long getGameTime() {
            return 1L;
        }
    }
}

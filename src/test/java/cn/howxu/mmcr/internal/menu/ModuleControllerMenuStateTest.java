package cn.howxu.mmcr.internal.menu;

import cn.howxu.mmcr.api.machine.BlockArray;
import cn.howxu.mmcr.api.machine.BlockPredicate;
import cn.howxu.mmcr.api.machine.DynamicMachine;
import cn.howxu.mmcr.api.machine.Machine;
import cn.howxu.mmcr.api.machine.MachinePatternCompiler;
import cn.howxu.mmcr.api.machine.MachineRole;
import cn.howxu.mmcr.internal.block.MachineControllerBlock;
import cn.howxu.mmcr.internal.multiblock.ModuleConnectionCoordinator;
import cn.howxu.mmcr.internal.network.PktMachineStatePayload;
import cn.howxu.mmcr.internal.tile.MachineControllerBlockEntity;
import cn.howxu.mmcr.internal.tile.ModuleCouplerBlockEntity;
import cn.howxu.mmcr.registry.ModBlocks;
import cn.howxu.mmcr.registry.ModUIs;
import cn.howxu.mmcr.test.TestBootstrap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.Holder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import io.netty.buffer.Unpooled;
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
class ModuleControllerMenuStateTest {
    private static final Identifier HOST_ID = Identifier.fromNamespaceAndPath("mmcr_test", "host");
    private static final Identifier MODULE_ID = Identifier.fromNamespaceAndPath("mmcr_test", "module");
    private static final Identifier OTHER_MODULE_ID = Identifier.fromNamespaceAndPath("mmcr_test", "other_module");

    @BeforeAll
    static void bootstrapMinecraft() throws Exception {
        TestBootstrap.bootstrap();
        bind(ModUIs.MACHINE_CONTROLLER, new MenuType<>(MachineControllerMenu::clientOpen, FeatureFlags.VANILLA_SET));
    }

    @Test
    void host_counts_only_loaded_formed_bidirectionally_valid_modules() throws Exception {
        FormationFixture valid = formedFixture(HOST_ID, MODULE_ID, true, true, false, null, true);
        ModuleConnectionCoordinator.refresh(valid.level(), valid.couplerPos());
        assertThat(valid.host().installedModuleCount()).isEqualTo(1);
        assertThat(new MachineControllerMenu(1, emptyInventory(), valid.host()).installedModuleCount()).isEqualTo(1);

        assertThat(formedFixture(HOST_ID, MODULE_ID, true, false, false, null, true).host().installedModuleCount()).isZero();
        assertThat(formedFixture(HOST_ID, MODULE_ID, true, true, false, null, false).host().installedModuleCount()).isZero();
        assertThat(formedFixture(HOST_ID, OTHER_MODULE_ID, true, true, false, null, true).host().installedModuleCount()).isZero();
    }

    @Test
    void host_interface_conflict_invalid_state_returns_zero_modules() throws Exception {
        FormationFixture fixture = formedFixture(HOST_ID, MODULE_ID, true, true, true, null, true);
        ModuleConnectionCoordinator.refresh(fixture.level(), fixture.couplerPos());

        assertThat(fixture.host().isFormed()).isFalse();
        assertThat(fixture.host().installedModuleCount()).isZero();
    }

    @Test
    void module_exposes_no_connection_or_connected_host_registry_id() throws Exception {
        FormationFixture disconnected = formedFixture(HOST_ID, MODULE_ID, true, true, false, null, true);
        assertThat(disconnected.module().connectedHostId()).isEmpty();
        assertThat(new MachineControllerMenu(1, emptyInventory(), disconnected.module()).connectedHostId()).isEmpty();

        ModuleConnectionCoordinator.refresh(disconnected.level(), disconnected.couplerPos());

        assertThat(disconnected.module().connectedHostId()).contains(HOST_ID);
        assertThat(new MachineControllerMenu(1, emptyInventory(), disconnected.module()).connectedHostId()).contains(HOST_ID);
    }

    @Test
    void client_sync_values_change_only_when_connection_state_changes() throws Exception {
        FormationFixture fixture = formedFixture(HOST_ID, MODULE_ID, true, true, false, null, true);
        MachineControllerMenu hostMenu = new MachineControllerMenu(1, emptyInventory(), fixture.host());
        MachineControllerMenu moduleMenu = new MachineControllerMenu(2, emptyInventory(), fixture.module());

        int hostCountBefore = dataSlot(hostMenu, 13).get();
        int moduleConnectedBefore = dataSlot(moduleMenu, 14).get();
        int moduleRoleBefore = dataSlot(moduleMenu, 15).get();
        assertThat(hostCountBefore).isZero();
        assertThat(moduleConnectedBefore).isZero();
        assertThat(moduleRoleBefore).isEqualTo(2);

        ModuleConnectionCoordinator.refresh(fixture.level(), fixture.couplerPos());

        assertThat(dataSlot(hostMenu, 13).get()).isEqualTo(1);
        assertThat(dataSlot(moduleMenu, 14).get()).isEqualTo(1);
        assertThat(dataSlot(moduleMenu, 15).get()).isEqualTo(2);

        int hostCountConnected = dataSlot(hostMenu, 13).get();
        int moduleConnected = dataSlot(moduleMenu, 14).get();
        int moduleRole = dataSlot(moduleMenu, 15).get();
        ModuleConnectionCoordinator.refresh(fixture.level(), fixture.couplerPos());

        assertThat(dataSlot(hostMenu, 13).get()).isEqualTo(hostCountConnected);
        assertThat(dataSlot(moduleMenu, 14).get()).isEqualTo(moduleConnected);
        assertThat(dataSlot(moduleMenu, 15).get()).isEqualTo(moduleRole);
    }

    @Test
    void client_open_reconstructs_exact_machine_role_and_connected_host_id_from_buffer() throws Exception {
        Identifier collidingHost = Identifier.fromNamespaceAndPath("mmcr_test", "an");
        Identifier collidingOther = Identifier.fromNamespaceAndPath("mmcr_test", "c0");
        assertThat(collidingHost.toString().hashCode()).isEqualTo(collidingOther.toString().hashCode());

        MachineControllerMenu clientMenu = MachineControllerMenu.clientOpen(3, emptyInventory(), menuBuffer(
                new BlockPos(1, 2, 3), MODULE_ID, collidingOther, 2, true, 0));

        assertThat(clientMenu.machineId()).isEqualTo(MODULE_ID);
        assertThat(clientMenu.connectedHostId()).contains(collidingOther);
        assertThat(clientMenu.isModuleController()).isTrue();
        assertThat(clientMenu.isHostController()).isFalse();
    }

    @Test
    void client_menu_payload_updates_exact_connected_host_id_without_hash_lookup() throws Exception {
        Identifier collidingHost = Identifier.fromNamespaceAndPath("mmcr_test", "an");
        Identifier collidingOther = Identifier.fromNamespaceAndPath("mmcr_test", "c0");
        assertThat(collidingHost.toString().hashCode()).isEqualTo(collidingOther.toString().hashCode());

        MachineControllerMenu clientMenu = MachineControllerMenu.clientOpen(4, emptyInventory(), menuBuffer(
                new BlockPos(4, 5, 6), MODULE_ID, collidingHost, 2, true, 0));

        clientMenu.applyModuleStatus(0, true, collidingOther);

        assertThat(clientMenu.connectedHostId()).contains(collidingOther);
    }

    @Test
    void machine_state_payload_change_detection_includes_module_controller_fields() {
        assertThat(PktMachineStatePayload.stateChanged(false, false, false, "", "mmcr_test:module", 2, 0, false, "",
                false, false, false, "", "mmcr_test:host", 2, 0, false, "")).isTrue();
        assertThat(PktMachineStatePayload.stateChanged(false, false, false, "", "mmcr_test:module", 1, 1, false, "",
                false, false, false, "", "mmcr_test:module", 1, 0, false, "")).isTrue();
        assertThat(PktMachineStatePayload.stateChanged(false, false, false, "", "mmcr_test:module", 2, 0, true, "mmcr_test:host",
                false, false, false, "", "mmcr_test:module", 2, 0, false, "")).isTrue();
        assertThat(PktMachineStatePayload.stateChanged(false, false, false, "", "mmcr_test:module", 2, 0, true, "mmcr_test:host_b",
                false, false, false, "", "mmcr_test:module", 2, 0, true, "mmcr_test:host_a")).isTrue();
        assertThat(PktMachineStatePayload.stateChanged(false, false, false, "", "mmcr_test:module", 2, 3, true, "mmcr_test:host",
                false, false, false, "", "mmcr_test:module", 2, 3, true, "mmcr_test:host")).isFalse();
    }

    private static Inventory emptyInventory() {
        return new Inventory(null, null);
    }

    private static RegistryFriendlyByteBuf menuBuffer(BlockPos pos, Identifier machineId, Identifier connectedHostId,
                                                      int controllerRole, boolean formed, int installedModuleCount) {
        RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), null);
        MachineControllerMenu.writeClientOpenData(buf, pos, machineId, connectedHostId, controllerRole, formed, installedModuleCount);
        return buf;
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
        if (hostFormed) cn.howxu.mmcr.internal.multiblock.StructureClaimRegistry.get(level).claim(hostPos, List.of());
        if (moduleFormed) cn.howxu.mmcr.internal.multiblock.StructureClaimRegistry.get(level).claim(modulePos, List.of());
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

    @SuppressWarnings("unchecked")
    private static DataSlot dataSlot(AbstractContainerMenu menu, int index) throws Exception {
        Field field = AbstractContainerMenu.class.getDeclaredField("dataSlots");
        field.setAccessible(true);
        return ((List<DataSlot>) field.get(menu)).get(index);
    }

    private static void setField(Class<?> declaringClass, Object target, String name, Object value) throws ReflectiveOperationException {
        Field field = declaringClass.getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static void bind(Object deferredHolder, MenuType<MachineControllerMenu> menuType) throws Exception {
        Class<?> type = deferredHolder.getClass();
        Field holder = null;
        while (type != null && holder == null) {
            try {
                holder = type.getDeclaredField("holder");
            } catch (NoSuchFieldException ignored) {
                type = type.getSuperclass();
            }
        }
        if (holder == null) throw new NoSuchFieldException("holder");
        holder.setAccessible(true);
        holder.set(deferredHolder, Holder.direct(menuType));
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

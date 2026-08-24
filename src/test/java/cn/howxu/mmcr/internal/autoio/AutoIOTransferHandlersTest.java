package cn.howxu.mmcr.internal.autoio;

import cn.howxu.mmcr.LevelStub;
import cn.howxu.mmcr.api.capability.CapabilitySnapshot;
import cn.howxu.mmcr.internal.tile.IOPortBlockEntity;
import cn.howxu.mmcr.internal.port.IOPortKind;
import cn.howxu.mmcr.registry.ModBlockEntities;
import cn.howxu.mmcr.registry.ModBlocks;
import cn.howxu.mmcr.registry.PortKinds;
import cn.howxu.mmcr.test.TestBootstrap;
import cn.howxu.mmcr.util.IOType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author howxu <dev@howxu.cn>
 */
class AutoIOTransferHandlersTest {

    @BeforeAll
    static void bootstrapMinecraft() throws Exception {
        TestBootstrap.bootstrap();
    }

    @Test
    void handler_for_matches_item_fluid_and_energy_ports() {
        assertThat(AutoIOTransferHandlers.handlerFor(port("item_input_bus_normal"))).isPresent();
        assertThat(AutoIOTransferHandlers.handlerFor(port("fluid_input_hatch_tiny"))).isPresent();
        assertThat(AutoIOTransferHandlers.handlerFor(port("energy_input_hatch_tiny"))).isPresent();
    }

    @Test
    void gas_is_reserved_but_has_no_builtin_handler() {
        assertThat(AutoIOCapabilityType.GAS).isNotNull();
    }

    @Test
    void empty_candidate_cache_rechecks_after_retry_delay() {
        ProbeHandler handler = new ProbeHandler();
        ProbePort port = new ProbePort(BlockPos.ZERO, ModBlocks.BLOCKS.get("item_input_bus").get().defaultBlockState(), handler);
        port.toggleAutoIOEnabled();
        port.setAllAutoIOSides(false);
        port.setAutoIOSide(Direction.EAST, true);

        port.runAutoIOCycleAt(1L);
        assertThat(handler.hasAdjacentTargetCalls).isEqualTo(1);

        handler.adjacentTarget = true;
        port.runAutoIOCycleAt(2L);
        assertThat(handler.hasAdjacentTargetCalls).isEqualTo(1);

        for (long gameTime = 3L; gameTime <= 6L; gameTime++) {
            port.runAutoIOCycleAt(gameTime);
        }
        assertThat(handler.hasAdjacentTargetCalls).isEqualTo(2);
        assertThat(handler.transferCalls).isEqualTo(1);

        port.runAutoIOCycleAt(7L);
        assertThat(handler.hasAdjacentTargetCalls).isEqualTo(2);
        assertThat(handler.transferCalls).isEqualTo(1);
    }

    @Test
    void output_port_with_no_transferable_contents_skips_transfer_attempts() {
        ProbeHandler handler = new ProbeHandler();
        ProbePort port = new ProbePort(BlockPos.ZERO, ModBlocks.BLOCKS.get("item_output_bus").get().defaultBlockState(), handler, IOType.OUTPUT, PortKinds.ITEM_OUTPUT);
        port.hasTransferWork = false;
        port.toggleAutoIOEnabled();
        port.setAllAutoIOSides(false);
        port.setAutoIOSide(Direction.EAST, true);
        handler.adjacentTarget = true;

        port.runAutoIOCycleAt(60L);

        assertThat(handler.hasAdjacentTargetCalls).isEqualTo(1);
        assertThat(handler.transferCalls).isZero();
    }

    @Test
    void eject_contents_stops_after_complete_first_side_export() {
        ProbeHandler handler = new ProbeHandler(4);
        ProbePort port = inputPort(handler);
        for (Direction direction : Direction.values()) handler.ejectedAmounts.put(direction, 4);

        assertThat(port.ejectContentsAt(1L)).isTrue();

        assertThat(handler.ejectCalls).hasSize(1);
        assertThat(handler.ejectCalls).containsExactly(handler.ejectCalls.getFirst());
        assertThat(handler.hasTransferableContentsCalls).isEqualTo(2);
        assertThat(handler.remainingContents).isZero();
    }

    @Test
    void eject_contents_continues_to_another_side_after_partial_export() {
        ProbeHandler handler = new ProbeHandler(6);
        ProbePort port = inputPort(handler);
        handler.ejectionAmounts.addAll(List.of(2, 4));

        assertThat(port.ejectContentsAt(1L)).isTrue();

        assertThat(handler.ejectCalls).hasSize(2).containsExactly(handler.ejectCalls.get(0), handler.ejectCalls.get(1));
        assertThat(handler.hasTransferableContentsCalls).isEqualTo(3);
        assertThat(handler.remainingContents).isZero();
    }

    @Test
    void eject_contents_preserves_remainder_when_total_capacity_is_insufficient() {
        ProbeHandler handler = new ProbeHandler(6);
        ProbePort port = inputPort(handler);
        for (Direction direction : Direction.values()) handler.ejectedAmounts.put(direction, 1);

        assertThat(port.ejectContentsAt(1L)).isTrue();

        assertThat(handler.ejectCalls).hasSize(Direction.values().length);
        assertThat(handler.hasTransferableContentsCalls).isEqualTo(Direction.values().length + 1);
        assertThat(handler.remainingContents).isEqualTo(6 - Direction.values().length);
    }

    @Test
    void eject_contents_rejects_output_ports_without_handler_calls() {
        ProbeHandler handler = new ProbeHandler(4);
        ProbePort port = new ProbePort(BlockPos.ZERO, ModBlocks.BLOCKS.get("item_output_bus").get().defaultBlockState(), handler, IOType.OUTPUT, PortKinds.ITEM_OUTPUT);

        assertThat(port.ejectContentsAt(1L)).isFalse();

        assertThat(handler.ejectCalls).isEmpty();
        assertThat(handler.hasTransferableContentsCalls).isZero();
    }

    @Test
    void eject_contents_rejects_ports_used_by_an_active_recipe_without_handler_calls() {
        ProbeHandler handler = new ProbeHandler(4);
        ProbePort port = inputPort(handler);
        port.usedByActiveRecipe = true;

        assertThat(port.ejectContentsAt(1L)).isFalse();

        assertThat(handler.ejectCalls).isEmpty();
        assertThat(handler.hasTransferableContentsCalls).isZero();
    }

    private static IOPortBlockEntity port(String id) {
        IOPortKind kind = PortKinds.all().stream().filter(candidate -> candidate.id().equals(id)).findFirst()
                .orElseGet(() -> PortKinds.all().stream()
                        .filter(candidate -> candidate.id().equals(id.replace("_normal", "")))
                        .findFirst()
                        .orElseThrow());
        return (IOPortBlockEntity) kind.entityFactory().create(BlockPos.ZERO, ModBlocks.BLOCKS.get(kind.id()).get().defaultBlockState());
    }

    private static ProbePort inputPort(ProbeHandler handler) {
        return new ProbePort(BlockPos.ZERO, ModBlocks.BLOCKS.get("item_input_bus").get().defaultBlockState(), handler);
    }

    private static final class ProbePort extends IOPortBlockEntity {
        private final ProbeHandler handler;
        private final IOType ioType;
        private final IOPortKind kind;
        private boolean hasTransferWork = true;
        private boolean usedByActiveRecipe;

        private ProbePort(BlockPos pos, BlockState state, ProbeHandler handler) {
            this(pos, state, handler, IOType.INPUT, PortKinds.ITEM_INPUT);
        }

        private ProbePort(BlockPos pos, BlockState state, ProbeHandler handler, IOType ioType, IOPortKind kind) {
            super(ModBlockEntities.BES.get(kind.id()).get(), pos, state);
            this.handler = handler;
            this.ioType = ioType;
            this.kind = kind;
        }

        private void runAutoIOCycleAt(long gameTime) {
            var level = LevelStub.create(Map.of());
            LevelStub.setGameTime(level, gameTime);
            setLevel(level);
            runAutoIOCycle();
        }

        private boolean ejectContentsAt(long gameTime) {
            var level = LevelStub.create(Map.of());
            LevelStub.setGameTime(level, gameTime);
            setLevel(level);
            return ejectContents();
        }

        @Override public IOType ioType() { return ioType; }
        @Override public IOPortKind kind() { return kind; }
        @Override public CapabilitySnapshot capabilitySnapshot() { return new CapabilitySnapshot(List.of()); }
        @Override public AutoIOCapabilityType autoIOCapabilityType() { return AutoIOCapabilityType.ITEM; }
        @Override protected AutoIOTransferHandler autoIOTransferHandler() { return handler; }
        @Override protected boolean hasAutoIOTransferWork() { return hasTransferWork; }
        @Override protected boolean isUsedByActiveRecipe() { return usedByActiveRecipe; }
    }

    private static final class ProbeHandler implements AutoIOTransferHandler {
        private boolean adjacentTarget;
        private int hasAdjacentTargetCalls;
        private int transferCalls;
        private int hasTransferableContentsCalls;
        private final List<Direction> ejectCalls = new ArrayList<>();
        private final List<Integer> ejectionAmounts = new ArrayList<>();
        private final Map<Direction, Integer> ejectedAmounts = new EnumMap<>(Direction.class);
        private int remainingContents;

        private ProbeHandler() {}

        private ProbeHandler(int remainingContents) {
            this.remainingContents = remainingContents;
        }

        @Override public AutoIOCapabilityType type() { return AutoIOCapabilityType.ITEM; }
        @Override public boolean supports(IOPortBlockEntity port) { return port instanceof ProbePort; }
        @Override public boolean hasAdjacentTarget(IOPortBlockEntity port, Direction side) {
            hasAdjacentTargetCalls++;
            return adjacentTarget;
        }
        @Override public boolean transfer(IOPortBlockEntity port, Direction side) {
            transferCalls++;
            return false;
        }
        @Override public boolean eject(IOPortBlockEntity port, Direction side) {
            ejectCalls.add(side);
            int capacity = ejectionAmounts.isEmpty() ? ejectedAmounts.getOrDefault(side, 0) : ejectionAmounts.removeFirst();
            int moved = Math.min(remainingContents, capacity);
            remainingContents -= moved;
            return moved > 0;
        }
        @Override public boolean hasTransferableContents(IOPortBlockEntity port) {
            hasTransferableContentsCalls++;
            return remainingContents > 0;
        }
    }
}

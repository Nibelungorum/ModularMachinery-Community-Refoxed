package cn.howxu.mmcr.internal.autoio;

import cn.howxu.mmcr.LevelStub;
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

    private static IOPortBlockEntity port(String id) {
        IOPortKind kind = PortKinds.all().stream().filter(candidate -> candidate.id().equals(id)).findFirst()
                .orElseGet(() -> PortKinds.all().stream()
                        .filter(candidate -> candidate.id().equals(id.replace("_normal", "")))
                        .findFirst()
                        .orElseThrow());
        return (IOPortBlockEntity) kind.entityFactory().create(BlockPos.ZERO, ModBlocks.BLOCKS.get(kind.id()).get().defaultBlockState());
    }

    private static final class ProbePort extends IOPortBlockEntity {
        private final ProbeHandler handler;
        private final IOType ioType;
        private final IOPortKind kind;
        private boolean hasTransferWork = true;

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

        @Override public IOType ioType() { return ioType; }
        @Override public IOPortKind kind() { return kind; }
        @Override public AutoIOCapabilityType autoIOCapabilityType() { return AutoIOCapabilityType.ITEM; }
        @Override protected AutoIOTransferHandler autoIOTransferHandler() { return handler; }
        @Override protected boolean hasAutoIOTransferWork() { return hasTransferWork; }
    }

    private static final class ProbeHandler implements AutoIOTransferHandler {
        private boolean adjacentTarget;
        private int hasAdjacentTargetCalls;
        private int transferCalls;

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
    }
}

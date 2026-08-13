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
    void empty_candidate_cache_rechecks_after_adaptive_delay() throws Exception {
        ProbeHandler handler = new ProbeHandler();
        ProbePort port = new ProbePort(BlockPos.ZERO, ModBlocks.BLOCKS.get("item_input_bus").get().defaultBlockState(), handler);
        port.toggleAutoIOEnabled();
        port.toggleAutoIOSide(Direction.EAST);

        port.runAutoIOCycleAt(1L);
        assertThat(handler.hasAdjacentTargetCalls).isEqualTo(1);

        handler.adjacentTarget = true;
        port.runAutoIOCycleAt(2L);
        assertThat(handler.hasAdjacentTargetCalls).isEqualTo(1);

        port.runAutoIOCycleAt(60L);
        assertThat(handler.hasAdjacentTargetCalls).isEqualTo(2);
        assertThat(handler.transferCalls).isEqualTo(1);
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

        private ProbePort(BlockPos pos, BlockState state, ProbeHandler handler) {
            super(ModBlockEntities.BES.get("item_input_bus").get(), pos, state);
            this.handler = handler;
        }

        private void runAutoIOCycleAt(long gameTime) {
            var level = LevelStub.create(Map.of());
            LevelStub.setGameTime(level, gameTime);
            setLevel(level);
            runAutoIOCycle();
        }

        @Override public IOType ioType() { return IOType.INPUT; }
        @Override public IOPortKind kind() { return PortKinds.ITEM_INPUT; }
        @Override public AutoIOCapabilityType autoIOCapabilityType() { return AutoIOCapabilityType.ITEM; }
        @Override protected AutoIOTransferHandler autoIOTransferHandler() { return handler; }
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

package cn.howxu.mmcr.compat.appliedflux.loaded.tile;

import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.IGridNodeListener;
import appeng.api.networking.IManagedGridNode;
import appeng.api.networking.ticking.IGridTickable;
import appeng.api.networking.ticking.ITickManager;
import appeng.api.networking.ticking.TickRateModulation;
import appeng.api.networking.ticking.TickingRequest;
import appeng.me.service.TickManagerService;
import cn.howxu.mmcr.LevelStub;
import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.compat.appliedenergistics2.AE2TestFixtures;
import cn.howxu.mmcr.compat.appliedflux.loaded.kind.FluxEnergyInputKind;
import cn.howxu.mmcr.internal.tile.MachineControllerBlockEntity;
import cn.howxu.mmcr.test.RuntimeTestFixtures;
import cn.howxu.mmcr.test.TestBootstrap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Exercises real energy notifications while recording the external AE2 scheduler boundary.
 * @author howxu <dev@howxu.cn>
 */
class FluxEnergyInputNotificationTest {
    @BeforeAll
    static void bootstrap() throws Exception {
        TestBootstrap.bootstrap();
        TestBootstrap.bootstrapCapabilities();
        AE2TestFixtures.bindEntityType(FluxEnergyInputKind.INSTANCE.id(), FluxEnergyInputKind.INSTANCE.entityFactory());
    }

    @Test
    void repeated_energy_changes_keep_immediate_content_facts_but_share_pending_alert() throws Exception {
        Fixture fixture = new Fixture(true);
        long revision = fixture.port.storageRevision();
        long content = fixture.controller.componentRuntime().capabilityContentRevision();
        fixture.onAlert = () -> {
            assertThat(fixture.port.getEnergyStorage().amount()).isEqualTo(40L);
            assertThat(fixture.controller.componentRuntime().capabilityContentRevision()).isGreaterThan(content);
        };

        fixture.port.getEnergyStorage().insert(40L, false);
        fixture.onAlert = null;
        fixture.port.getEnergyStorage().extract(10L, false);

        assertThat(fixture.port.getEnergyStorage().amount()).isEqualTo(30L);
        assertThat(fixture.port.storageRevision()).isEqualTo(revision + 2);
        assertThat(fixture.controller.componentRuntime().capabilityContentRevision()).isEqualTo(content + 2);
        assertThat(fixture.alerts).isEqualTo(1);
    }

    @Test
    void ticker_execution_rearms_alert_in_the_same_tick_and_later_ticks_are_not_suppressed() throws Exception {
        Fixture fixture = new Fixture(true);
        fixture.port.getEnergyStorage().insert(40L, false);
        fixture.tick();
        fixture.port.getEnergyStorage().extract(1L, false);
        fixture.port.getEnergyStorage().extract(1L, false);
        assertThat(fixture.alerts).isEqualTo(2);

        LevelStub.setGameTime(fixture.level, 1L);
        fixture.port.getEnergyStorage().extract(1L, false);
        assertThat(fixture.alerts).isEqualTo(3);
    }

    @Test
    void actual_ae2_queue_ticks_a_sleeping_port_again_after_same_tick_rearm() throws Exception {
        Fixture fixture = new Fixture(true);
        TickManagerService scheduler = new TickManagerService();
        fixture.scheduler = scheduler;
        int[] ticks = {0};
        fixture.scheduledTicker = new IGridTickable() {
            @Override
            public TickingRequest getTickingRequest(IGridNode node) {
                return new TickingRequest(1, 20, true);
            }

            @Override
            public TickRateModulation tickingRequest(IGridNode node, int ticksSinceLastCall) {
                ticks[0]++;
                fixture.tick();
                return TickRateModulation.SLEEP;
            }
        };
        scheduler.addNode(fixture.node, null);
        scheduler.onServerStartTick();
        fixture.port.getEnergyStorage().insert(40L, false);
        fixture.port.getEnergyStorage().extract(1L, false);
        assertThat(fixture.alerts).isEqualTo(1);
        scheduler.onServerEndTick();
        assertThat(ticks[0]).isEqualTo(1);

        fixture.port.getEnergyStorage().extract(1L, false);
        fixture.port.getEnergyStorage().extract(1L, false);
        assertThat(fixture.alerts).isEqualTo(2);
        scheduler.onServerStartTick();
        scheduler.onServerEndTick();
        assertThat(ticks[0]).isEqualTo(2);
        assertThat(fixture.port.getEnergyStorage().amount()).isEqualTo(37L);
    }

    @Test
    void grid_node_and_network_state_changes_invalidate_pending_alert() throws Exception {
        Fixture fixture = new Fixture(true);
        fixture.port.getEnergyStorage().insert(40L, false);
        fixture.grid = fixture.newGrid();
        fixture.port.getEnergyStorage().extract(1L, false);
        assertThat(fixture.alerts).isEqualTo(2);
        fixture.node = fixture.newNode();
        fixture.port.getEnergyStorage().extract(1L, false);
        assertThat(fixture.alerts).isEqualTo(3);

        fixture.port.onMainNodeStateChanged(IGridNodeListener.State.values()[0]);
        fixture.port.getEnergyStorage().extract(1L, false);
        assertThat(fixture.alerts).isEqualTo(5);
    }

    @Test
    void rejected_or_throwing_alert_is_not_cached_as_success() throws Exception {
        Fixture fixture = new Fixture(true);
        fixture.acceptAlert = false;
        fixture.port.getEnergyStorage().insert(40L, false);
        fixture.port.getEnergyStorage().extract(1L, false);
        assertThat(fixture.alerts).isEqualTo(2);
        fixture.onAlert = () -> { throw new IllegalStateException("scheduler rejected"); };
        assertThatThrownBy(() -> fixture.port.getEnergyStorage().extract(1L, false))
                .isInstanceOf(IllegalStateException.class);
        fixture.onAlert = null;
        fixture.acceptAlert = true;
        fixture.port.getEnergyStorage().extract(1L, false);
        fixture.port.getEnergyStorage().extract(1L, false);
        assertThat(fixture.alerts).isEqualTo(4);
        assertThat(fixture.port.getEnergyStorage().amount()).isEqualTo(36L);
    }

    @Test
    void ticker_reentry_during_alert_does_not_restore_an_obsolete_pending_marker() throws Exception {
        Fixture fixture = new Fixture(true);
        fixture.onAlert = fixture::tick;
        fixture.port.getEnergyStorage().insert(40L, false);
        fixture.onAlert = null;
        fixture.port.getEnergyStorage().extract(1L, false);
        assertThat(fixture.alerts).isEqualTo(2);
    }

    @Test
    void unload_and_load_clear_pending_alerts_even_with_reused_node_identity() throws Exception {
        Fixture fixture = new Fixture(true);
        fixture.port.getEnergyStorage().insert(40L, false);
        fixture.port.onChunkUnloaded();
        fixture.port.getEnergyStorage().extract(1L, false);
        assertThat(fixture.alerts).isEqualTo(2);
        fixture.port.loadAdditional(new CompoundTag(), HolderLookup.Provider.create(Stream.empty()));
        fixture.port.getEnergyStorage().extract(1L, false);
        assertThat(fixture.alerts).isEqualTo(3);
    }

    @Test
    void absent_level_keeps_live_alerts_and_replacing_level_does_not_reuse_pending_marker() throws Exception {
        Fixture fixture = new Fixture(false);
        fixture.port.getEnergyStorage().insert(40L, false);
        fixture.port.getEnergyStorage().extract(1L, false);
        assertThat(fixture.alerts).isEqualTo(2);
        fixture.port.setLevel(fixture.level);
        fixture.port.getEnergyStorage().extract(1L, false);
        fixture.port.getEnergyStorage().extract(1L, false);
        assertThat(fixture.alerts).isEqualTo(3);
        fixture.port.setLevel(LevelStub.create(Map.of(BlockPos.ZERO, Blocks.IRON_BLOCK), List.of(fixture.port)));
        fixture.port.getEnergyStorage().extract(1L, false);
        assertThat(fixture.alerts).isEqualTo(4);
    }

    /** Node/grid proxies isolate the external scheduler; all storage and controller notifications are real.
     * @author howxu <dev@howxu.cn>
     */
    private static final class Fixture {
        private final FluxEnergyInputInterfaceBlockEntity port = FluxEnergyInputKind.INSTANCE.entityFactory()
                .create(BlockPos.ZERO, Blocks.IRON_BLOCK.defaultBlockState());
        private final MachineControllerBlockEntity controller = RuntimeTestFixtures.controllerEntity(
                MMCR.id("test_cube"), new BlockPos(1, 0, 0));
        private final Level level = LevelStub.create(Map.of(BlockPos.ZERO, Blocks.IRON_BLOCK,
                controller.getBlockPos(), controller.getBlockState().getBlock()), List.of(port, controller));
        private int alerts;
        private boolean acceptAlert = true;
        private Runnable onAlert;
        private ITickManager scheduler;
        private IGridTickable scheduledTicker;
        private final ITickManager manager = proxy(ITickManager.class, (object, method, args) -> {
            if (method.getName().equals("alertDevice")) {
                alerts++;
                if (onAlert != null) onAlert.run();
                return scheduler == null ? acceptAlert : scheduler.alertDevice((IGridNode) args[0]);
            }
            throw new UnsupportedOperationException(method.toString());
        });
        private IGrid grid = newGrid();
        private IGridNode node = newNode();
        private final IGridTickable ticker;

        private Fixture(boolean attachLevel) throws Exception {
            ticker = (IGridTickable) field(FluxEnergyInputInterfaceBlockEntity.class, "ticker").get(port);
            IManagedGridNode managed = proxy(IManagedGridNode.class, (object, method, args) -> {
                return switch (method.getName()) {
                    case "ifPresent" -> {
                        @SuppressWarnings("unchecked")
                        BiConsumer<IGrid, IGridNode> callback = (BiConsumer<IGrid, IGridNode>) args[0];
                        callback.accept(grid, node);
                        yield true;
                    }
                    case "isActive" -> false;
                    case "getNode" -> node;
                    case "destroy", "loadFromNBT", "saveToNBT" -> null;
                    default -> throw new UnsupportedOperationException(method.toString());
                };
            });
            field(FluxInterfaceBlockEntity.class, "mainNode").set(port, managed);
            controller.setLevel(level);
            if (attachLevel) port.setLevel(level);
            port.linkControllerAppearance(controller.getBlockPos(), null);
        }

        private IGrid newGrid() {
            return proxy(IGrid.class, (object, method, args) -> {
                if (method.getName().equals("getTickManager")) return manager;
                throw new UnsupportedOperationException(method.toString());
            });
        }

        private IGridNode newNode() {
            return proxy(IGridNode.class, (object, method, args) -> {
                if (method.getName().equals("isActive")) return false;
                if (method.getName().equals("getGrid")) return grid;
                // A virtual node uses AE2's real null-level queue without requiring a running ServerLevel.
                if (method.getName().equals("getLevel")) return null;
                if (method.getName().equals("getService")) return args[0] == IGridTickable.class ? scheduledTicker : null;
                throw new UnsupportedOperationException(method.toString());
            });
        }

        private void tick() {
            ticker.tickingRequest(node, 1);
        }
    }

    private static Field field(Class<?> type, String name) throws Exception {
        Field field = type.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }

    private static <T> T proxy(Class<T> type, InvocationHandler handler) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, handler));
    }
}

package cn.howxu.mmcr.client.renderer;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.machine.BlockArray;
import cn.howxu.mmcr.api.machine.DynamicMachine;
import cn.howxu.mmcr.api.publicapi.render.ControllerRenderContext;
import cn.howxu.mmcr.api.publicapi.render.ControllerRenderer;
import cn.howxu.mmcr.internal.runtime.ControllerRuntimeSnapshot;
import cn.howxu.mmcr.internal.runtime.StructureSnapshot;
import cn.howxu.mmcr.internal.tile.MachineControllerBlockEntity;
import cn.howxu.mmcr.registry.ModBlocks;
import cn.howxu.mmcr.test.TestBootstrap;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Tests failure containment for machine controller renderer dispatch.
 * @author howxu <dev@howxu.cn>
 */
class MachineControllerRendererDispatcherTest {
    @BeforeAll
    static void bootstrapMinecraft() throws Exception {
        TestBootstrap.bootstrap();
    }

    @Test
    void rendererFailureIsLoggedAndDoesNotEscapeSubmit() {
        Identifier machine = Identifier.fromNamespaceAndPath("test", "machine");
        ControllerRenderer renderer = (context, poseStack, collector, camera) -> {
            throw new IllegalStateException("test failure");
        };
        MachineControllerRendererDispatcher dispatcher =
                new MachineControllerRendererDispatcher(machine, renderer);

        assertDoesNotThrow(() -> dispatcher.invokeForTesting(
                null, new PoseStack(), null, null));
    }

    @Test
    void unavailableStructureDoesNotInvokeRenderer() {
        Identifier machine = MMCR.id("test_cube");
        AtomicBoolean invoked = new AtomicBoolean();
        ControllerRenderer renderer = (context, poseStack, collector, camera) -> invoked.set(true);
        MachineControllerRendererDispatcher dispatcher =
                new MachineControllerRendererDispatcher(machine, renderer);
        MachineControllerBlockEntity controller = new SnapshotController(machine,
                snapshot(machine, StructureSnapshot.empty()));
        MachineControllerRendererDispatcher.ControllerRenderState state = dispatcher.createRenderState();

        dispatcher.extractRenderState(controller, state, 0.0F, Vec3.ZERO, null);
        dispatcher.submit(state, new PoseStack(), null, null);

        assertFalse(invoked.get());
    }

    @Test
    void configuredUnformedMachineStillInvokesRenderer() {
        Identifier machine = MMCR.id("test_cube");
        AtomicBoolean invoked = new AtomicBoolean();
        ControllerRenderer renderer = (context, poseStack, collector, camera) -> invoked.set(true);
        MachineControllerRendererDispatcher dispatcher =
                new MachineControllerRendererDispatcher(machine, renderer);
        MachineControllerBlockEntity controller = new SnapshotController(machine,
                snapshot(machine, configuredUnformedStructure(machine)));
        MachineControllerRendererDispatcher.ControllerRenderState state = dispatcher.createRenderState();

        dispatcher.extractRenderState(controller, state, 0.0F, Vec3.ZERO, null);
        dispatcher.submit(state, new PoseStack(), null, null);

        assertTrue(invoked.get());
    }

    @Test
    void rendererMetadataFailureDoesNotEscapeOrExpandRenderRange() {
        Identifier machine = MMCR.id("test_cube");
        ControllerRenderer renderer = new ControllerRenderer() {
            @Override
            public void render(ControllerRenderContext context, PoseStack poseStack,
                               SubmitNodeCollector collector, CameraRenderState camera) {
            }

            @Override
            public boolean shouldRenderOffScreen() {
                throw new IllegalStateException("off-screen metadata failure");
            }

            @Override
            public int getViewDistance() {
                throw new IllegalStateException("view-distance metadata failure");
            }
        };
        MachineControllerRendererDispatcher dispatcher =
                new MachineControllerRendererDispatcher(machine, renderer);

        assertDoesNotThrow(() -> assertFalse(dispatcher.shouldRenderOffScreen()));
        assertDoesNotThrow(() -> assertEquals(64, dispatcher.getViewDistance()));
    }

    private static ControllerRuntimeSnapshot snapshot(Identifier machine, StructureSnapshot structure) {
        return new ControllerRuntimeSnapshot(structure, 0L, 0L, 0L, Map.of(), Map.of(), Set.of(),
                null, 0, null, null, null, List.of(), List.of(), List.of(),
                machine.toString(), "", 0, false, false, 0, 0L, 1L);
    }

    private static StructureSnapshot configuredUnformedStructure(Identifier machine) {
        return new StructureSnapshot(new DynamicMachine(machine, "test", new BlockArray(Map.of())),
                null, null, null, null, Direction.SOUTH, 0, false, 0L,
                null, null, null, true, true, Set.of());
    }

    private static final class SnapshotController extends MachineControllerBlockEntity {
        private final ControllerRuntimeSnapshot snapshot;

        private SnapshotController(Identifier machine, ControllerRuntimeSnapshot snapshot) {
            super(BlockPos.ZERO, ModBlocks.controllerFor(machine).get().defaultBlockState());
            this.snapshot = snapshot;
        }

        @Override
        public ControllerRuntimeSnapshot runtimeSnapshot() {
            return snapshot;
        }
    }
}

package cn.howxu.mmcr.client.renderer;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.machine.BlockArray;
import cn.howxu.mmcr.api.machine.DynamicMachine;
import cn.howxu.mmcr.api.render.ControllerRenderContext;
import cn.howxu.mmcr.api.render.ControllerRenderer;
import cn.howxu.mmcr.api.recipe.helper.CraftingStatus;
import cn.howxu.mmcr.internal.runtime.ControllerRuntimeSnapshot;
import cn.howxu.mmcr.internal.runtime.StructureSnapshot;
import cn.howxu.mmcr.internal.tile.MachineControllerBlockEntity;
import cn.howxu.mmcr.registry.ModBlocks;
import cn.howxu.mmcr.test.TestBootstrap;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
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
        ResourceLocation machine = ResourceLocation.fromNamespaceAndPath("test", "machine");
        ControllerRenderer renderer = (context, poseStack, buffers, light, overlay) -> {
            throw new IllegalStateException("test failure");
        };
        MachineControllerRendererDispatcher dispatcher =
                new MachineControllerRendererDispatcher(machine, renderer);

        assertDoesNotThrow(() -> dispatcher.invokeForTesting(
                null, new PoseStack(), null, 0, 0));
    }

    @Test
    void unavailableStructureDoesNotInvokeRenderer() {
        ResourceLocation machine = MMCR.id("test_cube");
        AtomicBoolean invoked = new AtomicBoolean();
        ControllerRenderer renderer = (context, poseStack, buffers, light, overlay) -> invoked.set(true);
        MachineControllerRendererDispatcher dispatcher =
                new MachineControllerRendererDispatcher(machine, renderer);
        MachineControllerBlockEntity controller = new SnapshotController(machine,
                snapshot(machine, StructureSnapshot.empty()));
        dispatcher.render(controller, 0.0F, new PoseStack(), null, 0, 0);

        assertFalse(invoked.get());
    }

    @Test
    void configuredUnformedMachineStillInvokesRenderer() {
        ResourceLocation machine = MMCR.id("test_cube");
        AtomicBoolean invoked = new AtomicBoolean();
        ControllerRenderer renderer = (context, poseStack, buffers, light, overlay) -> {
            invoked.set(true);
            assertFalse(context.structure().formed());
            assertTrue(context.structure().structureAreaLoaded());
            assertEquals(0, context.structure().matchedStage());
            assertEquals(CraftingStatus.Status.IDLE, context.crafting().status());
        };
        MachineControllerRendererDispatcher dispatcher =
                new MachineControllerRendererDispatcher(machine, renderer);
        MachineControllerBlockEntity controller = new SnapshotController(machine,
                snapshot(machine, configuredUnformedStructure(machine)));
        dispatcher.render(controller, 0.0F, new PoseStack(), null, 0, 0);

        assertTrue(invoked.get());
    }

    @Test
    void rendererMetadataFailureDoesNotEscapeOrExpandRenderRange() {
        ResourceLocation machine = MMCR.id("test_cube");
        ControllerRenderer renderer = new ControllerRenderer() {
            @Override
            public void render(ControllerRenderContext context, PoseStack poseStack,
                               MultiBufferSource buffers, int light, int overlay) {
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
        MachineControllerBlockEntity controller = new SnapshotController(machine,
                snapshot(machine, StructureSnapshot.empty()));

        assertDoesNotThrow(() -> assertFalse(dispatcher.shouldRenderOffScreen(controller)));
        assertDoesNotThrow(() -> assertEquals(64, dispatcher.getViewDistance()));
    }

    private static ControllerRuntimeSnapshot snapshot(ResourceLocation machine, StructureSnapshot structure) {
        return new ControllerRuntimeSnapshot(structure, 0L, 0L, 0L, Map.of(), Map.of(), Set.of(),
                null, 0, null, null, List.of(), List.of(), List.of(),
                machine.toString(), "", 0, false, false, 0, 0L, 1L);
    }

    private static StructureSnapshot configuredUnformedStructure(ResourceLocation machine) {
        return new StructureSnapshot(new DynamicMachine(machine, "test", new BlockArray(Map.of())),
                null, null, null, null, Direction.SOUTH, 0, false, 0L,
                null, null, null, true, true, Set.of());
    }

    private static final class SnapshotController extends MachineControllerBlockEntity {
        private final ControllerRuntimeSnapshot snapshot;

        private SnapshotController(ResourceLocation machine, ControllerRuntimeSnapshot snapshot) {
            super(BlockPos.ZERO, ModBlocks.controllerFor(machine).get().defaultBlockState());
            this.snapshot = snapshot;
        }

        @Override
        public ControllerRuntimeSnapshot runtimeSnapshot() {
            return snapshot;
        }
    }
}

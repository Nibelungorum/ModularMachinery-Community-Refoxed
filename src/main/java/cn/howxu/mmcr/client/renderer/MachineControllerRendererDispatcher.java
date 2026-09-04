package cn.howxu.mmcr.client.renderer;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.internal.block.MachineControllerBlock;
import cn.howxu.mmcr.api.publicapi.render.ControllerRenderContext;
import cn.howxu.mmcr.api.publicapi.render.ControllerRenderer;
import cn.howxu.mmcr.internal.runtime.ControllerRuntimeSnapshot;
import cn.howxu.mmcr.internal.runtime.StructureSnapshot;
import cn.howxu.mmcr.internal.tile.MachineControllerBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/** Dispatches a machine controller's published state to its public renderer.
 * @author howxu <dev@howxu.cn>
 */
public final class MachineControllerRendererDispatcher
        implements BlockEntityRenderer<MachineControllerBlockEntity, MachineControllerRendererDispatcher.ControllerRenderState> {
    private final Identifier machineId;
    private final ControllerRenderer renderer;

    public MachineControllerRendererDispatcher(Identifier machineId, ControllerRenderer renderer) {
        this.machineId = machineId;
        this.renderer = renderer;
    }

    @Override
    public ControllerRenderState createRenderState() {
        return new ControllerRenderState();
    }

    @Override
    public void extractRenderState(MachineControllerBlockEntity controller, ControllerRenderState state,
                                   float partialTick, Vec3 cameraPosition,
                                   @Nullable ModelFeatureRenderer.CrumblingOverlay breakProgress) {
        BlockEntityRenderer.super.extractRenderState(controller, state, partialTick, cameraPosition, breakProgress);
        state.context = null;

        ControllerRuntimeSnapshot snapshot = controller.runtimeSnapshot();
        if (!machineId.toString().equals(snapshot.machineId())) return;

        StructureSnapshot structure = snapshot.structure();
        if (structure.configuredMachine() == null) return;

        BlockState blockState = controller.getBlockState();
        Direction facing = structure.facing();
        if (blockState.hasProperty(MachineControllerBlock.FACING)) {
            facing = blockState.getValue(MachineControllerBlock.FACING);
        }

        state.context = new ControllerRenderContext(
                controller.getLevel(), controller.getBlockPos(), machineId, facing,
                structure, snapshot.crafting(), snapshot.dataStorageValues(), state.lightCoords, partialTick);
    }

    @Override
    public void submit(ControllerRenderState state, PoseStack poseStack,
                       SubmitNodeCollector nodeCollector, CameraRenderState camera) {
        if (state.context != null) {
            invokeForTesting(state.context, poseStack, nodeCollector, camera);
        }
    }

    @Override
    public boolean shouldRenderOffScreen() {
        try {
            return renderer.shouldRenderOffScreen();
        } catch (RuntimeException exception) {
            MMCR.LOG.error("Controller renderer off-screen metadata failed for machine {}", machineId, exception);
            return BlockEntityRenderer.super.shouldRenderOffScreen();
        }
    }

    @Override
    public int getViewDistance() {
        try {
            return renderer.getViewDistance();
        } catch (RuntimeException exception) {
            MMCR.LOG.error("Controller renderer view-distance metadata failed for machine {}", machineId, exception);
            return BlockEntityRenderer.super.getViewDistance();
        }
    }

    @Override
    public AABB getRenderBoundingBox(MachineControllerBlockEntity controller) {
        return shouldRenderOffScreen() ? AABB.INFINITE : new AABB(controller.getBlockPos());
    }

    void invokeForTesting(@Nullable ControllerRenderContext context, PoseStack poseStack,
                          SubmitNodeCollector nodeCollector, CameraRenderState camera) {
        try {
            renderer.render(context, poseStack, nodeCollector, camera);
        } catch (RuntimeException exception) {
            MMCR.LOG.error("Controller renderer failed for machine {}", machineId, exception);
        }
    }

    public static final class ControllerRenderState extends BlockEntityRenderState {
        private @Nullable ControllerRenderContext context;
    }
}

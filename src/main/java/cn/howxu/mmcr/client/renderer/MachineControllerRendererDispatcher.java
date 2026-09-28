package cn.howxu.mmcr.client.renderer;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.internal.block.MachineControllerBlock;
import cn.howxu.mmcr.api.publicapi.render.ControllerRenderContext;
import cn.howxu.mmcr.api.publicapi.render.ControllerRenderer;
import cn.howxu.mmcr.internal.runtime.ControllerRuntimeSnapshot;
import cn.howxu.mmcr.internal.tile.MachineControllerBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/** Dispatches a machine controller's published state to its public renderer.
 * @author howxu <dev@howxu.cn>
 */
public final class MachineControllerRendererDispatcher
        implements BlockEntityRenderer<MachineControllerBlockEntity> {
    private final ResourceLocation machineId;
    private final ControllerRenderer renderer;

    public MachineControllerRendererDispatcher(ResourceLocation machineId, ControllerRenderer renderer) {
        this.machineId = machineId;
        this.renderer = renderer;
    }

    @Override
    public void render(MachineControllerBlockEntity controller, float partialTick, PoseStack poseStack,
                       MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        ControllerRuntimeSnapshot snapshot = controller.runtimeSnapshot();
        if (!machineId.toString().equals(snapshot.machineId())) return;

        var structure = snapshot.structure();
        if (structure.configuredMachine() == null) return;

        BlockState blockState = controller.getBlockState();
        Direction facing = structure.facing();
        if (blockState.hasProperty(MachineControllerBlock.FACING)) {
            facing = blockState.getValue(MachineControllerBlock.FACING);
        }

        ControllerRenderContext context = new ControllerRenderContext(
                controller.getBlockPos(), machineId, facing,
                new ControllerRenderContext.StructureView(
                        structure.formed(), structure.structureAreaLoaded(), structure.matchedStage()),
                new ControllerRenderContext.CraftingView(
                        snapshot.crafting().recipeId(), snapshot.crafting().status().getStatus(),
                        snapshot.crafting().status().getUnlocMessage(), snapshot.crafting().failure(),
                        snapshot.crafting().tick(), snapshot.crafting().totalTick(),
                        snapshot.crafting().parallelism(), snapshot.crafting().maxParallelism(),
                        snapshot.crafting().recipeLocked(), snapshot.crafting().lockedRecipeId()),
                snapshot.dataStorageValues(), packedLight, partialTick);
        invokeForTesting(context, poseStack, bufferSource, packedLight, packedOverlay);
    }

    @Override
    public boolean shouldRenderOffScreen(MachineControllerBlockEntity controller) {
        try {
            return renderer.shouldRenderOffScreen();
        } catch (RuntimeException exception) {
            MMCR.LOG.error("Controller renderer off-screen metadata failed for machine {}", machineId, exception);
            return BlockEntityRenderer.super.shouldRenderOffScreen(controller);
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
        return shouldRenderOffScreen(controller) ? AABB.INFINITE : new AABB(controller.getBlockPos());
    }

    void invokeForTesting(ControllerRenderContext context, PoseStack poseStack,
                          MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        try {
            renderer.render(context, poseStack, bufferSource, packedLight, packedOverlay);
        } catch (RuntimeException exception) {
            MMCR.LOG.error("Controller renderer failed for machine {}", machineId, exception);
        }
    }

}

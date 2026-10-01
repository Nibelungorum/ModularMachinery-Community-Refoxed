package cn.howxu.mmcr.publicapi.client.render;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.data.DataValue;
import cn.howxu.mmcr.api.capability.status.ExecutionStatus;
import cn.howxu.mmcr.api.capability.status.StatusSeverity;
import cn.howxu.mmcr.api.recipe.helper.CraftingStatus;
import cn.howxu.mmcr.internal.api.facade.client.RenderAdapters;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** The Java callback receives a typed public state view and keeps renderer metadata.
 * @author howxu <dev@howxu.cn>
 */
class ControllerRenderBoundaryTest {
    @Test
    void wraps_published_state_at_callback_boundary_and_delegates_metadata() {
        var received = new AtomicReference<ControllerRenderContext>();
        ControllerRenderer callback = new ControllerRenderer() {
            public void render(ControllerRenderContext context, PoseStack poses,
                               MultiBufferSource buffers, int light, int overlay) {
                received.set(context);
            }
            public boolean shouldRenderOffScreen() { return true; }
            public int getViewDistance() { return 512; }
        };
        var renderer = RenderAdapters.toCore(callback);
        var core = new cn.howxu.mmcr.api.render.ControllerRenderContext(
                new BlockPos(2, 3, 4), MMCR.id("render_boundary"), Direction.NORTH,
                new cn.howxu.mmcr.api.render.ControllerRenderContext.StructureView(true, true, 2),
                new cn.howxu.mmcr.api.render.ControllerRenderContext.CraftingView(
                        MMCR.id("render_recipe"), CraftingStatus.Status.CRAFTING, "mmcr.status.crafting",
                        new ExecutionStatus(MMCR.id("render_failure"), StatusSeverity.BLOCKED,
                                MMCR.id("render_source"), Map.of("required", "12")),
                        5, 20, 4, 8),
                Map.of("stored", DataValue.of(42L)), 15728880, 0.5F);
        renderer.render(core, null, null, 0, 0);
        var view = received.get();
        assertThat(view.controllerPos()).isEqualTo(core.controllerPos());
        assertThat(view.machineId()).isEqualTo(core.machineId());
        assertThat(view.structure().formed()).isTrue();
        assertThat(view.crafting().status()).isEqualTo(ControllerRenderContext.Status.CRAFTING);
        assertThat(view.crafting().recipeId()).isEqualTo(core.crafting().recipeId());
        assertThat(view.crafting().failure().id()).isEqualTo(MMCR.id("render_failure"));
        assertThat(view.crafting().failure().details()).containsEntry("required", "12");
        assertThat(view.dataStorageValues().get("stored").longValue()).isEqualTo(42L);
        assertThatThrownBy(() -> view.dataStorageValues().clear()).isInstanceOf(UnsupportedOperationException.class);
        assertThat(renderer.shouldRenderOffScreen()).isTrue();
        assertThat(renderer.getViewDistance()).isEqualTo(512);
    }
}

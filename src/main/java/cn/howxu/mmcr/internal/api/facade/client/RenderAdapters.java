package cn.howxu.mmcr.internal.api.facade.client;

import cn.howxu.mmcr.api.data.view.DataValue;
import cn.howxu.mmcr.internal.api.facade.data.StorageAdapters;
import cn.howxu.mmcr.internal.api.facade.runtime.IoAdapters;
import cn.howxu.mmcr.publicapi.client.render.ControllerRenderContext;
import cn.howxu.mmcr.publicapi.client.render.ControllerRenderer;
import cn.howxu.mmcr.publicapi.data.DataKey;
import cn.howxu.mmcr.publicapi.runtime.RuntimeFailure;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

/** Wrap published core state only at the Java rendering callback boundary.
 * @author howxu <dev@howxu.cn>
 */
public final class RenderAdapters {
    private RenderAdapters() { }
    public static cn.howxu.mmcr.api.render.ControllerRenderer toCore(ControllerRenderer renderer) {
        Objects.requireNonNull(renderer, "renderer");
        return new cn.howxu.mmcr.api.render.ControllerRenderer() {
            public void render(cn.howxu.mmcr.api.render.ControllerRenderContext context, PoseStack poses,
                               MultiBufferSource buffers, int light, int overlay) {
                renderer.render(wrap(context), poses, buffers, light, overlay);
            }
            public boolean shouldRenderOffScreen() { return renderer.shouldRenderOffScreen(); }
            public int getViewDistance() { return renderer.getViewDistance(); }
        };
    }
    public static ControllerRenderContext wrap(cn.howxu.mmcr.api.render.ControllerRenderContext context) {
        return new ContextView(Objects.requireNonNull(context, "context"));
    }
    private record ContextView(cn.howxu.mmcr.api.render.ControllerRenderContext core) implements ControllerRenderContext {
        public BlockPos controllerPos() { return core.controllerPos(); }
        public ResourceLocation machineId() { return core.machineId(); }
        public @Nullable Direction facing() { return core.facing(); }
        public StructureView structure() { return new Structure(core.structure()); }
        public CraftingView crafting() { return new Crafting(core.crafting()); }
        public Map<String, DataKey> dataStorageValues() {
            Map<String, DataKey> values = new LinkedHashMap<>();
            core.dataStorageValues().forEach((key, value) -> values.put(key, StorageAdapters.wrap(DataValue.fromInternal(value))));
            return Map.copyOf(values);
        }
        public int lightCoords() { return core.lightCoords(); }
        public float partialTick() { return core.partialTick(); }
    }
    private record Structure(cn.howxu.mmcr.api.render.ControllerRenderContext.StructureView core)
            implements ControllerRenderContext.StructureView {
        public boolean formed() { return core.formed(); }
        public boolean structureAreaLoaded() { return core.structureAreaLoaded(); }
        public int matchedStage() { return core.matchedStage(); }
    }
    private record Crafting(cn.howxu.mmcr.api.render.ControllerRenderContext.CraftingView core)
            implements ControllerRenderContext.CraftingView {
        public @Nullable ResourceLocation recipeId() { return core.recipeId(); }
        public ControllerRenderContext.Status status() {
            return switch (core.status()) {
                case IDLE -> ControllerRenderContext.Status.IDLE;
                case CRAFTING -> ControllerRenderContext.Status.CRAFTING;
                case MISSING_STRUCTURE -> ControllerRenderContext.Status.MISSING_STRUCTURE;
                case CHUNK_UNLOADED -> ControllerRenderContext.Status.CHUNK_UNLOADED;
                case NO_RECIPE -> ControllerRenderContext.Status.NO_RECIPE;
                case PAUSED -> ControllerRenderContext.Status.PAUSED;
            };
        }
        public String statusMessage() { return core.statusMessage(); }
        public @Nullable RuntimeFailure failure() { return IoAdapters.wrap(core.failure()); }
        public int tick() { return core.tick(); }
        public int totalTick() { return core.totalTick(); }
        public long parallelism() { return core.parallelism(); }
        public long maxParallelism() { return core.maxParallelism(); }
    }
}

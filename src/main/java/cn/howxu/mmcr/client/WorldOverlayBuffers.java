package cn.howxu.mmcr.client;

import cn.howxu.mmcr.MMCR;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.LevelEvent;
import org.jetbrains.annotations.Nullable;

import java.util.function.Consumer;

/**
 * Draws camera-relative overlays within the render stage that supplied their camera.
 * Iris defers per-type flushes on the global entity buffer, so these overlays use their own.
 *
 * @author HowXu {@literal <dev@howxu.cn>}
 */
@EventBusSubscriber(modid = MMCR.MODID, value = Dist.CLIENT)
public final class WorldOverlayBuffers {
    private static @Nullable WorldOverlayBuffers active;
    private final ByteBufferBuilder storage = new ByteBufferBuilder(RenderType.TRANSIENT_BUFFER_SIZE);
    private final MultiBufferSource.BufferSource buffers = MultiBufferSource.immediate(storage);

    private WorldOverlayBuffers() {}

    static void draw(RenderType type, Consumer<VertexConsumer> geometry) {
        if (active == null) active = new WorldOverlayBuffers();
        var buffers = active.buffers;
        geometry.accept(buffers.getBuffer(type));
        buffers.endBatch(type);
    }

    @SubscribeEvent
    public static void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel().isClientSide() && active != null) {
            active.storage.close();
            active = null;
        }
    }
}

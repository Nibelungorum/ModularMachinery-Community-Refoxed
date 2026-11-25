package cn.howxu.mmcr.compat.fluxnetworks;

import cn.howxu.mmcr.MMCR;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.serialization.MapCodec;
import net.minecraft.client.renderer.texture.SpriteContents;
import net.minecraft.client.renderer.texture.atlas.SpriteSource;
import net.minecraft.client.renderer.texture.atlas.SpriteSourceType;
import net.minecraft.client.resources.metadata.animation.FrameSize;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceMetadata;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.client.event.RegisterSpriteSourceTypesEvent;

/**
 * Pads each native Flux core frame onto a transparent casing face at atlas reload.
 * @author HowXu {@literal <dev@howxu.cn>}
 */
public enum FluxFaceSpriteSource implements SpriteSource {
    INSTANCE;

    private static final SpriteSourceType TYPE = new SpriteSourceType(MapCodec.unit(INSTANCE));

    public static void register(RegisterSpriteSourceTypesEvent event) {
        event.register(MMCR.id("flux_network_faces"), TYPE);
    }

    @Override
    public SpriteSourceType type() { return TYPE; }

    @Override
    public void run(ResourceManager resources, Output output) {
        for (var core : FluxNetworkOverlay.values()) {
            resources.getResource(TEXTURE_ID_CONVERTER.idToFile(core.texture)).ifPresent(resource ->
                    output.add(core.worldSprite(), loader -> loader.loadSprite(core.worldSprite(), resource,
                            (id, frame, image, metadata) -> pad(core, id, frame, image, metadata))));
        }
    }

    private static SpriteContents pad(FluxNetworkOverlay core, ResourceLocation id, FrameSize frame,
                                      NativeImage image, ResourceMetadata metadata) {
        try (image) {
            int width = Mth.roundToward(frame.width(), core.faceGrid);
            int height = Mth.roundToward(frame.height(), core.faceGrid);
            int coreWidth = width * core.faceSize / 16, coreHeight = height * core.faceSize / 16;
            int left = (width - coreWidth) / 2, top = (height - coreHeight) / 2;
            int columns = image.getWidth() / frame.width(), rows = image.getHeight() / frame.height();
            var padded = new NativeImage(columns * width, rows * height, true);
            try {
                for (int row = 0; row < rows; row++) {
                    for (int col = 0; col < columns; col++) {
                        for (int y = 0; y < coreHeight; y++) {
                            int srcY = row * frame.height() + sample(y, coreHeight, core.minV, core.uvSize, frame.height());
                            for (int x = 0; x < coreWidth; x++) {
                                int srcX = col * frame.width() + sample(x, coreWidth, core.minU, core.uvSize, frame.width());
                                padded.setPixelRGBA(col * width + left + x, row * height + top + y, image.getPixelRGBA(srcX, srcY));
                            }
                        }
                    }
                }
                return new SpriteContents(id, new FrameSize(width, height), padded, metadata);
            } catch (RuntimeException | Error failure) {
                padded.close();
                throw failure;
            }
        }
    }

    private static int sample(int pixel, int extent, int uvStart, int uvSize, int frameSize) {
        return (int) ((uvStart + (pixel + 0.5) * uvSize / extent) * frameSize / 16);
    }
}

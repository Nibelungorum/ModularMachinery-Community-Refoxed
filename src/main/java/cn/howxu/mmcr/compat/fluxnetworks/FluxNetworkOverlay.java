package cn.howxu.mmcr.compat.fluxnetworks;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.internal.port.IOPortKind;
import com.google.common.collect.ImmutableList;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.client.model.IQuadTransformer;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

import java.util.Map;

/** Native Flux core regions shared by the item geometry and the block atlas.
 * @author howxu <dev@howxu.cn>
 */
public enum FluxNetworkOverlay {
    POINT("flux_point_on", true, false),
    POINT_COLOUR("flux_point_colour", true, true),
    PLUG("flux_plug_on", false, false),
    PLUG_COLOUR("flux_plug_colour", false, true);

    // Keep network tint separate from tint indices on the borrowed casing/CTM model.
    public static final int NETWORK_TINT_INDEX = 1000;
    private static final Map<ResourceLocation, FluxNetworkOverlay> BY_TEXTURE = Map.of(
            POINT.texture, POINT, POINT_COLOUR.texture, POINT_COLOUR,
            PLUG.texture, PLUG, PLUG_COLOUR.texture, PLUG_COLOUR);

    final ResourceLocation texture;
    final int faceSize, faceGrid, minU, minV, uvSize;
    private final ResourceLocation worldSprite;
    private final int tintIndex;

    FluxNetworkOverlay(String name, boolean point, boolean colour) {
        texture = ResourceLocation.fromNamespaceAndPath(FluxNetworksIds.MOD_ID, "block/" + name);
        worldSprite = MMCR.id("block/fluxnetworks/" + name);
        faceSize = point ? 5 : 8;
        faceGrid = point ? 32 : 4; // Centred margins are 11/32 for Point and 1/4 for Plug.
        minU = point ? 12 : 8;
        minV = point ? 6 : 2;
        uvSize = point ? 4 : 8;
        tintIndex = colour ? NETWORK_TINT_INDEX : -1;
    }

    public static boolean isPort(@Nullable IOPortKind kind) {
        return kind != null && (FluxNetworksIds.INPUT.equals(kind.id()) || FluxNetworksIds.OUTPUT.equals(kind.id()));
    }

    public static ImmutableList<ResourceLocation> textures(IOPortKind kind) {
        return FluxNetworksIds.INPUT.equals(kind.id())
                ? ImmutableList.of(POINT.texture, POINT_COLOUR.texture) : ImmutableList.of(PLUG.texture, PLUG_COLOUR.texture);
    }

    public static @Nullable FluxNetworkOverlay forTexture(ResourceLocation texture) {
        return BY_TEXTURE.get(texture);
    }

    public ResourceLocation worldSprite() { return worldSprite; }
    public int tintIndex() { return tintIndex; }

    public static float[] uv(Direction face, Vector3f point) {
        return new float[]{u(face, point), v(face, point)};
    }

    private static float u(Direction face, Vector3f point) {
        return switch (face) {
            case EAST -> 1F - point.z;
            case WEST -> point.z;
            case NORTH -> 1F - point.x;
            default -> point.x;
        };
    }

    private static float v(Direction face, Vector3f point) {
        return switch (face) {
            case UP -> 1F - point.z;
            case DOWN -> point.z;
            default -> 1F - point.y;
        };
    }

    public BakedQuad itemQuad(BakedQuad base, TextureAtlasSprite sprite) {
        float size = faceSize / 16F;
        float min = (1F - size) / 2F;
        Direction direction = base.getDirection();
        int axis = direction.getAxis().ordinal();
        int[] data = base.getVertices().clone();
        Vector3f vertex = new Vector3f();
        for (int i = 0; i < data.length; i += IQuadTransformer.STRIDE) {
            int pos = i + IQuadTransformer.POSITION;
            for (int j = 0; j < 3; j++) {
                float coordinate = Mth.clamp(Float.intBitsToFloat(data[pos + j]), 0F, 1F);
                vertex.setComponent(j, coordinate);
                if (j != axis) data[pos + j] = Float.floatToRawIntBits(min + coordinate * size);
            }
            float u = (minU + u(direction, vertex) * uvSize) / 16F;
            float v = (minV + v(direction, vertex) * uvSize) / 16F;
            data[i + IQuadTransformer.COLOR] = -1;
            data[i + IQuadTransformer.UV0] = Float.floatToRawIntBits(sprite.getU(Mth.lerp(sprite.uvShrinkRatio(), u, (minU + uvSize / 2F) / 16F)));
            data[i + IQuadTransformer.UV0 + 1] = Float.floatToRawIntBits(sprite.getV(Mth.lerp(sprite.uvShrinkRatio(), v, (minV + uvSize / 2F) / 16F)));
        }
        return new BakedQuad(data, tintIndex, direction, sprite, base.isShade(), base.hasAmbientOcclusion());
    }
}

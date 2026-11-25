package cn.howxu.mmcr.compat.fluxnetworks;

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

/** Native Flux core faces centred over the dynamic MMCR casing.
 * @author howxu <dev@howxu.cn>
 */
public final class FluxNetworkOverlay {
    // Keep network tint separate from tint indices on the borrowed casing/CTM model.
    public static final int NETWORK_TINT_INDEX = 1000;
    private static final ResourceLocation POINT = texture("flux_point_on");
    private static final ResourceLocation POINT_COLOUR = texture("flux_point_colour");
    private static final ResourceLocation PLUG = texture("flux_plug_on");
    private static final ResourceLocation PLUG_COLOUR = texture("flux_plug_colour");

    private FluxNetworkOverlay() {
    }

    public static boolean isPort(@Nullable IOPortKind kind) {
        return kind != null && (FluxNetworksIds.INPUT.equals(kind.id()) || FluxNetworksIds.OUTPUT.equals(kind.id()));
    }

    public static ImmutableList<ResourceLocation> textures(IOPortKind kind) {
        return FluxNetworksIds.INPUT.equals(kind.id())
                ? ImmutableList.of(POINT, POINT_COLOUR) : ImmutableList.of(PLUG, PLUG_COLOUR);
    }

    public static @Nullable BakedQuad quad(ResourceLocation texture, BakedQuad base, TextureAtlasSprite sprite,
                                           float outwardOffset) {
        boolean point = POINT.equals(texture) || POINT_COLOUR.equals(texture);
        if (!point && !PLUG.equals(texture) && !PLUG_COLOUR.equals(texture)) return null;
        float size = (point ? 5F : 8F) / 16F;
        float min = (1F - size) / 2F;
        float minU = (point ? 12F : 8F) / 16F;
        float minV = (point ? 6F : 2F) / 16F;
        float uvSize = (point ? 4F : 8F) / 16F;
        Direction direction = base.getDirection();
        int axis = direction.getAxis().ordinal();
        float offset = direction.getAxisDirection().getStep() * outwardOffset;
        int[] data = base.getVertices().clone();
        Vector3f vertex = new Vector3f();
        for (int i = 0; i < data.length; i += IQuadTransformer.STRIDE) {
            int pos = i + IQuadTransformer.POSITION;
            for (int j = 0; j < 3; j++) {
                float coordinate = Float.intBitsToFloat(data[pos + j]);
                float clamped = Mth.clamp(coordinate, 0F, 1F);
                vertex.setComponent(j, clamped);
                // Contract CTM tiles tangentially; keep depth relative to the original face.
                data[pos + j] = Float.floatToRawIntBits(j == axis ? coordinate + offset : min + clamped * size);
            }
            float u = switch (direction) {
                case EAST -> 1F - vertex.z;
                case WEST -> vertex.z;
                case NORTH -> 1F - vertex.x;
                default -> vertex.x;
            };
            float v = switch (direction) {
                case UP -> 1F - vertex.z;
                case DOWN -> vertex.z;
                default -> 1F - vertex.y;
            };
            u = minU + u * uvSize;
            v = minV + v * uvSize;
            data[i + IQuadTransformer.COLOR] = -1;
            data[i + IQuadTransformer.UV0] = Float.floatToRawIntBits(sprite.getU(Mth.lerp(sprite.uvShrinkRatio(), u, minU + uvSize / 2F)));
            data[i + IQuadTransformer.UV0 + 1] = Float.floatToRawIntBits(sprite.getV(Mth.lerp(sprite.uvShrinkRatio(), v, minV + uvSize / 2F)));
        }
        int tint = POINT_COLOUR.equals(texture) || PLUG_COLOUR.equals(texture) ? NETWORK_TINT_INDEX : -1;
        return new BakedQuad(data, tint, direction, sprite, base.isShade(), base.hasAmbientOcclusion());
    }

    private static ResourceLocation texture(String name) {
        return ResourceLocation.fromNamespaceAndPath(FluxNetworksIds.MOD_ID, "block/" + name);
    }
}

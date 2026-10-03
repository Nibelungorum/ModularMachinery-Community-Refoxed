package cn.howxu.mmcr.compat.fluxnetworks;

import cn.howxu.mmcr.internal.port.IOPortKind;
import com.google.common.collect.ImmutableList;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.client.model.pipeline.QuadBakingVertexConsumer;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

import java.util.List;

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

    public static @Nullable BakedQuad quad(ResourceLocation texture, Direction direction,
                                           TextureAtlasSprite sprite, float grow) {
        boolean point = POINT.equals(texture) || POINT_COLOUR.equals(texture);
        if (!point && !PLUG.equals(texture) && !PLUG_COLOUR.equals(texture)) return null;
        float size = (point ? 5F : 8F) / 16F;
        float min = (1F - size) / 2F;
        float minU = (point ? 12F : 8F) / 16F;
        float minV = (point ? 6F : 2F) / 16F;
        float uvSize = (point ? 4F : 8F) / 16F;
        List<Vector3f> points = switch (direction) {
            case EAST -> List.of(new Vector3f(1, 1, 1), new Vector3f(1, 0, 1), new Vector3f(1, 0, 0), new Vector3f(1, 1, 0));
            case WEST -> List.of(new Vector3f(0, 1, 0), new Vector3f(0, 0, 0), new Vector3f(0, 0, 1), new Vector3f(0, 1, 1));
            case UP -> List.of(new Vector3f(0, 1, 1), new Vector3f(1, 1, 1), new Vector3f(1, 1, 0), new Vector3f(0, 1, 0));
            case DOWN -> List.of(new Vector3f(0, 0, 0), new Vector3f(1, 0, 0), new Vector3f(1, 0, 1), new Vector3f(0, 0, 1));
            case SOUTH -> List.of(new Vector3f(0, 1, 1), new Vector3f(0, 0, 1), new Vector3f(1, 0, 1), new Vector3f(1, 1, 1));
            case NORTH -> List.of(new Vector3f(1, 1, 0), new Vector3f(1, 0, 0), new Vector3f(0, 0, 0), new Vector3f(0, 1, 0));
        };
        QuadBakingVertexConsumer builder = new QuadBakingVertexConsumer();
        builder.setSprite(sprite);
        builder.setDirection(direction);
        builder.setShade(true);
        builder.setHasAmbientOcclusion(true);
        builder.setTintIndex(POINT_COLOUR.equals(texture) || PLUG_COLOUR.equals(texture) ? NETWORK_TINT_INDEX : -1);
        Vec3i normal = direction.getNormal();
        for (Vector3f vertex : points) {
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
            vertex.mul(size).add(min, min, min);
            vertex.setComponent(direction.getAxis().ordinal(),
                    direction.getAxisDirection() == Direction.AxisDirection.POSITIVE ? 1F + grow : -grow);
            builder.addVertex(vertex.x, vertex.y, vertex.z);
            builder.setColor(255, 255, 255, 255);
            builder.setNormal(normal.getX(), normal.getY(), normal.getZ());
            builder.setUv(sprite.getU(Mth.lerp(sprite.uvShrinkRatio(), u, minU + uvSize / 2F)),
                    sprite.getV(Mth.lerp(sprite.uvShrinkRatio(), v, minV + uvSize / 2F)));
        }
        return builder.bakeQuad();
    }

    private static ResourceLocation texture(String name) {
        return ResourceLocation.fromNamespaceAndPath(FluxNetworksIds.MOD_ID, "block/" + name);
    }
}

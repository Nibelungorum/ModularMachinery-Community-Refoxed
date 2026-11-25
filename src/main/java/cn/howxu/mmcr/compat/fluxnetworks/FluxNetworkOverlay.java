package cn.howxu.mmcr.compat.fluxnetworks;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.internal.port.IOPortKind;
import com.google.common.collect.ImmutableList;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

import java.util.Map;

/** Native Flux core regions baked into padded face sprites for blocks and items.
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
}

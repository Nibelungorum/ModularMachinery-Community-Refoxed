package cn.howxu.mmcr.client.model;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.internal.block.MachineControllerBlock;
import com.mojang.serialization.MapCodec;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.resources.model.ModelBaker;
import net.minecraft.client.resources.model.ModelDebugName;
import net.minecraft.client.resources.model.SimpleModelWrapper;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.client.resources.model.geometry.QuadCollection;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.client.resources.model.sprite.MaterialBaker;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.DynamicBlockStateModel;
import net.neoforged.neoforge.client.model.block.CustomUnbakedBlockStateModel;
import net.neoforged.neoforge.client.model.pipeline.QuadBakingVertexConsumer;
import net.neoforged.neoforge.model.data.ModelData;
import org.joml.Vector3f;

import java.util.List;

/**
 * Dynamic block-state model for shared machine controller and I/O port overlays.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class DynamicOverlayModelLoader implements DynamicBlockStateModel {
    public static final Identifier CONTROLLER_ID = MMCR.id("dynamic_controller_overlay");
    public static final Identifier PORT_ID = MMCR.id("dynamic_port_overlay");
    public static final MapCodec<Unbaked> CONTROLLER_CODEC = MapCodec.unit(() -> new Unbaked(DynamicOverlayBakedModel.Kind.CONTROLLER));
    public static final MapCodec<Unbaked> PORT_CODEC = MapCodec.unit(() -> new Unbaked(DynamicOverlayBakedModel.Kind.PORT));

    private static final Material FALLBACK_PARTICLE = new Material(MMCR.id("block/basic_casing"));
    static final float OVERLAY_GROW = 0.002f;

    private final DynamicOverlayBakedModel.Kind kind;
    private final MaterialBaker materials;
    private final Material.Baked particle;
    private final ModelDebugName debugName = getClass()::toString;

    private DynamicOverlayModelLoader(DynamicOverlayBakedModel.Kind kind, MaterialBaker materials) {
        this.kind = kind;
        this.materials = materials;
        this.particle = materials.get(FALLBACK_PARTICLE, debugName);
    }

    @Override
    public void collectParts(BlockAndTintGetter level, BlockPos pos, BlockState state, RandomSource random,
                             List<BlockStateModelPart> parts) {
        DynamicOverlayBakedModel.TextureSet textures = textures(level, pos, state);
        QuadCollection.Builder quads = new QuadCollection.Builder();

        Material.Baked base = material(textures.base());
        Material.Baked overlay = material(textures.overlay());
        Direction overlayFace = overlayFace(state);
        for (Direction direction : Direction.values()) {
            addFace(quads, direction, base, 0.0f, true);
            if (direction == overlayFace || overlayFace == null) {
                addFace(quads, direction, overlay, OVERLAY_GROW, false);
            }
        }

        parts.add(new SimpleModelWrapper(quads.build(), true, base));
    }

    @Override
    public Material.Baked particleMaterial() {
        return particle;
    }

    @Override
    public int materialFlags() {
        return BakedQuad.FLAG_TRANSLUCENT;
    }

    private DynamicOverlayBakedModel.TextureSet textures(BlockAndTintGetter level, BlockPos pos, BlockState state) {
        Identifier machineId = machineId(state, level.getModelData(pos));
        if (kind == DynamicOverlayBakedModel.Kind.CONTROLLER) {
            return DynamicOverlayBakedModel.controllerTextures(machineId);
        }
        Identifier base = level.getModelData(pos).get(MachineModelDataKeys.PORT_BASE_TEXTURE);
        return DynamicOverlayBakedModel.portTextures(machineId, base, portOverlayTexture(state));
    }

    private Direction overlayFace(BlockState state) {
        if (kind == DynamicOverlayBakedModel.Kind.CONTROLLER && state.hasProperty(MachineControllerBlock.FACING)) {
            return state.getValue(MachineControllerBlock.FACING);
        }
        return null;
    }

    private static Identifier machineId(BlockState state, ModelData modelData) {
        Identifier modelDataId = modelData.get(MachineModelDataKeys.MACHINE_ID);
        if (modelDataId != null) {
            return modelDataId;
        }
        if (state.getBlock() instanceof MachineControllerBlock controller) {
            return controller.machineId();
        }
        return null;
    }

    private static Identifier portOverlayTexture(BlockState state) {
        if (state.getBlock() instanceof cn.howxu.mmcr.internal.block.IOPortBlock port) {
            return DynamicOverlayTextures.portOverlayTexture(port.kind());
        }
        return DynamicOverlayBakedModel.defaultPortOverlayTexture();
    }

    private Material.Baked material(Identifier texture) {
        return materials.get(new Material(texture), debugName);
    }

    static void addFace(QuadCollection.Builder quads, Direction direction, Material.Baked material,
                        float grow, boolean culled) {
        QuadBakingVertexConsumer builder = new QuadBakingVertexConsumer();
        builder.setSprite(material);
        builder.setDirection(direction);
        builder.setShade(true);

        Vec3i normal = direction.getUnitVec3i();
        for (Vector3f vertex : vertices(direction, grow)) {
            putVertex(builder, material, normal, vertex.x(), vertex.y(), vertex.z(), u(direction, vertex), v(direction, vertex));
        }

        if (culled) {
            quads.addCulledFace(direction, builder.bakeQuad());
        } else {
            quads.addUnculledFace(builder.bakeQuad());
        }
    }

    private static void putVertex(QuadBakingVertexConsumer builder, Material.Baked material, Vec3i normal,
                                  float x, float y, float z, float u, float v) {
        try (var sprite = material.sprite()) {
            builder.addVertex(x, y, z);
            builder.setColor(1.0f, 1.0f, 1.0f, 1.0f);
            builder.setNormal(normal.getX(), normal.getY(), normal.getZ());
            builder.setUv(sprite.getU(u), sprite.getV(v));
        }
    }

    private static List<Vector3f> vertices(Direction direction, float grow) {
        float min = -grow;
        float max = 1.0f + grow;
        return switch (direction) {
            case EAST -> List.of(new Vector3f(max, max, max), new Vector3f(max, min, max), new Vector3f(max, min, min), new Vector3f(max, max, min));
            case WEST -> List.of(new Vector3f(min, max, min), new Vector3f(min, min, min), new Vector3f(min, min, max), new Vector3f(min, max, max));
            case UP -> List.of(new Vector3f(min, max, max), new Vector3f(max, max, max), new Vector3f(max, max, min), new Vector3f(min, max, min));
            case DOWN -> List.of(new Vector3f(min, min, min), new Vector3f(max, min, min), new Vector3f(max, min, max), new Vector3f(min, min, max));
            case SOUTH -> List.of(new Vector3f(min, max, max), new Vector3f(min, min, max), new Vector3f(max, min, max), new Vector3f(max, max, max));
            case NORTH -> List.of(new Vector3f(max, max, min), new Vector3f(max, min, min), new Vector3f(min, min, min), new Vector3f(min, max, min));
        };
    }

    private static float u(Direction direction, Vector3f vertex) {
        return switch (direction) {
            case EAST -> 1.0f - vertex.z();
            case WEST -> vertex.z();
            case NORTH -> 1.0f - vertex.x();
            default -> vertex.x();
        };
    }

    private static float v(Direction direction, Vector3f vertex) {
        return direction.getAxis().isVertical() ? vertex.z() : 1.0f - vertex.y();
    }

    public record Unbaked(DynamicOverlayBakedModel.Kind kind) implements CustomUnbakedBlockStateModel {
        @Override
        public BlockStateModel bake(ModelBaker baker) {
            return new DynamicOverlayModelLoader(kind, baker.materials());
        }

        @Override
        public void resolveDependencies(Resolver resolver) {
        }

        @Override
        public MapCodec<Unbaked> codec() {
            return kind == DynamicOverlayBakedModel.Kind.CONTROLLER ? CONTROLLER_CODEC : PORT_CODEC;
        }
    }

}

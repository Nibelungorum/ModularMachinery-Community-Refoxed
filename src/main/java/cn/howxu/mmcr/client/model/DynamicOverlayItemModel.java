package cn.howxu.mmcr.client.model;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.internal.port.IOPortKind;
import com.mojang.serialization.MapCodec;
import net.minecraft.client.renderer.block.dispatch.BlockModelRotation;
import net.minecraft.client.renderer.item.CuboidItemModelWrapper;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.item.ItemModel;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.item.ModelRenderProperties;
import net.minecraft.client.resources.model.ResolvableModel;
import net.minecraft.client.resources.model.ModelBaker;
import net.minecraft.client.resources.model.ModelDebugName;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.client.resources.model.geometry.QuadCollection;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.client.resources.model.sprite.MaterialBaker;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.ItemOwner;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4fc;
import org.joml.Vector3fc;

import java.util.EnumSet;
import java.util.List;
import java.util.function.Supplier;

/**
 * Item model entry point shared by runtime machine controller and I/O port items.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class DynamicOverlayItemModel implements ItemModel {
    public static final Identifier ID = MMCR.id("dynamic_machine_item");
    public static final MapCodec<Unbaked> CODEC = MapCodec.unit(Unbaked::new);

    private final MaterialBaker materials;
    private final ModelBaker modelBaker;
    private final Matrix4fc transformation;
    private final ModelDebugName debugName = getClass()::toString;

    private DynamicOverlayItemModel(ModelBaker modelBaker, MaterialBaker materials, Matrix4fc transformation) {
        this.modelBaker = modelBaker;
        this.materials = materials;
        this.transformation = transformation;
    }

    @Override
    public void update(ItemStackRenderState renderState, ItemStack stack, ItemModelResolver itemModelResolver,
                       ItemDisplayContext displayContext, @Nullable ClientLevel level, @Nullable ItemOwner owner,
                       int seed) {
        Description description = describe(stack);
        if (description.kind() == null) {
            return;
        }
        renderState.appendModelIdentityElement(description);

        BaseModel baseModel = baseModel(description.baseModel(), description.baseTexture());
        baseModel.applyToLayer(renderState.newLayer(), displayContext, transformation);

        Material.Baked overlay = material(description.overlayTexture());
        QuadCollection.Builder quads = new QuadCollection.Builder();
        for (Direction direction : description.overlayFaces()) {
            DynamicOverlayModelLoader.addFace(quads, direction, overlay, DynamicOverlayModelLoader.OVERLAY_GROW, false);
        }

        var layer = renderState.newLayer();
        baseModel.renderProperties().applyToLayer(layer, displayContext);
        layer.setExtents(baseModel.extents());
        layer.setLocalTransform(transformation);
        layer.setParticleMaterial(overlay);
        layer.prepareQuadList().addAll(quads.build().getAll());
    }

    private BaseModel baseModel(Identifier modelId, Identifier baseTexture) {
        var model = modelBaker.getModel(modelId);
        var textures = model.getTopTextureSlots();
        QuadCollection.Builder quads = new QuadCollection.Builder();
        Material.Baked base = material(baseTexture);
        for (Direction direction : Direction.values()) {
            DynamicOverlayModelLoader.addFace(quads, direction, base, 0.0f, true);
        }
        var renderProperties = ModelRenderProperties.fromResolvedModel(modelBaker, model, textures);
        List<BakedQuad> builtQuads = quads.build().getAll();
        return new BaseModel(builtQuads, () -> CuboidItemModelWrapper.computeExtents(builtQuads), renderProperties);
    }

    private Material.Baked material(Identifier texture) {
        return materials.get(new Material(texture), debugName);
    }

    public static Description describe(ItemStack stack) {
        return describeItem(stack.getItem());
    }

    public static Description describeItem(Item item) {
        if (item instanceof BlockItem blockItem) {
            return describeBlock(blockItem.getBlock());
        }
        return Description.staticItem();
    }

    static Description describeBlock(Block block) {
        RuntimeBlockModelDefinition definition = RuntimeMachineModelRegistry.definition(block);
        if (definition == null) {
            return Description.staticItem();
        }
        Description description = definition.itemDescription();
        return description.kind() == DynamicOverlayBakedModel.Kind.CONTROLLER
                ? Description.controller(description.machineId()) : description;
    }

    public record Description(
            DynamicOverlayBakedModel.Kind kind,
            Identifier machineId,
            IOPortKind portKind,
            Identifier baseModel,
            Identifier baseTexture,
            Identifier overlayTexture,
            EnumSet<Direction> overlayFaces) {
        static Description controller(Identifier machineId) {
            DynamicOverlayBakedModel.TextureSet textures = DynamicOverlayBakedModel.controllerTextures(machineId);
            return new Description(DynamicOverlayBakedModel.Kind.CONTROLLER, machineId, null,
                    MMCR.id("block/dynamic_machine_controller"), textures.base(), textures.overlay(), EnumSet.of(Direction.NORTH));
        }

        static Description port(IOPortKind kind) {
            Identifier overlay = DynamicOverlayTextures.portOverlayTexture(kind);
            DynamicOverlayBakedModel.TextureSet textures = DynamicOverlayBakedModel.portTextures(MMCR.id("runtime_port_item"), null, overlay);
            return new Description(DynamicOverlayBakedModel.Kind.PORT, null, kind,
                    MMCR.id("block/dynamic_io_port"), textures.base(), textures.overlay(), EnumSet.allOf(Direction.class));
        }

        static Description portOverlay(Identifier overlay) {
            DynamicOverlayBakedModel.TextureSet textures = DynamicOverlayBakedModel.portTextures(MMCR.id("runtime_port_item"), null, overlay);
            return new Description(DynamicOverlayBakedModel.Kind.PORT, null, null,
                    MMCR.id("block/dynamic_io_port"), textures.base(), textures.overlay(), EnumSet.allOf(Direction.class));
        }

        static Description staticItem() {
            return new Description(null, null, null, null, null, null, EnumSet.noneOf(Direction.class));
        }
    }

    private record BaseModel(
            List<BakedQuad> quads,
            Supplier<Vector3fc[]> extents,
            ModelRenderProperties renderProperties) {
        void applyToLayer(ItemStackRenderState.LayerRenderState layer, ItemDisplayContext context, Matrix4fc transformation) {
            layer.setExtents(extents);
            layer.setLocalTransform(transformation);
            renderProperties.applyToLayer(layer, context);
            layer.prepareQuadList().addAll(quads);
        }
    }

    public record Unbaked() implements ItemModel.Unbaked {
        @Override
        public void resolveDependencies(ResolvableModel.Resolver resolver) {
            resolver.markDependency(MMCR.id("block/dynamic_machine_controller"));
            resolver.markDependency(MMCR.id("block/dynamic_io_port"));
        }

        @Override
        public ItemModel bake(ItemModel.BakingContext context, Matrix4fc transform) {
            return new DynamicOverlayItemModel(context.blockModelBaker(), context.blockModelBaker().materials(), transform);
        }

        @Override
        public MapCodec<Unbaked> type() {
            return CODEC;
        }
    }
}

package cn.howxu.mmcr.client.model;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.machine.MachineAppearanceSpec;
import cn.howxu.mmcr.internal.port.IOPortKind;
import com.google.common.collect.ImmutableList;
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

        BaseModel baseModel = baseModel(description.baseModel(), DynamicOverlayBakedModel.resolveBase(description.baseTextureSource()));
        baseModel.applyToLayer(renderState.newLayer(), displayContext, transformation);

        QuadCollection.Builder quads = new QuadCollection.Builder();
        for (DynamicOverlayModelLoader.OverlayLayer overlay : DynamicOverlayModelLoader.overlayLayers(
                description.overlayTextures(), description.stateOverlayTexture())) {
            for (Direction direction : description.overlayFaces()) {
                DynamicOverlayModelLoader.addFace(quads, direction, material(overlay.texture()), overlay.grow(), false);
            }
        }

        var layer = renderState.newLayer();
        baseModel.renderProperties().applyToLayer(layer, displayContext);
        layer.setExtents(baseModel.extents());
        layer.setLocalTransform(transformation);
        layer.setParticleMaterial(material(description.overlayTextures().getFirst()));
        QuadCollection overlayQuads = quads.build();
        layer.prepareQuadList().addAll(overlayQuads.getAll());
        if (baseModel.quads().hasMaterialFlag(BakedQuad.FLAG_ANIMATED)
                || overlayQuads.hasMaterialFlag(BakedQuad.FLAG_ANIMATED)) {
            renderState.setAnimated();
        }
    }

    private BaseModel baseModel(Identifier modelId, DynamicOverlayBakedModel.FaceTextures baseTextures) {
        var model = modelBaker.getModel(modelId);
        var textures = model.getTopTextureSlots();
        QuadCollection.Builder quads = new QuadCollection.Builder();
        for (Direction direction : Direction.values()) {
            DynamicOverlayModelLoader.addFace(quads, direction, material(baseTextures.forFace(direction)), 0.0f, true);
        }
        var renderProperties = ModelRenderProperties.fromResolvedModel(modelBaker, model, textures);
        QuadCollection builtQuads = quads.build();
        return new BaseModel(builtQuads, () -> CuboidItemModelWrapper.computeExtents(builtQuads.getAll()), renderProperties);
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
            MachineAppearanceSpec.TextureSource baseTextureSource,
            ImmutableList<Identifier> overlayTextures,
            @Nullable Identifier stateOverlayTexture,
            EnumSet<Direction> overlayFaces) {
        public Description {
            overlayTextures = overlayTextures == null ? ImmutableList.of() : ImmutableList.copyOf(overlayTextures);
        }

        static Description controller(Identifier machineId) {
            var appearance = MachineAppearanceCache.specFor(machineId);
            var controller = cn.howxu.mmcr.client.controller.ControllerSpecCache.specFor(machineId);
            return new Description(DynamicOverlayBakedModel.Kind.CONTROLLER, machineId, null,
                    MMCR.id("block/dynamic_machine_controller"), appearance.controllerTextureSource(),
                    ImmutableList.of(controller.frontTexture()),
                    appearance.controllerIdleOverlayTexture(), EnumSet.of(Direction.NORTH));
        }

        static Description port(IOPortKind kind) {
            ImmutableList<Identifier> overlays = DynamicOverlayTextures.portOverlayTexture(kind);
            return new Description(DynamicOverlayBakedModel.Kind.PORT, null, kind,
                    MMCR.id("block/dynamic_io_port"), MachineAppearanceSpec.defaults().formedPortTextureSource(), overlays,
                    null, EnumSet.allOf(Direction.class));
        }

        static Description portOverlay(Identifier overlay) {
            return new Description(DynamicOverlayBakedModel.Kind.PORT, null, null,
                    MMCR.id("block/dynamic_io_port"), MachineAppearanceSpec.defaults().formedPortTextureSource(),
                    ImmutableList.of(overlay),
                    null, EnumSet.allOf(Direction.class));
        }

        static Description staticItem() {
            return new Description(null, null, null, null, null, ImmutableList.of(), null,
                    EnumSet.noneOf(Direction.class));
        }
    }

    private record BaseModel(
            QuadCollection quads,
            Supplier<Vector3fc[]> extents,
            ModelRenderProperties renderProperties) {
        void applyToLayer(ItemStackRenderState.LayerRenderState layer, ItemDisplayContext context, Matrix4fc transformation) {
            layer.setExtents(extents);
            layer.setLocalTransform(transformation);
            renderProperties.applyToLayer(layer, context);
            layer.prepareQuadList().addAll(quads.getAll());
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

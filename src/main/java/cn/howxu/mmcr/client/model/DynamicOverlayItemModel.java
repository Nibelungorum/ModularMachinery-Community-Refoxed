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
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.client.resources.model.sprite.Material;
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

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Item model entry point shared by runtime machine controller and I/O port items.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class DynamicOverlayItemModel implements ItemModel {
    public static final Identifier ID = MMCR.id("dynamic_machine_item");
    public static final MapCodec<Unbaked> CODEC = MapCodec.unit(Unbaked::new);

    private final Map<Identifier, ModelRenderProperties> renderProperties;
    private final Function<Identifier, Material.Baked> runtimeMaterialGetter;
    private final Matrix4fc transformation;

    DynamicOverlayItemModel(Map<Identifier, ModelRenderProperties> renderProperties,
                            Function<Identifier, Material.Baked> runtimeMaterialGetter, Matrix4fc transformation) {
        this.renderProperties = Map.copyOf(renderProperties);
        this.runtimeMaterialGetter = runtimeMaterialGetter;
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

        var baseTextures = DynamicOverlayBakedModel.resolveBase(description.baseTextureSource());
        var overlays = new ArrayList<Material.Baked>(description.overlayTextures().size() + 1);
        for (var texture : description.overlayTextures()) {
            overlays.add(material(texture));
        }
        if (description.stateOverlayTexture() != null) {
            overlays.add(material(description.stateOverlayTexture()));
        }

        // The renderer assigns passes per item submit; keep each base face and its overlays together.
        var layer = renderState.newLayer();
        renderProperties.get(description.baseModel()).applyToLayer(layer, displayContext);
        layer.setLocalTransform(transformation);
        var baseQuads = new ArrayList<BakedQuad>(6);
        var quads = layer.prepareQuadList();
        for (Direction face : Direction.values()) {
            var base = DynamicOverlayModelLoader.baseFace(face, material(baseTextures.forFace(face)));
            baseQuads.add(base);
            quads.add(base);
            if (description.overlayFaces().contains(face)) {
                LayeredFacePart.addOverlays(quads, base, Direction.NORTH, overlays);
            }
        }
        layer.setExtents(() -> CuboidItemModelWrapper.computeExtents(baseQuads));
        if (quads.stream().anyMatch(q -> (q.materialInfo().flags() & BakedQuad.FLAG_ANIMATED) != 0)) {
            renderState.setAnimated();
        }
    }

    private Material.Baked material(Identifier texture) {
        return runtimeMaterialGetter.apply(texture);
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

    public record Unbaked() implements ItemModel.Unbaked {
        @Override
        public void resolveDependencies(ResolvableModel.Resolver resolver) {
            resolver.markDependency(MMCR.id("block/dynamic_machine_controller"));
            resolver.markDependency(MMCR.id("block/dynamic_io_port"));
        }

        @Override
        public ItemModel bake(ItemModel.BakingContext context, Matrix4fc transform) {
            var baker = context.blockModelBaker();
            Map<Identifier, ModelRenderProperties> renderProperties = new HashMap<>();
            for (Identifier modelId : List.of(MMCR.id("block/dynamic_machine_controller"), MMCR.id("block/dynamic_io_port"))) {
                var model = baker.getModel(modelId);
                renderProperties.put(modelId, ModelRenderProperties.fromResolvedModel(baker, model, model.getTopTextureSlots()));
            }
            return new DynamicOverlayItemModel(renderProperties, DynamicOverlayModelLoader::runtimeMaterial, transform);
        }

        @Override
        public MapCodec<Unbaked> type() {
            return CODEC;
        }
    }
}

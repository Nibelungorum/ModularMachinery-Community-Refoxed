package cn.howxu.mmcr.client.model;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.internal.block.IOPortBlock;
import cn.howxu.mmcr.internal.block.MachineControllerBlock;
import cn.howxu.mmcr.internal.port.IOPortKind;
import com.mojang.serialization.MapCodec;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.item.ItemModel;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.resources.model.ResolvableModel;
import net.minecraft.client.resources.model.ModelDebugName;
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

/**
 * Item model entry point shared by runtime machine controller and I/O port items.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class DynamicOverlayItemModel implements ItemModel {
    public static final Identifier ID = MMCR.id("dynamic_machine_item");
    public static final MapCodec<Unbaked> CODEC = MapCodec.unit(Unbaked::new);

    private final MaterialBaker materials;
    private final ModelDebugName debugName = getClass()::toString;

    private DynamicOverlayItemModel(MaterialBaker materials) {
        this.materials = materials;
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
        Material.Baked base = material(description.baseTexture());
        Material.Baked overlay = material(description.overlayTexture());
        QuadCollection.Builder quads = new QuadCollection.Builder();
        for (Direction direction : Direction.values()) {
            DynamicOverlayModelLoader.addFace(quads, direction, base, 0.0f, false);
            DynamicOverlayModelLoader.addFace(quads, direction, overlay, DynamicOverlayModelLoader.OVERLAY_GROW, false);
        }

        var layer = renderState.newLayer();
        layer.setUsesBlockLight(true);
        layer.setParticleMaterial(base);
        layer.prepareQuadList().addAll(quads.build().getAll());
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
        if (block instanceof MachineControllerBlock controller) {
            return Description.controller(controller.machineId());
        }
        if (block instanceof IOPortBlock port) {
            return Description.port(port.kind());
        }
        return Description.staticItem();
    }

    public record Description(
            DynamicOverlayBakedModel.Kind kind,
            Identifier machineId,
            IOPortKind portKind,
            Identifier baseTexture,
            Identifier overlayTexture) {
        static Description controller(Identifier machineId) {
            DynamicOverlayBakedModel.TextureSet textures = DynamicOverlayBakedModel.controllerTextures(machineId);
            return new Description(DynamicOverlayBakedModel.Kind.CONTROLLER, machineId, null, textures.base(), textures.overlay());
        }

        static Description port(IOPortKind kind) {
            Identifier overlay = DynamicOverlayTextures.portOverlayTexture(kind);
            DynamicOverlayBakedModel.TextureSet textures = DynamicOverlayBakedModel.portTextures(MMCR.id("runtime_port_item"), null, overlay);
            return new Description(DynamicOverlayBakedModel.Kind.PORT, null, kind, textures.base(), textures.overlay());
        }

        static Description staticItem() {
            return new Description(null, null, null, null, null);
        }
    }

    public record Unbaked() implements ItemModel.Unbaked {
        @Override
        public void resolveDependencies(ResolvableModel.Resolver resolver) {
        }

        @Override
        public ItemModel bake(ItemModel.BakingContext context, Matrix4fc transform) {
            return new DynamicOverlayItemModel(context.blockModelBaker().materials());
        }

        @Override
        public MapCodec<Unbaked> type() {
            return CODEC;
        }
    }
}

package cn.howxu.mmcr.client.model;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.machine.MachineAppearanceSpec;
import cn.howxu.mmcr.internal.port.IOPortKind;
import com.google.common.collect.ImmutableList;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;

/**
 * Resolves the fixed item appearance consumed by the 1.21.1 geometry loader.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class DynamicOverlayItemModel {
    private DynamicOverlayItemModel() {
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
            ResourceLocation machineId,
            IOPortKind portKind,
            ResourceLocation baseModel,
            MachineAppearanceSpec.TextureSource baseTextureSource,
            ImmutableList<ResourceLocation> overlayTextures,
            @Nullable ResourceLocation stateOverlayTexture,
            EnumSet<Direction> overlayFaces) {
        public Description {
            overlayTextures = overlayTextures == null ? ImmutableList.of() : ImmutableList.copyOf(overlayTextures);
        }

        static Description controller(ResourceLocation machineId) {
            var appearance = MachineAppearanceCache.specFor(machineId);
            var controller = cn.howxu.mmcr.client.controller.ControllerSpecCache.specFor(machineId);
            return new Description(DynamicOverlayBakedModel.Kind.CONTROLLER, machineId, null,
                    MMCR.id("block/dynamic_machine_controller"), appearance.controllerTextureSource(),
                    ImmutableList.of(controller.frontTexture()),
                    appearance.controllerIdleOverlayTexture(), EnumSet.of(Direction.NORTH));
        }

        static Description port(IOPortKind kind) {
            ImmutableList<ResourceLocation> overlays = DynamicOverlayTextures.portOverlayTexture(kind);
            return new Description(DynamicOverlayBakedModel.Kind.PORT, null, kind,
                    MMCR.id("block/dynamic_io_port"), MachineAppearanceSpec.defaults().formedPortTextureSource(), overlays,
                    null, EnumSet.allOf(Direction.class));
        }

        static Description portOverlay(ResourceLocation overlay) {
            return new Description(DynamicOverlayBakedModel.Kind.PORT, null, null,
                    MMCR.id("block/dynamic_io_port"), MachineAppearanceSpec.defaults().formedPortTextureSource(),
                    ImmutableList.of(overlay), null, EnumSet.allOf(Direction.class));
        }

        static Description staticItem() {
            return new Description(null, null, null, null, null, ImmutableList.of(), null,
                    EnumSet.noneOf(Direction.class));
        }
    }
}

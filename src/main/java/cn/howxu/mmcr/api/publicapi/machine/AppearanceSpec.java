package cn.howxu.mmcr.api.publicapi.machine;

import net.minecraft.resources.ResourceLocation;

import java.util.Objects;

/**
 * Machine appearance declaration.
 *
 * @author howxu <dev@howxu.cn>
 */
public record AppearanceSpec(
        ResourceLocation machineBasicBlock,
        ResourceLocation controllerBaseTexture,
        ResourceLocation formedPortBaseTexture,
        ResourceLocation controllerIdleOverlayTexture,
        ResourceLocation controllerActiveOverlayTexture) {

    public AppearanceSpec(ResourceLocation machineBasicBlock, ResourceLocation controllerBaseTexture, ResourceLocation formedPortBaseTexture) {
        this(machineBasicBlock, controllerBaseTexture, formedPortBaseTexture, null, null);
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for machine appearance declarations.
     *
     * @author howxu <dev@howxu.cn>
     */
    public static final class Builder {
        private ResourceLocation machineBasicBlock;
        private ResourceLocation controllerBaseTexture;
        private ResourceLocation formedPortBaseTexture;
        private ResourceLocation controllerIdleOverlayTexture;
        private ResourceLocation controllerActiveOverlayTexture;

        public Builder appearance(ResourceLocation machineBasicBlock) {
            return machineBasicBlock(machineBasicBlock);
        }

        public Builder appearance(String machineBasicBlock) {
            return appearance(ResourceLocation.parse(machineBasicBlock));
        }

        public Builder machineBasicBlock(ResourceLocation machineBasicBlock) {
            this.machineBasicBlock = Objects.requireNonNull(machineBasicBlock, "machineBasicBlock");
            return this;
        }

        public Builder machineBasicBlock(String machineBasicBlock) {
            return machineBasicBlock(ResourceLocation.parse(machineBasicBlock));
        }

        public Builder controllerBaseTexture(ResourceLocation controllerBaseTexture) {
            this.controllerBaseTexture = Objects.requireNonNull(controllerBaseTexture, "controllerBaseTexture");
            return this;
        }

        public Builder formedPortBaseTexture(ResourceLocation formedPortBaseTexture) {
            this.formedPortBaseTexture = Objects.requireNonNull(formedPortBaseTexture, "formedPortBaseTexture");
            return this;
        }

        public Builder controllerIdleOverlayTexture(ResourceLocation controllerIdleOverlayTexture) {
            this.controllerIdleOverlayTexture = Objects.requireNonNull(controllerIdleOverlayTexture, "controllerIdleOverlayTexture");
            return this;
        }

        public Builder controllerActiveOverlayTexture(ResourceLocation controllerActiveOverlayTexture) {
            this.controllerActiveOverlayTexture = Objects.requireNonNull(controllerActiveOverlayTexture, "controllerActiveOverlayTexture");
            return this;
        }

        public AppearanceSpec build() {
            return new AppearanceSpec(machineBasicBlock, controllerBaseTexture, formedPortBaseTexture,
                    controllerIdleOverlayTexture, controllerActiveOverlayTexture);
        }
    }
}

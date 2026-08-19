package cn.howxu.mmcr.api.publicapi.machine;

import net.minecraft.resources.Identifier;

import java.util.Objects;

/**
 * Machine appearance declaration.
 *
 * @author howxu <dev@howxu.cn>
 */
public record AppearanceSpec(
        Identifier machineBasicBlock,
        Identifier controllerBaseTexture,
        Identifier formedPortBaseTexture) {

    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for machine appearance declarations.
     *
     * @author howxu <dev@howxu.cn>
     */
    public static final class Builder {
        private Identifier machineBasicBlock;
        private Identifier controllerBaseTexture;
        private Identifier formedPortBaseTexture;

        public Builder machineBasicBlock(Identifier machineBasicBlock) {
            this.machineBasicBlock = Objects.requireNonNull(machineBasicBlock, "machineBasicBlock");
            return this;
        }

        public Builder controllerBaseTexture(Identifier controllerBaseTexture) {
            this.controllerBaseTexture = Objects.requireNonNull(controllerBaseTexture, "controllerBaseTexture");
            return this;
        }

        public Builder formedPortBaseTexture(Identifier formedPortBaseTexture) {
            this.formedPortBaseTexture = Objects.requireNonNull(formedPortBaseTexture, "formedPortBaseTexture");
            return this;
        }

        public AppearanceSpec build() {
            return new AppearanceSpec(machineBasicBlock, controllerBaseTexture, formedPortBaseTexture);
        }
    }
}

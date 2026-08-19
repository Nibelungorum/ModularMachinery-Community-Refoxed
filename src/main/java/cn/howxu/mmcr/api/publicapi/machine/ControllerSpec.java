package cn.howxu.mmcr.api.publicapi.machine;

import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Controller texture and facing declaration.
 *
 * @author howxu <dev@howxu.cn>
 */
public record ControllerSpec(
        Identifier id,
        Identifier frontTexture,
        Identifier sideTexture,
        Identifier topTexture,
        Identifier bottomTexture,
        boolean allowVerticalFacing,
        boolean fullyRotationallySymmetric,
        boolean requireVerticalFacing,
        List<String> tooltip) {

    public ControllerSpec {
        tooltip = List.copyOf(tooltip == null ? List.of() : tooltip);
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for controller texture and facing declarations.
     *
     * @author howxu <dev@howxu.cn>
     */
    public static final class Builder {
        private Identifier id;
        private Identifier frontTexture;
        private Identifier sideTexture;
        private Identifier topTexture;
        private Identifier bottomTexture;
        private boolean allowVerticalFacing;
        private boolean fullyRotationallySymmetric;
        private boolean requireVerticalFacing;
        private final List<String> tooltip = new ArrayList<>();

        public Builder id(Identifier id) {
            this.id = Objects.requireNonNull(id, "id");
            return this;
        }

        public Builder textures(Identifier frontTexture, Identifier otherFaces) {
            return textures(frontTexture, otherFaces, otherFaces, otherFaces);
        }

        public Builder textures(Identifier frontTexture, Identifier sideTexture, Identifier topTexture, Identifier bottomTexture) {
            this.frontTexture = Objects.requireNonNull(frontTexture, "frontTexture");
            this.sideTexture = Objects.requireNonNull(sideTexture, "sideTexture");
            this.topTexture = Objects.requireNonNull(topTexture, "topTexture");
            this.bottomTexture = Objects.requireNonNull(bottomTexture, "bottomTexture");
            return this;
        }

        public Builder frontTexture(Identifier frontTexture) {
            this.frontTexture = Objects.requireNonNull(frontTexture, "frontTexture");
            return this;
        }

        public Builder sideTexture(Identifier sideTexture) {
            this.sideTexture = Objects.requireNonNull(sideTexture, "sideTexture");
            return this;
        }

        public Builder topTexture(Identifier topTexture) {
            this.topTexture = Objects.requireNonNull(topTexture, "topTexture");
            return this;
        }

        public Builder bottomTexture(Identifier bottomTexture) {
            this.bottomTexture = Objects.requireNonNull(bottomTexture, "bottomTexture");
            return this;
        }

        public Builder allowVerticalFacing() {
            return allowVerticalFacing(true);
        }

        public Builder allowVerticalFacing(boolean allowVerticalFacing) {
            this.allowVerticalFacing = allowVerticalFacing;
            return this;
        }

        public Builder fullyRotationallySymmetric() {
            return fullyRotationallySymmetric(true);
        }

        public Builder fullyRotationallySymmetric(boolean fullyRotationallySymmetric) {
            this.fullyRotationallySymmetric = fullyRotationallySymmetric;
            return this;
        }

        public Builder requireVerticalFacing() {
            return requireVerticalFacing(true);
        }

        public Builder requireVerticalFacing(boolean requireVerticalFacing) {
            this.requireVerticalFacing = requireVerticalFacing;
            if (requireVerticalFacing) this.allowVerticalFacing = true;
            return this;
        }

        public Builder tooltip(String... lines) {
            Objects.requireNonNull(lines, "lines");
            for (String line : lines) {
                if (line != null && !line.isBlank()) tooltip.add(line);
            }
            return this;
        }

        public ControllerSpec build() {
            return new ControllerSpec(id, frontTexture, sideTexture, topTexture, bottomTexture,
                    allowVerticalFacing, fullyRotationallySymmetric, requireVerticalFacing, tooltip);
        }
    }
}

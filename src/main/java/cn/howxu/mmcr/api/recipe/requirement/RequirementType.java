package cn.howxu.mmcr.api.recipe.requirement;

import com.mojang.serialization.MapCodec;
import net.minecraft.resources.Identifier;

import java.util.Objects;

/**
 * Identifies a machine requirement and owns its serialization, planning and presentation contracts.
 *
 * @author howxu <dev@howxu.cn>
 */
public interface RequirementType<R extends MachineRequirement> {
    Identifier id();

    MapCodec<R> codec();

    RequirementHandler<R> handler();

    default Presentation presentation() {
        return Presentation.defaults(id());
    }

    /**
     * Immutable metadata used by recipe presentation consumers.
     *
     * @param translationKey the localized name key
     * @param descriptionKey the localized description key
     * @author howxu <dev@howxu.cn>
     */
    record Presentation(String translationKey, String descriptionKey) {
        public Presentation {
            if (translationKey == null || translationKey.isBlank()) {
                throw new IllegalArgumentException("translationKey must not be blank");
            }
            if (descriptionKey == null || descriptionKey.isBlank()) {
                throw new IllegalArgumentException("descriptionKey must not be blank");
            }
        }

        public static Presentation defaults(Identifier id) {
            String key = "requirement." + id;
            return new Presentation(key, key + ".description");
        }
    }

    /**
     * Standard immutable implementation for custom and built-in requirement declarations.
     *
     * @param id the stable requirement identifier
     * @param codec the complete map codec for the requirement
     * @param handler the handler for the requirement
     * @param presentation the presentation metadata
     * @param <R> the requirement type
     * @author howxu <dev@howxu.cn>
     */
    final class Definition<R extends MachineRequirement> implements RequirementType<R> {
        private final Identifier id;
        private final MapCodec<R> codec;
        private final RequirementHandler<R> handler;
        private final Presentation presentation;

        public Definition(Identifier id, MapCodec<R> codec, RequirementHandler<R> handler) {
            this(id, codec, handler, Presentation.defaults(id));
        }

        public Definition(Identifier id, MapCodec<R> codec, RequirementHandler<R> handler,
                          Presentation presentation) {
            this.id = Objects.requireNonNull(id, "id");
            this.codec = Objects.requireNonNull(codec, "codec");
            this.handler = Objects.requireNonNull(handler, "handler");
            this.presentation = Objects.requireNonNull(presentation, "presentation");
        }

        @Override
        public Identifier id() {
            return id;
        }

        @Override
        public MapCodec<R> codec() {
            return codec;
        }

        @Override
        public RequirementHandler<R> handler() {
            return handler;
        }

        @Override
        public Presentation presentation() {
            return presentation;
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof RequirementType<?> type && id.equals(type.id());
        }

        @Override
        public int hashCode() {
            return id.hashCode();
        }

        @Override
        public String toString() {
            return id.toString();
        }
    }
}

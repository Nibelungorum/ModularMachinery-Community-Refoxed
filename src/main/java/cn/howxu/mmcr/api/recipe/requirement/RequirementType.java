package cn.howxu.mmcr.api.recipe.requirement;

import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.MapCodec;
import net.minecraft.resources.Identifier;

import cn.howxu.mmcr.api.recipe.RecipeSyncCodec;
import cn.howxu.mmcr.api.recipe.modifier.RecipeModifier;
import java.util.List;
import java.util.Objects;
import java.util.function.UnaryOperator;

/**
 * Identifies a machine requirement and owns its serialization, planning and presentation contracts.
 *
 * @author howxu <dev@howxu.cn>
 */
public interface RequirementType<R extends MachineRequirement> {
    Identifier id();

    MapCodec<R> codec();

    RequirementHandler<R> handler();

    default RecipeSyncCodec<R> syncCodec() {
        return RecipeSyncCodec.json(codec().codec());
    }

    default R applyModifiers(R requirement, List<RecipeModifier> modifiers) {
        return handler().applyModifiers(requirement, modifiers);
    }

    default R applyLevelModifiers(R requirement, double energyMultiplier, double outputMultiplier) {
        return handler().applyLevelModifiers(requirement, energyMultiplier, outputMultiplier);
    }

    default boolean overlaps(R requirement, MachineRequirement other) {
        return handler().overlaps(requirement, other);
    }

    /**
     * Copies a requirement using this canonical type's codec by default.
     */
    default R copy(R requirement) {
        Codec<R> codec = codec().codec();
        R copy = codec.parse(JsonOps.INSTANCE, codec.encodeStart(JsonOps.INSTANCE, requirement).getOrThrow()).getOrThrow();
        if (copy == null || copy.type() != this) {
            throw new IllegalArgumentException("Copied requirement does not match registered type: " + id());
        }
        return copy;
    }

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
        private final UnaryOperator<R> copier;
        private final RecipeSyncCodec<R> syncCodec;

        public Definition(Identifier id, MapCodec<R> codec, RequirementHandler<R> handler) {
            this(id, codec, handler, Presentation.defaults(id), null);
        }

        public Definition(Identifier id, MapCodec<R> codec, RequirementHandler<R> handler,
                           UnaryOperator<R> copier) {
            this(id, codec, handler, Presentation.defaults(id), copier);
        }

        public Definition(Identifier id, MapCodec<R> codec, RequirementHandler<R> handler,
                          UnaryOperator<R> copier, RecipeSyncCodec<R> syncCodec) {
            this(id, codec, handler, Presentation.defaults(id), copier, syncCodec);
        }

        public Definition(Identifier id, MapCodec<R> codec, RequirementHandler<R> handler,
                          Presentation presentation) {
            this(id, codec, handler, presentation, null);
        }

        public Definition(Identifier id, MapCodec<R> codec, RequirementHandler<R> handler,
                           Presentation presentation, UnaryOperator<R> copier) {
            this(id, codec, handler, presentation, copier, null);
        }

        public Definition(Identifier id, MapCodec<R> codec, RequirementHandler<R> handler,
                          RecipeSyncCodec<R> syncCodec) {
            this(id, codec, handler, Presentation.defaults(id), null, syncCodec);
        }

        private Definition(Identifier id, MapCodec<R> codec, RequirementHandler<R> handler,
                           Presentation presentation, UnaryOperator<R> copier, RecipeSyncCodec<R> syncCodec) {
            this.id = Objects.requireNonNull(id, "id");
            this.codec = Objects.requireNonNull(codec, "codec");
            this.handler = Objects.requireNonNull(handler, "handler");
            this.presentation = Objects.requireNonNull(presentation, "presentation");
            this.copier = copier;
            this.syncCodec = syncCodec;
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
        public RecipeSyncCodec<R> syncCodec() {
            return syncCodec == null ? RequirementType.super.syncCodec() : syncCodec;
        }

        @Override
        public Presentation presentation() {
            return presentation;
        }

        @Override
        public R copy(R requirement) {
            return copier == null ? RequirementType.super.copy(requirement) : copier.apply(requirement);
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

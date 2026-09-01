package cn.howxu.mmcr.api.recipe;

import cn.howxu.mmcr.api.recipe.modifier.RecipeModifier;
import cn.howxu.mmcr.api.recipe.requirement.MachineRequirement;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.MapCodec;
import net.minecraft.resources.Identifier;

import java.util.List;
import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;

/**
 * Identifies a machine output and owns its serialization and transformations.
 *
 * @author howxu <dev@howxu.cn>
 */
public interface OutputType<O extends MachineOutput> {
    Identifier id();

    MapCodec<O> codec();

    O withChance(O output, float chance);

    O applyModifiers(O output, List<RecipeModifier> modifiers);

    MachineRequirement toRequirement(O output, List<String> tags);

    boolean matchesRequirement(MachineRequirement requirement);

    default RecipeSyncCodec<O> syncCodec() {
        return RecipeSyncCodec.json(codec().codec());
    }

    default MachineOutput fromRequirement(MachineRequirement requirement) {
        return null;
    }

    default O copy(O output) {
        Codec<O> codec = codec().codec();
        O copy = codec.parse(JsonOps.INSTANCE, codec.encodeStart(JsonOps.INSTANCE, output).getOrThrow()).getOrThrow();
        if (copy == null || copy.outputType() != this) {
            throw new IllegalArgumentException("Copied output does not match registered type: " + id());
        }
        return copy;
    }

    default String serializedId() {
        return id().toString();
    }

    default Presentation presentation() {
        return Presentation.defaults(id());
    }

    /** Immutable metadata used by recipe presentation consumers. */
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
            String key = "output." + id;
            return new Presentation(key, key + ".description");
        }
    }

    /** Standard immutable implementation for custom and built-in output declarations. */
    final class Definition<O extends MachineOutput> implements OutputType<O> {
        private final Identifier id;
        private final MapCodec<O> codec;
        private final BiFunction<O, Float, O> chanceTransformer;
        private final BiFunction<O, List<RecipeModifier>, O> modifierTransformer;
        private final UnaryOperator<O> copier;
        private final BiFunction<O, List<String>, MachineRequirement> requirementFactory;
        private final Predicate<MachineRequirement> requirementMatcher;
        private final Function<MachineRequirement, MachineOutput> outputFactory;
        private final Presentation presentation;
        private final String serializedId;
        private final RecipeSyncCodec<O> syncCodec;

        public Definition(Identifier id, MapCodec<O> codec, BiFunction<O, Float, O> chanceTransformer,
                          BiFunction<O, List<RecipeModifier>, O> modifierTransformer, UnaryOperator<O> copier) {
            this(id, codec, chanceTransformer, modifierTransformer, copier, Presentation.defaults(id), id.toString(),
                    (output, tags) -> {
                        throw new IllegalStateException("Output type does not support execution requirements: " + id);
                    }, requirement -> false);
        }

        public Definition(Identifier id, MapCodec<O> codec, BiFunction<O, Float, O> chanceTransformer,
                          BiFunction<O, List<RecipeModifier>, O> modifierTransformer, UnaryOperator<O> copier,
                          Presentation presentation) {
            this(id, codec, chanceTransformer, modifierTransformer, copier, presentation, id.toString(),
                    (output, tags) -> {
                        throw new IllegalStateException("Output type does not support execution requirements: " + id);
                    }, requirement -> false);
        }

        public Definition(Identifier id, MapCodec<O> codec, BiFunction<O, Float, O> chanceTransformer,
                          BiFunction<O, List<RecipeModifier>, O> modifierTransformer, UnaryOperator<O> copier,
                          Presentation presentation, String serializedId) {
            this(id, codec, chanceTransformer, modifierTransformer, copier, presentation, serializedId,
                    (output, tags) -> {
                        throw new IllegalStateException("Output type does not support execution requirements: " + id);
                    }, requirement -> false);
        }

        public Definition(Identifier id, MapCodec<O> codec, BiFunction<O, Float, O> chanceTransformer,
                          BiFunction<O, List<RecipeModifier>, O> modifierTransformer, UnaryOperator<O> copier,
                          Presentation presentation, String serializedId,
                          BiFunction<O, List<String>, MachineRequirement> requirementFactory,
                          Predicate<MachineRequirement> requirementMatcher) {
            this(id, codec, chanceTransformer, modifierTransformer, copier, presentation, serializedId,
                    requirementFactory, requirementMatcher, ignored -> null);
        }

        public Definition(Identifier id, MapCodec<O> codec, BiFunction<O, Float, O> chanceTransformer,
                          BiFunction<O, List<RecipeModifier>, O> modifierTransformer, UnaryOperator<O> copier,
                          RecipeSyncCodec<O> syncCodec) {
            this(id, codec, chanceTransformer, modifierTransformer, copier, Presentation.defaults(id), id.toString(),
                    (output, tags) -> {
                        throw new IllegalStateException("Output type does not support execution requirements: " + id);
                    }, requirement -> false, ignored -> null, syncCodec);
        }

        public Definition(Identifier id, MapCodec<O> codec, BiFunction<O, Float, O> chanceTransformer,
                          BiFunction<O, List<RecipeModifier>, O> modifierTransformer, UnaryOperator<O> copier,
                          Presentation presentation, String serializedId,
                          BiFunction<O, List<String>, MachineRequirement> requirementFactory,
                          Predicate<MachineRequirement> requirementMatcher,
                          Function<MachineRequirement, MachineOutput> outputFactory) {
            this(id, codec, chanceTransformer, modifierTransformer, copier, presentation, serializedId,
                    requirementFactory, requirementMatcher, outputFactory, null);
        }

        public Definition(Identifier id, MapCodec<O> codec, BiFunction<O, Float, O> chanceTransformer,
                          BiFunction<O, List<RecipeModifier>, O> modifierTransformer, UnaryOperator<O> copier,
                          Presentation presentation, String serializedId,
                          BiFunction<O, List<String>, MachineRequirement> requirementFactory,
                          Predicate<MachineRequirement> requirementMatcher,
                          Function<MachineRequirement, MachineOutput> outputFactory, RecipeSyncCodec<O> syncCodec) {
            this.id = Objects.requireNonNull(id, "id");
            this.codec = Objects.requireNonNull(codec, "codec");
            this.chanceTransformer = Objects.requireNonNull(chanceTransformer, "chanceTransformer");
            this.modifierTransformer = Objects.requireNonNull(modifierTransformer, "modifierTransformer");
            this.copier = Objects.requireNonNull(copier, "copier");
            this.requirementFactory = Objects.requireNonNull(requirementFactory, "requirementFactory");
            this.requirementMatcher = Objects.requireNonNull(requirementMatcher, "requirementMatcher");
            this.outputFactory = Objects.requireNonNull(outputFactory, "outputFactory");
            this.presentation = Objects.requireNonNull(presentation, "presentation");
            if (serializedId == null || serializedId.isBlank()) throw new IllegalArgumentException("serializedId must not be blank");
            this.serializedId = serializedId;
            this.syncCodec = syncCodec;
        }

        @Override
        public Identifier id() {
            return id;
        }

        @Override
        public MapCodec<O> codec() {
            return codec;
        }

        @Override
        public O withChance(O output, float chance) {
            return chanceTransformer.apply(output, chance);
        }

        @Override
        public O applyModifiers(O output, List<RecipeModifier> modifiers) {
            return modifierTransformer.apply(output, modifiers);
        }

        @Override
        public O copy(O output) {
            return copier.apply(output);
        }

        @Override
        public RecipeSyncCodec<O> syncCodec() {
            return syncCodec == null ? OutputType.super.syncCodec() : syncCodec;
        }

        @Override
        public MachineRequirement toRequirement(O output, List<String> tags) {
            return requirementFactory.apply(output, tags);
        }

        @Override
        public boolean matchesRequirement(MachineRequirement requirement) {
            return requirementMatcher.test(requirement);
        }

        @Override
        public MachineOutput fromRequirement(MachineRequirement requirement) {
            return outputFactory.apply(requirement);
        }

        @Override
        public Presentation presentation() {
            return presentation;
        }

        @Override
        public String serializedId() {
            return serializedId;
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof OutputType<?> type && id.equals(type.id());
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

package cn.howxu.mmcr.internal.api.facade.recipe;

import cn.howxu.mmcr.api.recipe.CustomOutput;
import cn.howxu.mmcr.api.recipe.MachineOutput;
import cn.howxu.mmcr.api.recipe.OutputRegistry;
import cn.howxu.mmcr.api.recipe.OutputType;
import cn.howxu.mmcr.api.recipe.RecipeSyncCodec;
import cn.howxu.mmcr.api.recipe.modifier.RecipeModifier;
import cn.howxu.mmcr.api.recipe.requirement.MachineRequirement;
import cn.howxu.mmcr.publicapi.recipe.OutputExtension;
import cn.howxu.mmcr.publicapi.recipe.OutputKind;
import cn.howxu.mmcr.publicapi.recipe.extension.SyncPayloadCodec;
import cn.howxu.mmcr.publicapi.recipe.extension.TypePresentation;
import cn.howxu.mmcr.publicapi.runtime.OutputView;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;

/** Typed output extension adaptation with canonical codec and executable requirement conversion.
 * @author howxu <dev@howxu.cn> */
public final class OutputExtensionAdapter {
    private OutputExtensionAdapter() {}
    public static <O> OutputKind<O> register(OutputExtension<O> extension) {
        Handle<O> handle = new Handle<>(extension);
        OutputRegistry.register(handle.type);
        return handle;
    }
    public static <O> OutputView output(OutputKind<O> kind, O payload) {
        if (!(kind instanceof Handle<O> handle)) throw new IllegalArgumentException("Kind must be library-produced");
        handle.canonical();
        return OutputAdapters.wrap(MachineOutput.copyOf(new Carrier<>(handle, payload)));
    }
    private static final class Handle<O> implements OutputKind<O> {
        private final OutputExtension<O> extension;
        private final ResourceLocation id;
        private final String serializedId;
        private final TypePresentation presentation;
        private final OutputType<Carrier<O>> type;
        Handle(OutputExtension<O> extension) {
            this.extension = Objects.requireNonNull(extension, "extension");
            id = Objects.requireNonNull(extension.id(), "id");
            serializedId = Objects.requireNonNull(extension.serializedId(), "serializedId");
            presentation = Objects.requireNonNull(extension.presentation(), "presentation");
            OutputType.Presentation corePresentation = new OutputType.Presentation(presentation.translationKey(), presentation.descriptionKey());
            Codec<String> discriminator = Codec.STRING.validate(value -> value.equals(serializedId)
                    ? DataResult.success(value) : DataResult.error(() -> "Wrong output type: " + value));
            MapCodec<Carrier<O>> codec = RecordCodecBuilder.mapCodec(instance -> instance.group(
                    discriminator.fieldOf("type").forGetter(v -> serializedId),
                    extension.codec().forGetter(v -> v.payload)
            ).apply(instance, (ignored, payload) -> new Carrier<>(this, payload)));
            SyncPayloadCodec<O> sync = extension.syncCodec();
            RecipeSyncCodec<Carrier<O>> syncCodec = sync == null ? RecipeSyncCodec.json(codec.codec())
                    : ExtensionSyncAdapters.adapt(sync, v -> v.payload, p -> new Carrier<>(this, p));
            type = new OutputType<>() {
                public ResourceLocation id() { return id; }
                public String serializedId() { return serializedId; }
                public MapCodec<Carrier<O>> codec() { return codec; }
                public RecipeSyncCodec<Carrier<O>> syncCodec() { return syncCodec; }
                public Presentation presentation() { return corePresentation; }
                public Carrier<O> copy(Carrier<O> value) { owned(value); return new Carrier<>(Handle.this, extension.copy(value.payload)); }
                public Carrier<O> withChance(Carrier<O> value, float chance) { owned(value); return new Carrier<>(Handle.this, extension.withChance(value.payload, chance)); }
                public Carrier<O> applyModifiers(Carrier<O> value, List<RecipeModifier> modifiers) {
                    owned(value);
                    return new Carrier<>(Handle.this, extension.applyModifiers(value.payload, modifiers.stream().map(ModifierAdapters::wrap).toList()));
                }
                public MachineRequirement toRequirement(Carrier<O> value, List<String> tags) {
                    owned(value);
                    return RequirementAdapters.unwrap(extension.toRequirement(value.payload, tags));
                }
                public boolean matchesRequirement(MachineRequirement requirement) { return extension.matchesRequirement(RequirementAdapters.wrap(requirement)); }
                public MachineOutput fromRequirement(MachineRequirement requirement) {
                    return extension.fromRequirement(RequirementAdapters.wrap(requirement)).map(p -> (MachineOutput) new Carrier<>(Handle.this, p)).orElse(null);
                }
            };
        }
        private void owned(Carrier<O> value) { if (value.owner != this) throw new IllegalArgumentException("Foreign output carrier"); }
        private void canonical() { if (OutputRegistry.typeFor(id) != type) throw new IllegalArgumentException("Output kind is no longer registered canonically"); }
        public ResourceLocation id() { return id; }
        public String serializedId() { return serializedId; }
        public TypePresentation presentation() { return presentation; }
        @SuppressWarnings("unchecked")
        public Optional<O> payload(OutputView output) {
            canonical();
            MachineOutput value = OutputAdapters.unwrap(output);
            if (!(value instanceof Carrier<?> carrier) || carrier.owner != this) return Optional.empty();
            return Optional.of(extension.copy(((Carrier<O>) carrier).payload));
        }
    }
    private record Carrier<O>(Handle<O> owner, O payload) implements CustomOutput {
        Carrier { Objects.requireNonNull(payload, "payload"); }
        public OutputType<Carrier<O>> outputType() { return owner.type; }
        public float chance() { return owner.extension.chance(payload); }
        public long amount() { return owner.extension.amount(payload); }
    }
}

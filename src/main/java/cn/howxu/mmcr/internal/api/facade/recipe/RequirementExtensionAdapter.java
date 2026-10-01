package cn.howxu.mmcr.internal.api.facade.recipe;

import cn.howxu.mmcr.api.capability.MachineCapability;
import cn.howxu.mmcr.api.capability.plan.PlanningContext;
import cn.howxu.mmcr.api.capability.plan.RequirementPlan;
import cn.howxu.mmcr.api.recipe.RecipeSyncCodec;
import cn.howxu.mmcr.api.recipe.modifier.RecipeModifier;
import cn.howxu.mmcr.api.recipe.requirement.CustomRequirement;
import cn.howxu.mmcr.api.recipe.requirement.MachineRequirement;
import cn.howxu.mmcr.api.recipe.requirement.RequirementHandler;
import cn.howxu.mmcr.api.recipe.requirement.RequirementHandlerRegistry;
import cn.howxu.mmcr.api.recipe.requirement.RequirementType;
import cn.howxu.mmcr.publicapi.recipe.extension.SyncPayloadCodec;
import cn.howxu.mmcr.publicapi.recipe.extension.TypePresentation;
import cn.howxu.mmcr.publicapi.recipe.requirement.*;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;

/** A registered handle creates exactly one canonical core type and typed payload carrier.
 * @author howxu <dev@howxu.cn> */
public final class RequirementExtensionAdapter {
    private RequirementExtensionAdapter() {}
    public static <R> RequirementKind<R> register(RequirementExtension<R> extension) {
        Handle<R> handle = new Handle<>(extension);
        RequirementHandlerRegistry.register(handle.type);
        return handle;
    }
    public static <R> RequirementSpec requirement(RequirementKind<R> kind, R payload) {
        if (!(kind instanceof Handle<R> handle)) throw new IllegalArgumentException("Kind must be library-produced");
        handle.canonical();
        return RequirementAdapters.wrap(MachineRequirement.copyOf(new Carrier<>(handle, payload)));
    }
    private static final class Handle<R> implements RequirementKind<R> {
        private final RequirementExtension<R> extension;
        private final ResourceLocation id;
        private final TypePresentation presentation;
        private final RequirementType<Carrier<R>> type;
        Handle(RequirementExtension<R> extension) {
            this.extension = Objects.requireNonNull(extension, "extension");
            id = Objects.requireNonNull(extension.id(), "id");
            Set<ResourceLocation> capabilityIds = Set.copyOf(extension.capabilityIds());
            presentation = Objects.requireNonNull(extension.presentation(), "presentation");
            RequirementType.Presentation corePresentation = new RequirementType.Presentation(presentation.translationKey(), presentation.descriptionKey());
            RequirementExecution<R> execution = Objects.requireNonNull(extension.execution(), "execution");
            Codec<String> discriminator = Codec.STRING.validate(value -> value.equals(id.toString())
                    ? DataResult.success(value) : DataResult.error(() -> "Wrong requirement type: " + value));
            MapCodec<Carrier<R>> codec = RecordCodecBuilder.mapCodec(instance -> instance.group(
                    discriminator.fieldOf("type").forGetter(v -> id.toString()),
                    extension.codec().forGetter(v -> v.payload)
            ).apply(instance, (ignored, payload) -> new Carrier<>(this, payload)));
            RequirementHandler<Carrier<R>> handler = new RequirementHandler<>() {
                public RequirementPlan plan(Carrier<R> value, List<MachineCapability> capabilities, PlanningContext context) {
                    owned(value);
                    return PlanningAdapters.unwrap(execution.plan(value.payload, PlanningAdapters.wrap(capabilities, context)));
                }
                public Carrier<R> applyModifiers(Carrier<R> value, List<RecipeModifier> modifiers) {
                    owned(value);
                    return new Carrier<>(Handle.this, execution.applyModifiers(value.payload, modifiers.stream().map(ModifierAdapters::wrap).toList()));
                }
                public Carrier<R> applyLevelModifiers(Carrier<R> value, double energy, double output) {
                    owned(value);
                    return new Carrier<>(Handle.this, execution.applyLevelModifiers(value.payload, energy, output));
                }
                public boolean overlaps(Carrier<R> value, MachineRequirement other) { owned(value); return execution.overlaps(value.payload, RequirementAdapters.wrap(other)); }
                public List<ResourceWakeup> resourceWakeups(Carrier<R> value) { owned(value); return execution.resourceWakeups(value.payload).stream().map(PlanningAdapters::wakeup).toList(); }
            };
            SyncPayloadCodec<R> sync = extension.syncCodec();
            RecipeSyncCodec<Carrier<R>> syncCodec = sync == null ? RecipeSyncCodec.json(codec.codec())
                    : ExtensionSyncAdapters.adapt(sync, v -> v.payload, p -> new Carrier<>(this, p));
            type = new RequirementType<>() {
                public ResourceLocation id() { return id; }
                public Set<ResourceLocation> capabilityIds() { return capabilityIds; }
                public MapCodec<Carrier<R>> codec() { return codec; }
                public RequirementHandler<Carrier<R>> handler() { return handler; }
                public RecipeSyncCodec<Carrier<R>> syncCodec() { return syncCodec; }
                public Presentation presentation() { return corePresentation; }
                public Carrier<R> copy(Carrier<R> value) { owned(value); return new Carrier<>(Handle.this, extension.copy(value.payload)); }
            };
        }
        private void owned(Carrier<R> value) { if (value.owner != this) throw new IllegalArgumentException("Foreign requirement carrier"); }
        private void canonical() { if (RequirementHandlerRegistry.typeFor(id) != type) throw new IllegalArgumentException("Requirement kind is no longer registered canonically"); }
        public ResourceLocation id() { return id; }
        public TypePresentation presentation() { return presentation; }
        @SuppressWarnings("unchecked")
        public Optional<R> payload(RequirementSpec requirement) {
            canonical();
            MachineRequirement value = RequirementAdapters.unwrap(requirement);
            if (!(value instanceof Carrier<?> carrier) || carrier.owner != this) return Optional.empty();
            return Optional.of(extension.copy(((Carrier<R>) carrier).payload));
        }
    }
    private record Carrier<R>(Handle<R> owner, R payload) implements CustomRequirement {
        Carrier { Objects.requireNonNull(payload, "payload"); }
        public RequirementType<Carrier<R>> type() { return owner.type; }
        public RecipeModifier.IOType io() { return Objects.requireNonNull(ModifierAdapters.io(owner.extension.io(payload)), "io"); }
        public List<String> tags() { return List.copyOf(owner.extension.tags(payload)); }
    }
}

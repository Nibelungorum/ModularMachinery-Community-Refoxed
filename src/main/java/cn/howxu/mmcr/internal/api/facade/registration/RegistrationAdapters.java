package cn.howxu.mmcr.internal.api.facade.registration;

import cn.howxu.mmcr.api.registration.StructureRegistration;
import cn.howxu.mmcr.internal.api.facade.machine.MachineAdapters;
import cn.howxu.mmcr.internal.api.facade.structure.StructureAdapters;
import cn.howxu.mmcr.internal.api.facade.recipe.RecipeAdapters;
import cn.howxu.mmcr.internal.api.facade.recipe.ModifierAdapters;
import cn.howxu.mmcr.api.registration.MachineDefinitionRegistration;
import cn.howxu.mmcr.api.registration.MachineRecipeRegistration;
import cn.howxu.mmcr.publicapi.event.RegisterMachineDefinitionsEvent;
import cn.howxu.mmcr.publicapi.event.RegisterMachineRecipesEvent;
import cn.howxu.mmcr.publicapi.event.RegisterMachineStructuresEvent;
import cn.howxu.mmcr.publicapi.machine.MachineDraft;
import cn.howxu.mmcr.publicapi.machine.MachineSpec;
import cn.howxu.mmcr.publicapi.recipe.RecipeDraft;
import cn.howxu.mmcr.publicapi.recipe.RecipeSpec;
import cn.howxu.mmcr.publicapi.recipe.modifier.ModifierBundle;
import cn.howxu.mmcr.publicapi.registration.MachineRegistrar;
import cn.howxu.mmcr.publicapi.registration.RecipeRegistrar;
import cn.howxu.mmcr.publicapi.registration.RegistrationException;
import cn.howxu.mmcr.publicapi.registration.StructureRegistrar;
import cn.howxu.mmcr.publicapi.structure.StructureDraft;
import cn.howxu.mmcr.publicapi.structure.StructureSpec;
import cn.howxu.mmcr.publicapi.structure.level.LevelTypeSpec;
import cn.howxu.mmcr.publicapi.structure.level.MachineLevelSpec;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/** Typed lifecycle access stays internal; public registrars delegate to authoritative collectors.
 * @author howxu <dev@howxu.cn>
 */
public final class RegistrationAdapters {
    private RegistrationAdapters() { }

    public static MachineRegistrar definitions() { return new Definitions(new MachineDefinitionRegistration()); }
    public static RecipeRegistrar recipes() { return new Recipes(new MachineRecipeRegistration()); }
    public static StructureRegistrar structures(Collection<ResourceLocation> ids) {
        return new Structures(new StructureRegistration(ids));
    }
    public static RegisterMachineStructuresEvent prepare(Collection<ResourceLocation> ids) {
        return new RegisterMachineStructuresEvent(new Structures(StructureRegistration.prepare(ids)));
    }
    public static RegisterMachineStructuresEvent current() {
        return new RegisterMachineStructuresEvent(new Structures(StructureRegistration.current()));
    }
    public static void resetCollector() { StructureRegistration.resetCollector(); }
    public static MachineDefinitionRegistration core(RegisterMachineDefinitionsEvent event) {
        return ((Definitions) event.registrar()).core;
    }
    public static MachineRecipeRegistration core(RegisterMachineRecipesEvent event) {
        return ((Recipes) event.registrar()).core;
    }
    public static StructureRegistration core(RegisterMachineStructuresEvent event) {
        return ((Structures) event.registrar()).core;
    }
    public static void freeze(RegisterMachineDefinitionsEvent event) { core(event).freeze(); }
    public static void freeze(RegisterMachineRecipesEvent event) {
        core(event).freeze();
    }
    public static StructureRegistration.Snapshot freeze(RegisterMachineStructuresEvent event) {
        try { return core(event).freeze(); }
        catch (IllegalStateException exception) { throw new RegistrationException(exception.getMessage(), exception); }
    }

    private static void registration(Runnable operation) {
        try { operation.run(); }
        catch (RegistrationException exception) { throw exception; }
        catch (IllegalStateException exception) {
            throw new RegistrationException(exception.getMessage(), exception);
        }
    }

    private static <T, V> Map<ResourceLocation, V> views(Map<ResourceLocation, T> source, Function<T, V> wrap) {
        Map<ResourceLocation, V> result = new LinkedHashMap<>();
        source.forEach((id, value) -> result.put(id, wrap.apply(value)));
        return Collections.unmodifiableMap(result);
    }

    private record Definitions(MachineDefinitionRegistration core) implements MachineRegistrar {
        @Override public void registerMachine(ResourceLocation id, Consumer<MachineDraft> configuration) {
            registration(() -> core.registerMachine(id, configuration == null ? null : builder -> {
                configuration.accept(MachineAdapters.wrap(builder));
                return builder;
            }));
        }
        @Override public void registerMachine(MachineSpec definition) {
            registration(() -> core.registerMachine(definition == null ? null : MachineAdapters.unwrap(definition)));
        }
        @Override public Map<ResourceLocation, MachineSpec> definitions() { return views(core.definitions(), MachineAdapters::wrap); }
    }

    private static final class Recipes implements RecipeRegistrar {
        private final MachineRecipeRegistration core;
        private Recipes(MachineRecipeRegistration core) { this.core = core; }
        @Override public void registerRecipe(RecipeSpec recipe) {
            Objects.requireNonNull(recipe, "recipe");
            registration(() -> core.registerRecipe(RecipeAdapters.unwrap(recipe)));
        }
        @Override public void registerRecipe(ResourceLocation id, Consumer<RecipeDraft> configuration) {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(configuration, "configuration");
            registration(() -> core.registerRecipe(id,
                    builder -> configuration.accept(RecipeAdapters.wrap(builder))));
        }
        @Override public Map<ResourceLocation, RecipeSpec> recipes() { return views(core.recipes(), RecipeAdapters::wrap); }
    }

    private record Structures(StructureRegistration core) implements StructureRegistrar {
        @Override public void registerStructure(ResourceLocation id, Consumer<StructureDraft> configuration) {
            Objects.requireNonNull(id, "machineId");
            Objects.requireNonNull(configuration, "configuration");
            registration(() -> core.registerStructure(id, builder -> {
                configuration.accept(StructureAdapters.wrap(builder));
                return builder;
            }));
        }
        @Override public void registerStructure(StructureSpec structure) {
            Objects.requireNonNull(structure, "structure");
            registration(() -> core.registerStructure(StructureAdapters.unwrap(structure)));
        }
        @Override public void registerLevelType(LevelTypeSpec type) {
            Objects.requireNonNull(type, "type");
            registration(() -> core.registerLevelType(StructureAdapters.unwrap(type)));
        }
        @Override public void registerLevel(MachineLevelSpec level) {
            Objects.requireNonNull(level, "level");
            registration(() -> core.registerLevel(StructureAdapters.unwrapRuntime(level)));
        }
        @Override public void registerModifier(ResourceLocation id, ModifierBundle modifier) {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(modifier, "modifier");
            registration(() -> core.registerModifier(id, ModifierAdapters.unwrap(modifier)));
        }
        @Override public void registerModifierItem(ItemStack stack, ResourceLocation id) {
            Objects.requireNonNull(stack, "stack");
            Objects.requireNonNull(id, "modifierId");
            registration(() -> core.registerModifierItem(stack, id));
        }
        @Override public Map<ResourceLocation, StructureSpec> structures() { return views(core.structures(), StructureAdapters::wrap); }
        @Override public Map<ResourceLocation, LevelTypeSpec> levelTypes() { return views(core.levelTypes(), StructureAdapters::wrap); }
        @Override public Map<ResourceLocation, MachineLevelSpec> levels() { return views(core.levels(), StructureAdapters::wrap); }
        @Override public Map<ResourceLocation, ModifierBundle> modifiers() { return views(core.modifiers(), ModifierAdapters::wrap); }
        @Override public Map<ResourceLocation, List<ItemStack>> modifierItems() { return core.modifierItems(); }
    }
}

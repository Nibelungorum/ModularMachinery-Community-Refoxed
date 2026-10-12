package cn.howxu.mmcr.compat.emi;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.client.gui.BlueprintScreen;
import cn.howxu.mmcr.internal.client.RecipeViewerRegistration;
import cn.howxu.mmcr.internal.client.RecipeViewerSnapshot;
import cn.howxu.mmcr.internal.client.RecipeViewerSnapshot.Version;
import cn.howxu.mmcr.internal.sync.RecipeViewerBackend;
import cn.howxu.mmcr.registry.ModBlocks;
import cn.howxu.mmcr.registry.ModUIs;
import dev.emi.emi.api.EmiEntrypoint;
import dev.emi.emi.api.EmiInitRegistry;
import dev.emi.emi.api.EmiPlugin;
import dev.emi.emi.api.EmiRegistry;
import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.recipe.EmiRecipeCategory;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.api.stack.EmiStackInteraction;
import dev.emi.emi.api.widget.Bounds;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.LongFunction;

/** EMI-native entrypoint; one capture, one detached registration cycle.
 * @author howxu <dev@howxu.cn>
 */
@EmiEntrypoint
public final class MachineEmiPlugin implements EmiPlugin {
    @Override public void initialize(EmiInitRegistry registry) {
        registry.addIngredientSerializer(SourceEmiStack.class, new SourceEmiStackSerializer());
    }

    @Override public void register(EmiRegistry registry) {
        if (RecipeViewerBackend.current() != RecipeViewerBackend.EMI) return;
        Version start = RecipeViewerRegistration.currentVersion();
        EmiRuntimeReloader.registrationStarted(start);
        registerCycle(start, RecipeViewerRegistration::captureAsync, snapshot -> register(registry, snapshot),
                EmiRuntimeReloader::registered, EmiRuntimeReloader::registrationFailed);
    }

    static void registerCycle(Version start, LongFunction<CompletableFuture<RecipeViewerSnapshot>> capture,
                              Consumer<RecipeViewerSnapshot> registration, Consumer<Version> completed,
                              BiConsumer<Version, Throwable> failed) {
        Version version = start;
        try {
            RecipeViewerSnapshot snapshot = capture.apply(start.session()).join();
            if (snapshot.version().session() != start.session()) {
                throw new CancellationException("Native EMI registration session ended");
            }
            version = snapshot.version();
            registration.accept(snapshot);
            completed.accept(version);
        } catch (RuntimeException | Error failure) {
            failed.accept(version, failure);
            throw failure;
        }
    }

    private static void register(EmiRegistry registry, RecipeViewerSnapshot snapshot) {
        Map<ResourceLocation, EmiRecipeCategory> pools = new LinkedHashMap<>();
        snapshot.machinesByPool().entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> {
            var first = entry.getValue().stream().sorted().findFirst();
            EmiStack icon = first.filter(ModBlocks::hasControllerFor).map(id ->
                    EmiStack.of(new ItemStack(ModBlocks.controllerFor(id).get()))).orElse(EmiStack.EMPTY);
            EmiRecipeCategory category = poolCategory(entry.getKey(), icon);
            pools.put(entry.getKey(), category);
            registry.addCategory(category);
        });
        EmiRecipeCategory structure = structureCategory(EmiStack.of(new ItemStack(ModBlocks.BASIC_CASING.get())));
        registry.addCategory(structure);
        snapshot.structures().stream().sorted(Comparator.comparing(display -> display.machine().registryName()))
                .forEach(display -> registry.addRecipe(new MachineStructureEmiRecipe(structure, display, snapshot.version())));
        snapshot.recipesByPool().forEach((pool, recipes) -> {
            EmiRecipeCategory category = pools.get(pool);
            if (category == null) {
                recipes.forEach(recipe -> MMCR.LOG.warn("Skipping EMI recipe {} for unknown recipe pool {}", recipe.recipeId(), pool));
            } else recipes.forEach(data -> registry.addRecipe(new MachineEmiRecipe(category, data,
                    snapshot.informationFor(pool, data.recipeId()))));
        });
        EmiWorkstations.register(registry, snapshot, pools);
        registry.addScreenBoundsProvider(BlueprintScreen.class, screen -> {
            var bounds = screen.guiBounds();
            return new Bounds(bounds.getX(), bounds.getY(), bounds.getWidth(), bounds.getHeight());
        });
        registry.addStackProvider(BlueprintScreen.class, (screen, mouseX, mouseY) -> {
            var hovered = screen.hoveredIngredient(mouseX, mouseY);
            return hovered == null ? EmiStackInteraction.EMPTY : new EmiStackInteraction(EmiStack.of(hovered.stack().copy()));
        });
        registry.addRecipeHandler(ModUIs.ITEM_BUS.get(), new MachineEmiRecipeHandler());
    }

    static EmiRecipeCategory poolCategory(ResourceLocation pool, EmiStack icon) {
        Comparator<EmiRecipe> sorter = Comparator.comparingInt((EmiRecipe recipe) -> recipe instanceof MachineEmiRecipe machine
                ? machine.data().recipe().priority() : 0).reversed().thenComparing(recipe -> recipe instanceof MachineEmiRecipe machine
                ? machine.data().recipeId() : recipe.getId(), Comparator.nullsLast(Comparator.naturalOrder()));
        return new EmiRecipeCategory(ResourceLocation.fromNamespaceAndPath(pool.getNamespace(), "recipe_pool/" + pool.getPath()),
                icon, icon, sorter) {
            @Override public Component getName() { return Component.translatable(pool.toLanguageKey("recipe_pool")); }
        };
    }

    static EmiRecipeCategory structureCategory(EmiStack icon) {
        return new EmiRecipeCategory(MMCR.id("multiblock_structure"), icon, icon,
                Comparator.comparing(EmiRecipe::getId)) {
            @Override public Component getName() { return Component.translatable("jei.mmcr.multiblock_structure"); }
        };
    }
}

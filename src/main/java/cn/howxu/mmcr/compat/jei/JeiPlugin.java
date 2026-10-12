package cn.howxu.mmcr.compat.jei;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.compat.ars_nouveau.client.SourceJeiIngredient;
import cn.howxu.mmcr.compat.ars_nouveau.client.SourceJeiIngredientHelper;
import cn.howxu.mmcr.compat.ars_nouveau.client.SourceJeiIngredientRenderer;
import cn.howxu.mmcr.api.machine.MachineRegistry;
import cn.howxu.mmcr.api.jei.JeiWorkstationRegistration;
import cn.howxu.mmcr.client.gui.BlueprintScreen;
import cn.howxu.mmcr.internal.client.RecipeViewerRegistration;
import cn.howxu.mmcr.internal.client.RecipeViewerSnapshot;
import cn.howxu.mmcr.internal.sync.RecipeViewerBackend;
import cn.howxu.mmcr.registry.ModBlocks;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IModIngredientRegistration;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import mezz.jei.api.registration.IRecipeTransferRegistration;
import mezz.jei.api.registration.IGuiHandlerRegistration;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * JEI plugin entrypoint for MMCR.
 *
 * @author howxu <dev@howxu.cn>
 */
@mezz.jei.api.JeiPlugin
public final class JeiPlugin implements IModPlugin {
    private final Supplier<RecipeViewerSnapshot> capture;
    private RecipeViewerSnapshot registrationSnapshot;

    public JeiPlugin() {
        this(() -> RecipeViewerRegistration.captureAsync().join());
    }

    JeiPlugin(Supplier<RecipeViewerSnapshot> capture) {
        this.capture = capture;
    }

    @Override
    public ResourceLocation getPluginUid() {
        return MMCR.id("jei");
    }

    @Override
    public void registerIngredients(IModIngredientRegistration registration) {
        if (RecipeViewerBackend.current() == RecipeViewerBackend.EMI) return;
        registration.register(SourceJeiIngredient.TYPE, List.of(), new SourceJeiIngredientHelper(),
                new SourceJeiIngredientRenderer(), SourceJeiIngredient.CODEC);
    }

    @Override
    public void registerCategories(IRecipeCategoryRegistration registration) {
        if (RecipeViewerBackend.current() == RecipeViewerBackend.EMI) return;
        registrationSnapshot = capture.get();
        JeiIngredientAdapterRegistry.registerBuiltIns();
        var jeiHelpers = registration.getJeiHelpers();
        var guiHelper = jeiHelpers.getGuiHelper();
        registration.addRecipeCategories(new MachineStructureCategory(guiHelper, jeiHelpers.getIngredientManager()));
        Map<ResourceLocation, List<ResourceLocation>> machinesByPool = registrationSnapshot.machinesByPool();
        JeiRuntimeReloader.markRegisteredRecipePoolCategories(machinesByPool.keySet());
        machinesByPool.forEach((poolId, machineIds) -> registration.addRecipeCategories(
                new MachineRecipeCategory(guiHelper, poolId, machineIds.getFirst())));
    }

    @Override
    public void onRuntimeAvailable(IJeiRuntime runtime) {
        if (RecipeViewerBackend.current() == RecipeViewerBackend.EMI) return;
        JeiRuntimeReloader.setRuntime(runtime);
    }

    @Override
    public void onRuntimeUnavailable() {
        JeiRuntimeReloader.setRuntime(null);
    }

    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        if (RecipeViewerBackend.current() == RecipeViewerBackend.EMI) return;
        List<MachineStructureDisplay> structures = MachineRegistry.getAll().values().stream()
                .map(MachineStructureDisplay::from)
                .toList();
        JeiRuntimeReloader.captureInitialStructures(structures);
        registration.addRecipes(JeiMachineRecipeTypes.STRUCTURE, structures);
        var displaysByPool = MachineRecipeDisplays.byPool();
        Set<ResourceLocation> poolIds = machineIdsByPool().keySet();
        Map<ResourceLocation, List<MachineRecipeDisplay>> registeredDisplays = displaysByPool.entrySet().stream()
                .filter(entry -> poolIds.contains(entry.getKey()))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue,
                        (first, ignored) -> first, LinkedHashMap::new));
        JeiRuntimeReloader.captureInitialDisplays(registeredDisplays);
        displaysByPool.forEach((poolId, displays) -> {
            if (!poolIds.contains(poolId)) {
                displays.forEach(display -> MMCR.LOG.warn("Skipping JEI recipe {} for unknown recipe pool {}", display.recipeId(), poolId));
            }
        });
        poolIds.forEach(poolId -> registration.addRecipes(
                JeiMachineRecipeTypes.forPool(poolId),
                displaysByPool.getOrDefault(poolId, List.of())));
    }

    @Override
    public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
        if (RecipeViewerBackend.current() == RecipeViewerBackend.EMI) return;
        if (registrationSnapshot == null) registrationSnapshot = capture.get();
        Map<ResourceLocation, List<ResourceLocation>> machinesByPool = registrationSnapshot.machinesByPool();
        Map<RecipeType<?>, List<ItemStack>> workstations = new LinkedHashMap<>();

        registrationSnapshot.workstations().forEach(entry -> {
            switch (entry) {
                case JeiWorkstationRegistration.RecipePoolItem poolItem -> {
                    if (!machinesByPool.containsKey(poolItem.recipePoolId())) {
                        MMCR.LOG.warn("Skipping JEI workstation {} for unknown recipe pool {}",
                                poolItem.itemId(), poolItem.recipePoolId());
                    } else if (!BuiltInRegistries.ITEM.containsKey(poolItem.itemId())) {
                        MMCR.LOG.warn("Skipping unknown JEI workstation item {}", poolItem.itemId());
                    } else {
                        ItemStack workstation = new ItemStack(BuiltInRegistries.ITEM.get(poolItem.itemId()));
                        if (workstation.isEmpty()) {
                            MMCR.LOG.warn("Skipping empty JEI workstation item {} for recipe pool {}",
                                    poolItem.itemId(), poolItem.recipePoolId());
                        } else {
                            addWorkstation(workstations, JeiMachineRecipeTypes.forPool(poolItem.recipePoolId()),
                                    workstation);
                        }
                    }
                }
                case JeiWorkstationRegistration.RecipePoolStack poolStack -> {
                    if (!machinesByPool.containsKey(poolStack.recipePoolId())) {
                        MMCR.LOG.warn("Skipping JEI workstation for unknown recipe pool {}", poolStack.recipePoolId());
                    } else {
                        addWorkstation(workstations, JeiMachineRecipeTypes.forPool(poolStack.recipePoolId()),
                                poolStack.workstation());
                    }
                }
                case JeiWorkstationRegistration.Machine machine -> {
                    if (!ModBlocks.hasControllerFor(machine.machineId())) {
                        MMCR.LOG.warn("Skipping JEI workstation for unknown machine {}", machine.machineId());
                        return;
                    }
                    registration.getJeiHelpers().getRecipeType(machine.recipeTypeId()).ifPresentOrElse(
                            recipeType -> addWorkstation(workstations, recipeType, controllerFor(machine.machineId())),
                            () -> MMCR.LOG.warn("Skipping machine {} workstation for unknown JEI recipe type {}",
                                    machine.machineId(), machine.recipeTypeId()));
                }
            }
        });

        workstations.forEach((recipeType, stacks) ->
                registration.addRecipeCatalysts(recipeType, stacks.toArray(ItemStack[]::new)));
    }

    @Override
    public void registerRecipeTransferHandlers(IRecipeTransferRegistration registration) {
        if (RecipeViewerBackend.current() == RecipeViewerBackend.EMI) return;
        var helper = registration.getTransferHelper();
        var ingredientManager = registration.getJeiHelpers().getIngredientManager();
        machineIdsByPool().keySet().forEach(poolId -> {
            var type = JeiMachineRecipeTypes.forPool(poolId);
            registration.addRecipeTransferHandler(new MachineRecipeTransferHandler(helper, type, ingredientManager), type);
        });
    }

    @Override
    public void registerGuiHandlers(IGuiHandlerRegistration registration) {
        if (RecipeViewerBackend.current() == RecipeViewerBackend.EMI) return;
        registration.addGuiScreenHandler(BlueprintScreen.class, new BlueprintScreenJeiHandler());
    }

    static Set<ResourceLocation> machineIds() {
        return RecipeViewerRegistration.machineIds();
    }

    static Map<ResourceLocation, List<ResourceLocation>> machineIdsByPool() {
        return RecipeViewerRegistration.machineIdsByPool();
    }

    private static ItemStack controllerFor(ResourceLocation machineId) {
        return new ItemStack(ModBlocks.controllerFor(machineId).get());
    }

    private static void addWorkstation(Map<RecipeType<?>, List<ItemStack>> workstations,
                                       RecipeType<?> recipeType, ItemStack workstation) {
        List<ItemStack> stacks = workstations.computeIfAbsent(recipeType, ignored -> new ArrayList<>());
        if (stacks.stream().noneMatch(existing -> ItemStack.isSameItemSameComponents(existing, workstation))) {
            stacks.add(workstation.copyWithCount(1));
        }
    }

}

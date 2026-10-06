package cn.howxu.mmcr.compat.jei;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.compat.ars_nouveau.client.SourceJeiIngredient;
import cn.howxu.mmcr.compat.ars_nouveau.client.SourceJeiIngredientHelper;
import cn.howxu.mmcr.compat.ars_nouveau.client.SourceJeiIngredientRenderer;
import cn.howxu.mmcr.api.machine.MachineRegistry;
import cn.howxu.mmcr.api.machine.MachineDefinitions;
import cn.howxu.mmcr.publicapi.event.RegisterJeiRecipeInformationEvent;
import cn.howxu.mmcr.publicapi.event.RegisterJeiWorkstationsEvent;
import cn.howxu.mmcr.internal.api.facade.client.ClientRegistrationAdapters;
import cn.howxu.mmcr.api.jei.JeiWorkstationRegistration;
import cn.howxu.mmcr.client.gui.BlueprintScreen;
import cn.howxu.mmcr.internal.client.JeiWorkstationRegistry;
import cn.howxu.mmcr.internal.client.RecipeInformationRegistry;
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
import net.neoforged.neoforge.common.NeoForge;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * JEI plugin entrypoint for MMCR.
 *
 * @author howxu <dev@howxu.cn>
 */
@mezz.jei.api.JeiPlugin
public final class JeiPlugin implements IModPlugin {

    @Override
    public ResourceLocation getPluginUid() {
        return MMCR.id("jei");
    }

    @Override
    public void registerIngredients(IModIngredientRegistration registration) {
        registration.register(SourceJeiIngredient.TYPE, List.of(), new SourceJeiIngredientHelper(),
                new SourceJeiIngredientRenderer(), SourceJeiIngredient.CODEC);
    }

    @Override
    public void registerCategories(IRecipeCategoryRegistration registration) {
        RegisterJeiRecipeInformationEvent event = new RegisterJeiRecipeInformationEvent();
        NeoForge.EVENT_BUS.post(event);
        ClientRegistrationAdapters.freeze(event);
        RecipeInformationRegistry.replacePublic(ClientRegistrationAdapters.coreEntries(event));
        JeiIngredientAdapterRegistry.registerBuiltIns();
        var jeiHelpers = registration.getJeiHelpers();
        var guiHelper = jeiHelpers.getGuiHelper();
        registration.addRecipeCategories(new MachineStructureCategory(guiHelper, jeiHelpers.getIngredientManager()));
        Map<ResourceLocation, List<ResourceLocation>> machinesByPool = machineIdsByPool();
        JeiRuntimeReloader.markRegisteredRecipePoolCategories(machinesByPool.keySet());
        machinesByPool.forEach((poolId, machineIds) -> registration.addRecipeCategories(
                new MachineRecipeCategory(guiHelper, poolId, machineIds.getFirst())));
    }

    @Override
    public void onRuntimeAvailable(IJeiRuntime runtime) {
        JeiRuntimeReloader.setRuntime(runtime);
    }

    @Override
    public void onRuntimeUnavailable() {
        JeiRuntimeReloader.setRuntime(null);
    }

    @Override
    public void registerRecipes(IRecipeRegistration registration) {
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
        Map<ResourceLocation, List<ResourceLocation>> machinesByPool = machineIdsByPool();
        Map<RecipeType<?>, List<ItemStack>> workstations = new LinkedHashMap<>();
        machinesByPool.forEach((poolId, machineIds) -> machineIds.forEach(machineId ->
                addWorkstation(workstations, JeiMachineRecipeTypes.forPool(poolId), controllerFor(machineId))));

        RegisterJeiWorkstationsEvent event = new RegisterJeiWorkstationsEvent();
        NeoForge.EVENT_BUS.post(event);
        ClientRegistrationAdapters.freeze(event);
        List<JeiWorkstationRegistration> manualEntries = new ArrayList<>(ClientRegistrationAdapters.coreEntries(event));
        manualEntries.addAll(JeiWorkstationRegistry.kubeJSEntries());

        manualEntries.forEach(entry -> {
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
        var helper = registration.getTransferHelper();
        var ingredientManager = registration.getJeiHelpers().getIngredientManager();
        machineIdsByPool().keySet().forEach(poolId -> {
            var type = JeiMachineRecipeTypes.forPool(poolId);
            registration.addRecipeTransferHandler(new MachineRecipeTransferHandler(helper, type, ingredientManager), type);
        });
    }

    @Override
    public void registerGuiHandlers(IGuiHandlerRegistration registration) {
        registration.addGuiScreenHandler(BlueprintScreen.class, new BlueprintScreenJeiHandler());
    }

    static Set<ResourceLocation> machineIds() {
        Set<ResourceLocation> ids = new LinkedHashSet<>(MachineRegistry.getAll().keySet());
        ids.addAll(MachineDefinitions.effectiveSnapshot().keySet());
        return ids;
    }

    static Map<ResourceLocation, List<ResourceLocation>> machineIdsByPool() {
        Map<ResourceLocation, List<ResourceLocation>> machinesByPool = new LinkedHashMap<>();
        machineIds().stream().sorted().forEach(machineId ->
                MachineRegistry.recipePoolsForMachine(machineId).forEach(poolId ->
                        machinesByPool.computeIfAbsent(poolId, ignored -> new java.util.ArrayList<>()).add(machineId)));
        machinesByPool.replaceAll((ignored, machineIds) -> List.copyOf(machineIds));
        return machinesByPool;
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

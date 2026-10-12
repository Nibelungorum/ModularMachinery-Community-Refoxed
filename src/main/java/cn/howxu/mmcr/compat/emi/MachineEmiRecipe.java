package cn.howxu.mmcr.compat.emi;

import cn.howxu.mmcr.api.presentation.ComponentSnapshots;
import cn.howxu.mmcr.api.recipe.modifier.RecipeModifier.IOType;
import cn.howxu.mmcr.client.recipe.MachineRecipeData;
import cn.howxu.mmcr.client.recipe.RecipeDisplayDrawing;
import cn.howxu.mmcr.client.recipe.RecipeDisplayEntry;
import cn.howxu.mmcr.client.recipe.RecipeMetadataRow;
import cn.howxu.mmcr.compat.botania.BotaniaManaIds;
import cn.howxu.mmcr.util.ReadableNumber;
import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.recipe.EmiRecipeCategory;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.api.widget.WidgetHolder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/** Native recipe with full, owned IO independent of the visible viewport.
 * @author howxu <dev@howxu.cn>
 */
public final class MachineEmiRecipe implements EmiRecipe {
    private final EmiRecipeCategory category;
    private final MachineRecipeData data;
    private final List<Component> information;
    private final List<Resource> resources;
    private final List<EmiIngredient> inputs;
    private final List<EmiStack> outputs;
    private final List<RecipeMetadataRow> details;
    private final boolean supportsTree;

    public MachineEmiRecipe(EmiRecipeCategory category, MachineRecipeData data, List<Component> information) {
        this.category = category;
        this.data = data;
        this.information = information.stream().map(ComponentSnapshots::copy).toList();
        resources = data.entries().stream().map(entry -> new Resource(entry, EmiIngredientAdapters.convert(entry))).toList();
        inputs = resources.stream().filter(resource -> resource.entry().io() == IOType.INPUT)
                .map(Resource::ingredient).filter(ingredient -> !ingredient.isEmpty()).map(EmiIngredientAdapters::deepCopy).toList();
        outputs = resources.stream().filter(resource -> resource.entry().io() == IOType.OUTPUT && resource.entry().chance() > 0F)
                .flatMap(resource -> resource.ingredient().getEmiStacks().stream()).filter(stack -> !stack.isEmpty())
                .map(EmiStack::copy).toList();
        supportsTree = EmiIngredientAdapters.supportsRecipeTree(data);
        List<RecipeMetadataRow> rows = new ArrayList<>(data.metadata());
        // Neutral data currently includes information at its tail. Replace it with this cycle's owned information.
        int tail = rows.size() - this.information.size();
        if (!this.information.isEmpty() && tail >= 0) {
            boolean matches = true;
            for (int i = 0; i < this.information.size(); i++) {
                if (!rows.get(tail + i).text().equals(this.information.get(i))) { matches = false; break; }
            }
            if (matches) rows.subList(tail, rows.size()).clear();
        }
        this.information.forEach(text -> rows.add(new RecipeMetadataRow(text, List.of(), List.of())));
        for (Resource resource : resources) {
            RecipeDisplayEntry entry = resource.entry();
            if (entry.typeId().equals(BotaniaManaIds.MANA)) {
                // Mana is metadata, never a fake consumable item in IO or a clickable slot.
                if (BuiltInRegistries.ITEM.containsKey(BotaniaManaIds.CREATIVE_POOL)) {
                    for (int i = 0; i < rows.size(); i++) {
                        RecipeMetadataRow row = rows.get(i);
                        if (entry.tooltip().stream().anyMatch(text -> text.equals(row.text()))) {
                            rows.set(i, new RecipeMetadataRow(row.text(), row.tooltip(),
                                    List.of(new ItemStack(BuiltInRegistries.ITEM.get(BotaniaManaIds.CREATIVE_POOL)))));
                        }
                    }
                }
            } else if (resource.ingredient().isEmpty()) {
                Component label = entry.kind() == RecipeDisplayEntry.Kind.TEXT
                        ? Component.translatable("emi.mmcr.recipe.unknown_resource", entry.typeId().toString())
                        : Component.translatable("emi.mmcr.recipe.empty_ingredient");
                rows.add(new RecipeMetadataRow(label, entry.tooltip(), List.of()));
                entry.tooltip().forEach(text -> rows.add(new RecipeMetadataRow(text, List.of(), List.of())));
                String amountKey = switch (entry.kind()) {
                    case ITEM -> "jei.mmcr.machine_recipe.item_count";
                    case FLUID -> "jei.mmcr.machine_recipe.fluid_amount";
                    case CHEMICAL -> "jei.mmcr.machine_recipe.chemical_amount";
                    case SOURCE -> entry.io() == IOType.INPUT ? "jei.mmcr.machine_recipe.source_input" : "jei.mmcr.machine_recipe.source_output";
                    case TEXT -> null;
                };
                if (amountKey != null) rows.add(new RecipeMetadataRow(Component.translatable(amountKey,
                        ReadableNumber.formatExact(entry.amount())), List.of(), List.of()));
                Component chance = RecipeDisplayDrawing.chanceLabel(entry.io() == IOType.INPUT, entry.chance());
                if (!chance.getString().isEmpty()) rows.add(new RecipeMetadataRow(chance, List.of(), List.of()));
            }
        }
        if (!supportsTree) rows.add(new RecipeMetadataRow(Component.translatable("emi.mmcr.recipe.tree_unavailable"), List.of(), List.of()));
        details = List.copyOf(rows);
    }

    public MachineRecipeData data() { return data; }
    public List<Component> information() { return information.stream().map(ComponentSnapshots::copy).toList(); }
    List<Resource> resources() {
        return resources.stream().map(resource -> new Resource(resource.entry(),
                EmiIngredientAdapters.deepCopy(resource.ingredient()))).toList();
    }
    List<RecipeMetadataRow> details() { return details; }
    @Override public EmiRecipeCategory getCategory() { return category; }
    @Override public ResourceLocation getId() { return id("recipe", data.recipeId()); }
    static ResourceLocation id(String kind, ResourceLocation original) {
        return ResourceLocation.fromNamespaceAndPath("mmcr", "/" + kind + "/" + original.getNamespace() + "/" + original.getPath());
    }
    @Override public List<EmiIngredient> getInputs() { return inputs.stream().map(EmiIngredientAdapters::deepCopy).toList(); }
    @Override public List<EmiStack> getOutputs() { return outputs.stream().map(EmiStack::copy).toList(); }
    @Override public int getDisplayWidth() { return 168; }
    @Override public int getDisplayHeight() { return 180; }
    @Override public boolean supportsRecipeTree() { return supportsTree; }
    @Override public void addWidgets(WidgetHolder holder) { MachineRecipeEmiWidget.add(holder, this); }

    /** Entry, icon, tooltip, and quantity are always bound together before sorting.
     * @author howxu <dev@howxu.cn>
     */
    record Resource(RecipeDisplayEntry entry, EmiIngredient ingredient) { }
}

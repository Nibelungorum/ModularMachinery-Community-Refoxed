package cn.howxu.mmcr.internal.api.facade.recipe;
import cn.howxu.mmcr.api.compat.mekanism.ChemicalIngredient;
import cn.howxu.mmcr.api.compat.mekanism.ChemicalOutput;
import cn.howxu.mmcr.api.recipe.MachineRecipeBuilder;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;
/** Delegates payload encoding without loading Mekanism implementation classes. @author howxu <dev@howxu.cn> */
public final class MekanismIoAdapters {
    private MekanismIoAdapters() {}
    public static JsonObject chemicalInputPayload(ResourceLocation id, long amount) { return MachineRecipeBuilder.chemicalInputPayload(ChemicalIngredient.chemical(id, amount)); }
    public static JsonObject chemicalInputPayload(ResourceLocation id, long amount, float chance) { return MachineRecipeBuilder.chemicalInputPayload(ChemicalIngredient.chemical(id, amount), chance); }
    public static JsonObject chemicalTagInputPayload(ResourceLocation id, long amount) { return MachineRecipeBuilder.chemicalInputPayload(ChemicalIngredient.tag(id, amount)); }
    public static JsonObject chemicalTagInputPayload(ResourceLocation id, long amount, float chance) { return MachineRecipeBuilder.chemicalInputPayload(ChemicalIngredient.tag(id, amount), chance); }
    public static JsonObject chemicalOutputPayload(ResourceLocation id, long amount, float chance) { return MachineRecipeBuilder.chemicalOutputPayload(ChemicalOutput.of(id, amount, chance)); }
    public static JsonObject heatInputPayload(double value) { return MachineRecipeBuilder.heatInputPayload(value); }
    public static JsonObject heatOutputPayload(double value) { return MachineRecipeBuilder.heatOutputPayload(value); }
}

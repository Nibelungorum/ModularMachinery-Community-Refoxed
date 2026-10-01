package cn.howxu.mmcr.publicapi.recipe;
import cn.howxu.mmcr.internal.api.facade.recipe.MekanismIoAdapters;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;
/** Optional-mod-safe scalar declaration payload factories. @author howxu <dev@howxu.cn> */
public final class MekanismIo {
    private MekanismIo() {}
    public static JsonObject chemicalInputPayload(ResourceLocation id, long amount) { return MekanismIoAdapters.chemicalInputPayload(id, amount); }
    public static JsonObject chemicalInputPayload(ResourceLocation id, long amount, float consumeChance) { return MekanismIoAdapters.chemicalInputPayload(id, amount, consumeChance); }
    public static JsonObject chemicalTagInputPayload(ResourceLocation id, long amount) { return MekanismIoAdapters.chemicalTagInputPayload(id, amount); }
    public static JsonObject chemicalTagInputPayload(ResourceLocation id, long amount, float consumeChance) { return MekanismIoAdapters.chemicalTagInputPayload(id, amount, consumeChance); }
    public static JsonObject chemicalOutputPayload(ResourceLocation id, long amount, float chance) { return MekanismIoAdapters.chemicalOutputPayload(id, amount, chance); }
    public static JsonObject heatInputPayload(double value) { return MekanismIoAdapters.heatInputPayload(value); }
    public static JsonObject heatOutputPayload(double value) { return MekanismIoAdapters.heatOutputPayload(value); }
}

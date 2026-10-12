package cn.howxu.mmcr.compat.mekanism.loaded;

import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.api.stack.ListEmiIngredient;
import mekanism.api.IMekanismAccess;
import mekanism.api.MekanismAPI;
import mekanism.api.chemical.Chemical;
import mekanism.api.integration.emi.IMekanismEmiHelper;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Loaded only when EMI and Mekanism are both available; uses Mekanism's own stack identity.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class MekanismEmiIngredients {
    private MekanismEmiIngredients() {
    }

    public static EmiIngredient convert(List<ResourceLocation> chemicals, long amount) {
        if (amount <= 0 || chemicals.isEmpty()) return EmiStack.EMPTY;
        IMekanismEmiHelper helper = IMekanismAccess.INSTANCE.emiHelper();
        List<EmiStack> stacks = new ArrayList<>();
        for (ResourceLocation id : chemicals) {
            Optional<Holder.Reference<Chemical>> holder = MekanismAPI.CHEMICAL_REGISTRY.getHolder(
                    ResourceKey.create(MekanismAPI.CHEMICAL_REGISTRY_NAME, id));
            if (holder.isPresent() && !holder.get().value().isEmptyType()) {
                EmiStack stack = helper.createEmiStack(holder.get(), amount);
                if (!stack.isEmpty()) stacks.add(stack);
            }
        }
        if (stacks.isEmpty()) return EmiStack.EMPTY;
        return stacks.size() == 1 ? stacks.getFirst()
                : new ListEmiIngredient(List.copyOf(stacks), amount);
    }
}

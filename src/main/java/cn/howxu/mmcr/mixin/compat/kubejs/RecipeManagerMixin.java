package cn.howxu.mmcr.mixin.compat.kubejs;

import cn.howxu.mmcr.compat.kubejs.KubeJSRecipeSync;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeMap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mirrors KubeJS-created MMCR recipes into MMCR's machine recipe index.
 *
 * @author howxu <dev@howxu.cn>
 */
@Mixin(RecipeManager.class)
public abstract class RecipeManagerMixin {
    @Inject(method = "apply(Lnet/minecraft/world/item/crafting/RecipeMap;Lnet/minecraft/server/packs/resources/ResourceManager;Lnet/minecraft/util/profiling/ProfilerFiller;)V", at = @At("TAIL"))
    private void mmcr$syncKubeJSRecipes(RecipeMap recipeMap, ResourceManager resourceManager, ProfilerFiller profiler, CallbackInfo ci) {
        KubeJSRecipeSync.replaceDataPackRecipes(recipeMap.values());
    }
}

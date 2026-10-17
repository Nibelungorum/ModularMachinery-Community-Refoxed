package cn.howxu.mmcr.mixin.compat.ae2lt;

import appeng.api.stacks.GenericStack;
import appeng.menu.me.items.PatternEncodingTermMenu;
import cn.howxu.mmcr.compat.ae2lt.client.LightningJeiDisplay;
import cn.howxu.mmcr.compat.ae2lt.client.LightningPatternTransfer;
import cn.howxu.mmcr.compat.jei.MachineRecipeDisplay;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.recipe.transfer.IRecipeTransferError;
import mezz.jei.api.recipe.transfer.IRecipeTransferHandlerHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import tamaized.ae2jeiintegration.integration.modules.jei.GenericEntryStackHelper;
import java.util.List;

/** Keeps native terminal handling while supplying exact MMCR lightning amounts.
 * @author howxu <dev@howxu.cn>
 */
@Pseudo
@Mixin(targets = "tamaized.ae2jeiintegration.integration.modules.jei.transfer.EncodePatternTransferHandler", remap = false)
public abstract class JeiEncodePatternTransferMixin {
    @Shadow @Final private IRecipeTransferHandlerHelper helper;

    @Inject(method = "transferRecipe(Lappeng/menu/me/items/PatternEncodingTermMenu;Ljava/lang/Object;Lmezz/jei/api/gui/ingredient/IRecipeSlotsView;Lnet/minecraft/world/entity/player/Player;ZZ)Lmezz/jei/api/recipe/transfer/IRecipeTransferError;",
            at = @At("HEAD"), cancellable = true)
    private void mmcr$checkLightningCapacity(PatternEncodingTermMenu menu, Object recipeBase, IRecipeSlotsView slots,
            Player player, boolean maxTransfer, boolean doTransfer, CallbackInfoReturnable<IRecipeTransferError> callback) {
        if (!(recipeBase instanceof MachineRecipeDisplay display) || LightningJeiDisplay.requirements(display).isEmpty()) return;
        var transfer = LightningPatternTransfer.prepare(display, GenericEntryStackHelper.ofInputs(slots), GenericEntryStackHelper.ofOutputs(slots));
        int inputSlots = menu.getProcessingInputSlots().length;
        int outputSlots = menu.getProcessingOutputSlots().length;
        if (!transfer.fits(inputSlots, outputSlots)) callback.setReturnValue(helper.createUserErrorWithTooltip(Component.translatable(
                "jei.mmcr.transfer.pattern_not_enough_slots", transfer.inputs().size(), transfer.outputs().size(), inputSlots, outputSlots)));
    }

    @WrapOperation(method = "transferRecipe(Lappeng/menu/me/items/PatternEncodingTermMenu;Ljava/lang/Object;Lmezz/jei/api/gui/ingredient/IRecipeSlotsView;Lnet/minecraft/world/entity/player/Player;ZZ)Lmezz/jei/api/recipe/transfer/IRecipeTransferError;",
            at = @At(value = "INVOKE", target = "Lappeng/integration/modules/itemlists/EncodingHelper;encodeProcessingRecipe(Lappeng/menu/me/items/PatternEncodingTermMenu;Ljava/util/List;Ljava/util/List;)V"))
    private void mmcr$encodeLightning(PatternEncodingTermMenu menu, List<List<GenericStack>> inputs, List<GenericStack> outputs,
                                     Operation<Void> original, @Local(argsOnly = true) Object recipeBase) {
        if (recipeBase instanceof MachineRecipeDisplay display && !LightningJeiDisplay.requirements(display).isEmpty()) {
            var transfer = LightningPatternTransfer.prepare(display, inputs, outputs);
            original.call(menu, transfer.inputs(), transfer.outputs());
        } else original.call(menu, inputs, outputs);
    }
}

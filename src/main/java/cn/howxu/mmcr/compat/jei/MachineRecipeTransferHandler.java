package cn.howxu.mmcr.compat.jei;

import cn.howxu.mmcr.internal.menu.ItemBusMenu;
import cn.howxu.mmcr.registry.ModUIs;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.recipe.transfer.IRecipeTransferError;
import mezz.jei.api.recipe.transfer.IRecipeTransferHandler;
import mezz.jei.api.recipe.transfer.IRecipeTransferHandlerHelper;
import mezz.jei.api.recipe.types.IRecipeType;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MenuType;
import org.jspecify.annotations.Nullable;

import java.util.Optional;

/**
 * Transfers JEI item inputs into an item input bus.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class MachineRecipeTransferHandler implements IRecipeTransferHandler<ItemBusMenu, MachineRecipeDisplay> {

    private final IRecipeTransferHandlerHelper helper;
    private final IRecipeTransferHandler<ItemBusMenu, MachineRecipeDisplay> basicHandler;

    public MachineRecipeTransferHandler(IRecipeTransferHandlerHelper helper) {
        this.helper = helper;
        this.basicHandler = helper.createUnregisteredRecipeTransferHandler(
                helper.createBasicRecipeTransferInfo(
                        ItemBusMenu.class,
                        ModUIs.ITEM_BUS.get(),
                        JeiMachineRecipeTypes.MACHINE_RECIPE,
                        ItemBusMenu.BUS_SLOT_START,
                        ItemBusMenu.BUS_SLOT_COUNT,
                        ItemBusMenu.PLAYER_INVENTORY_SLOT_START,
                        ItemBusMenu.PLAYER_INVENTORY_SLOT_COUNT));
    }

    @Override
    public Class<? extends ItemBusMenu> getContainerClass() {
        return ItemBusMenu.class;
    }

    @Override
    public Optional<MenuType<ItemBusMenu>> getMenuType() {
        return Optional.of(ModUIs.ITEM_BUS.get());
    }

    @Override
    public IRecipeType<MachineRecipeDisplay> getRecipeType() {
        return JeiMachineRecipeTypes.MACHINE_RECIPE;
    }

    @Override
    public @Nullable IRecipeTransferError transferRecipe(ItemBusMenu container, MachineRecipeDisplay recipe, IRecipeSlotsView recipeSlots, Player player, boolean maxTransfer, boolean doTransfer) {
        if (!recipe.fluidInputs().isEmpty() || !recipe.energyInputs().isEmpty()) {
            return helper.createUserErrorWithTooltip(Component.translatable("jei.mmcr.transfer.fluid_energy_not_supported"));
        }
        if (recipe.itemInputs().isEmpty()) {
            return helper.createUserErrorWithTooltip(Component.translatable("jei.mmcr.transfer.no_item_inputs"));
        }
        return basicHandler.transferRecipe(container, recipe, recipeSlots, player, maxTransfer, doTransfer);
    }
}

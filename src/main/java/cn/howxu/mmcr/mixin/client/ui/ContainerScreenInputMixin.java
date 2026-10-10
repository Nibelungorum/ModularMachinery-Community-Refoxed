package cn.howxu.mmcr.mixin.client.ui;

import cn.howxu.mmcr.client.controller.ui.ContainerScreenInputControl;
import cn.howxu.mmcr.internal.menu.ControllerUiMenu;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.lang.reflect.Field;
import java.util.Set;

/** Keeps vanilla slot interaction out of hidden controller inventories.
 * @author howxu <dev@howxu.cn> */
@Mixin(AbstractContainerScreen.class)
public abstract class ContainerScreenInputMixin implements ContainerScreenInputControl {
    @Shadow @Final protected AbstractContainerMenu menu;
    @Shadow protected Slot hoveredSlot;
    @Shadow private Slot clickedSlot;
    @Shadow private Slot lastClickSlot;
    @Shadow private Slot quickdropSlot;
    @Shadow @Final protected Set<Slot> quickCraftSlots;
    @Shadow protected boolean isQuickCrafting;
    @Shadow private ItemStack draggingItem;
    @Shadow private boolean isSplittingStack;
    @Shadow private boolean doubleclick;
    @Shadow private ItemStack lastQuickMoved;
    @Shadow private boolean skipNextRelease;
    @Unique private static final Field mmcr$snapbackField = mmcr$findSnapbackField();

    @Unique
    private static Field mmcr$findSnapbackField() {
        try {
            Field field = AbstractContainerScreen.class.getDeclaredField("snapbackData");
            field.setAccessible(true);
            return field;
        } catch (ReflectiveOperationException exception) {
            throw new ExceptionInInitializerError(exception);
        }
    }

    @Override
    public void resetControllerSlotInput() {
        if (!(menu instanceof ControllerUiMenu)) return;
        hoveredSlot = null;
        clickedSlot = null;
        lastClickSlot = null;
        quickdropSlot = null;
        quickCraftSlots.clear();
        isQuickCrafting = false;
        draggingItem = ItemStack.EMPTY;
        isSplittingStack = false;
        doubleclick = false;
        lastQuickMoved = ItemStack.EMPTY;
        skipNextRelease = true;
        try {
            mmcr$snapbackField.set(this, null);
        } catch (IllegalAccessException exception) {
            throw new IllegalStateException("Cannot clear controller screen snapback animation", exception);
        }
    }

    @Unique
    private boolean mmcr$hidden() {
        return menu instanceof ControllerUiMenu controller && !controller.playerInventoryVisible();
    }

    @Unique
    private void mmcr$clearHiddenHover() {
        if (mmcr$hidden()) hoveredSlot = null;
    }

    @Inject(method = "keyPressed", at = @At("HEAD"))
    private void mmcr$key(KeyEvent event, CallbackInfoReturnable<Boolean> callback) {
        mmcr$clearHiddenHover();
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"))
    private void mmcr$click(MouseButtonEvent event, boolean doubleClick, CallbackInfoReturnable<Boolean> callback) {
        mmcr$clearHiddenHover();
    }

    @Inject(method = "mouseDragged", at = @At("HEAD"))
    private void mmcr$drag(MouseButtonEvent event, double dx, double dy, CallbackInfoReturnable<Boolean> callback) {
        mmcr$clearHiddenHover();
    }

    @Inject(method = "mouseReleased", at = @At("HEAD"))
    private void mmcr$release(MouseButtonEvent event, CallbackInfoReturnable<Boolean> callback) {
        mmcr$clearHiddenHover();
    }

    @Inject(method = "mouseScrolled", at = @At("HEAD"))
    private void mmcr$scroll(double x, double y, double dx, double dy, CallbackInfoReturnable<Boolean> callback) {
        mmcr$clearHiddenHover();
    }

    @Inject(method = "slotClicked", at = @At("HEAD"), cancellable = true)
    private void mmcr$slotClick(Slot slot, int slotId, int button, ContainerInput input, CallbackInfo callback) {
        if (mmcr$hidden()) callback.cancel();
    }

    @Inject(method = "hasClickedOutside", at = @At("HEAD"), cancellable = true)
    private void mmcr$outside(double x, double y, int left, int top, CallbackInfoReturnable<Boolean> callback) {
        if (mmcr$hidden()) callback.setReturnValue(false);
    }
}

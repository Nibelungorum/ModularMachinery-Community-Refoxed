package cn.howxu.mmcr.compat.jei;

import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.gui.ingredient.IRecipeSlotDrawable;
import mezz.jei.api.gui.widgets.IRecipeWidget;
import mezz.jei.api.ingredients.IIngredientType;
import mezz.jei.api.ingredients.ITypedIngredient;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.navigation.ScreenPosition;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Optional;
import java.util.function.LongSupplier;

/**
 * Displays the default-stage structure materials in a nine-slot rotating page.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class StructureMaterialWidget implements IRecipeWidget {
    public static final int SLOT_COUNT = 9;
    public static final int SLOT_STEP = 19;
    public static final long PAGE_DURATION_MILLIS = 8_000L;

    private final StructureMaterialSummary summary;
    private final List<IRecipeSlotDrawable> slots;
    private final int x;
    private final int y;
    private final LongSupplier clock;
    private int displayedPage = -1;

    public StructureMaterialWidget(StructureMaterialSummary summary, List<IRecipeSlotDrawable> slots,
            int x, int y) {
        this(summary, slots, x, y, System::currentTimeMillis);
    }

    StructureMaterialWidget(StructureMaterialSummary summary, List<IRecipeSlotDrawable> slots,
            int x, int y, LongSupplier clock) {
        if (slots.size() != SLOT_COUNT) throw new IllegalArgumentException("nine material slots required");
        this.summary = summary;
        this.slots = List.copyOf(slots);
        this.x = x;
        this.y = y;
        this.clock = clock;
        applyPage(pageFor(clock.getAsLong(), summary.entries().size()));
    }

    @Override
    public ScreenPosition getPosition() {
        return new ScreenPosition(x, y);
    }

    @Override
    public void tick() {
        int page = pageFor(clock.getAsLong(), summary.entries().size());
        if (page != displayedPage) applyPage(page);
    }

    static void refreshPage(StructureMaterialSummary summary, List<IRecipeSlotDrawable> slots, long timeMillis) {
        if (slots.size() != SLOT_COUNT) throw new IllegalArgumentException("nine material slots required");
        int page = pageFor(timeMillis, summary.entries().size());
        applyPage(summary, slots, page);
    }

    @Override
    public void drawWidget(GuiGraphicsExtractor graphics, double mouseX, double mouseY) {
        int first = displayedPage * SLOT_COUNT;
        for (int slotIndex = 0; slotIndex < SLOT_COUNT; slotIndex++) {
            int entryIndex = first + slotIndex;
            if (entryIndex >= summary.entries().size()) continue;
            String quantity = MachineRecipeCategory.itemQuantityText(summary.entries().get(entryIndex).count());
            if (!quantity.isEmpty()) {
                new JeiSlotOverlayDrawable("", quantity).draw(graphics,
                        slotIndex * SLOT_STEP, 0);
            }
        }
    }

    static int pageFor(long timeMillis, int materialCount) {
        int pageCount = (materialCount + SLOT_COUNT - 1) / SLOT_COUNT;
        return pageCount == 0 ? 0 : (int) Math.floorDiv(timeMillis, PAGE_DURATION_MILLIS) % pageCount;
    }

    private void applyPage(int page) {
        displayedPage = page;
        applyPage(summary, slots, page);
    }

    private static void applyPage(StructureMaterialSummary summary, List<IRecipeSlotDrawable> slots, int page) {
        int first = page * SLOT_COUNT;
        for (int slotIndex = 0; slotIndex < SLOT_COUNT; slotIndex++) {
            int entryIndex = first + slotIndex;
            List<Optional<ITypedIngredient<?>>> values = List.of(entryIndex < summary.entries().size()
                    ? Optional.of(new ItemStackIngredient(summary.entries().get(entryIndex).stack()))
                    : Optional.empty());
            IRecipeSlotDrawable slot = slots.get(slotIndex);
            slot.clearDisplayOverrides();
            slot.createDisplayOverrides().addOptionalTypedIngredients(values);
        }
    }

    private record ItemStackIngredient(ItemStack stack) implements ITypedIngredient<ItemStack> {
        @Override
        public IIngredientType<ItemStack> getType() {
            return VanillaTypes.ITEM_STACK;
        }

        @Override
        public ItemStack getIngredient() {
            return stack.copyWithCount(1);
        }
    }
}

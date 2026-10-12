package cn.howxu.mmcr.client.recipe;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.recipe.modifier.RecipeModifier.IOType;
import cn.howxu.mmcr.client.render.GuiClipContext;
import cn.howxu.mmcr.util.ReadableNumber;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.BiFunction;
import java.util.function.BiConsumer;

/**
 * Client drawing shared by recipe viewers; no JEI or EMI types enter these APIs.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class RecipeDisplayDrawing {
    public static final float TEXT_SCALE = 0.85F;
    public static final float OVERLAY_SCALE = 0.6F;
    public static final int TEXT_LINE_SPACING = 10;
    private static final int TEXT_COLOR = 0xFF404040;

    private RecipeDisplayDrawing() {}

    public static void drawBackground(GuiGraphics graphics, boolean structure, int x, int y, int width, int height) {
        int scale = (int) Math.round(Minecraft.getInstance().getWindow().getGuiScale());
        scale = Math.max(1, Math.min(4, scale));
        ResourceLocation texture = MMCR.id("textures/gui/jei/" + (structure ? "preview" : "recipe") + "/" + scale + "x.png");
        graphics.blit(texture, x, y, 0.0F, 0.0F, width, height, width, height);
    }

    public static Component chanceLabel(boolean input, float chance) {
        if (input && chance == 0F) return Component.translatable("jei.mmcr.machine_recipe.keep");
        return Component.literal(chance < 1F ? Math.round(chance * 100F) + "%" : "");
    }

    public static String itemQuantityText(long amount) {
        return amount > 1 ? ReadableNumber.formatForSlot(amount, 0, "") : "";
    }

    public static String fluidQuantityText(long amount) {
        return amount <= 10 ? ReadableNumber.formatForSlot(amount, 0, "mB")
                : ReadableNumber.formatForSlot(amount, 3, "B");
    }

    public static String chemicalQuantityText(long amount) {
        return ReadableNumber.formatForSlot(amount, 3, "B");
    }

    public static String quantityText(RecipeDisplayEntry.Kind kind, long amount) {
        return switch (kind) {
            case ITEM -> itemQuantityText(amount);
            case FLUID -> fluidQuantityText(amount);
            case CHEMICAL -> chemicalQuantityText(amount);
            case SOURCE -> ReadableNumber.formatForSlot(amount, 0, "");
            case TEXT -> "";
        };
    }

    public static void drawSlotOverlay(GuiGraphics graphics, RecipeDisplayEntry entry, int x, int y) {
        drawSlotOverlay(graphics, x, y, chanceLabel(entry.io() == IOType.INPUT, entry.chance()).getString(),
                quantityText(entry.kind(), entry.amount()));
    }

    public static void drawSlotOverlay(GuiGraphics graphics, int x, int y, String chanceText, String quantityText) {
        var font = Minecraft.getInstance().font;
        graphics.pose().pushPose();
        try {
            graphics.pose().translate(x, y, 0.0F);
            graphics.pose().scale(OVERLAY_SCALE, OVERLAY_SCALE, 1.0F);
            if (!chanceText.isEmpty()) graphics.drawString(font, chanceText, 0, 0, 0xFFFF4040, false);
            if (!quantityText.isEmpty()) {
                int textX = Math.max(0, (int) (16 / OVERLAY_SCALE) - font.width(quantityText)) + 1;
                int textY = (int) (16 / OVERLAY_SCALE) - font.lineHeight + 1;
                graphics.drawString(font, quantityText, textX, textY, 0xFFFFFFFF, true);
            }
        } finally {
            graphics.pose().popPose();
        }
    }

    /** Draws a font-height icon and label in the caller's current coordinate system. */
    public static void drawIconLabel(GuiGraphics graphics, Component label, int x, int y,
                                     int iconWidth, int iconHeight, Consumer<GuiGraphics> drawIcon) {
        var font = Minecraft.getInstance().font;
        if (iconHeight > 0) {
            float scale = (float) font.lineHeight / iconHeight;
            graphics.pose().pushPose();
            try {
                graphics.pose().translate(x, y, 0F);
                graphics.pose().scale(scale, scale, 1F);
                drawIcon.accept(graphics);
            } finally {
                graphics.pose().popPose();
            }
            x += (int) (iconWidth * scale) + 2;
        }
        graphics.drawString(font, label, x, y, TEXT_COLOR, false);
    }

    /** Measures complete wrapped rows before pagination, in unscaled viewer pixels. */
    public static List<Integer> metadataRowHeights(List<RecipeMetadataRow> rows, int width) {
        return rows.stream().map(row -> wrapMetadata(row, width, Minecraft.getInstance().font::split).height()).toList();
    }

    public static WrappedText wrapMetadata(RecipeMetadataRow row, int width,
                                           BiFunction<Component, Integer, List<FormattedCharSequence>> splitter) {
        // The vanilla font's 9px-high icon and 2px gap match drawMetadataRow below.
        int iconWidth = row.icons().isEmpty() ? 0 : 11;
        return new WrappedText(splitter.apply(row.text(), metadataTextWidth(width, iconWidth)));
    }

    public static int metadataTextWidth(int width, int iconWidth) {
        return Math.max(1, (int) (width / TEXT_SCALE) - iconWidth);
    }

    /** The exact lines measured for pagination are also emitted for drawing.
     * @author howxu <dev@howxu.cn>
     */
    public record WrappedText(List<FormattedCharSequence> lines) {
        public WrappedText { lines = List.copyOf(lines); }
        public int height() { return Math.max(1, lines.size()) * TEXT_LINE_SPACING; }
        public void drawLines(BiConsumer<FormattedCharSequence, Integer> draw) {
            for (int index = 0; index < lines.size(); index++) draw.accept(lines.get(index), index * TEXT_LINE_SPACING);
        }
    }

    /** Draws every wrapped line and cycles the neutral row's optional representative icons. */
    public static void drawMetadataRow(GuiGraphics graphics, RecipeMetadataRow row, int x, int y, int width) {
        drawMetadataRow(graphics, row, wrapMetadata(row, width, Minecraft.getInstance().font::split), x, y);
    }

    public static void drawMetadataRow(GuiGraphics graphics, RecipeMetadataRow row, WrappedText wrapped, int x, int y) {
        var minecraft = Minecraft.getInstance();
        var font = minecraft.font;
        var icons = row.icons();
        graphics.pose().pushPose();
        try {
            graphics.pose().translate(x, y, 0);
            graphics.pose().scale(TEXT_SCALE, TEXT_SCALE, 1F);
            int textX = 0;
            if (!icons.isEmpty()) {
                long gameTime = minecraft.level == null ? 0L : minecraft.level.getGameTime();
                var icon = icons.get((int) ((gameTime / 60) % icons.size()));
                float iconScale = (float) font.lineHeight / 16;
                graphics.pose().pushPose();
                try {
                    graphics.pose().scale(iconScale, iconScale, 1F);
                    graphics.renderFakeItem(icon, 0, 0);
                } finally {
                    graphics.pose().popPose();
                }
                textX = font.lineHeight + 2;
            }
            int labelX = textX;
            wrapped.drawLines((line, offsetY) -> graphics.drawString(font, line, labelX,
                    (int) (offsetY / TEXT_SCALE), TEXT_COLOR, false));
        } finally {
            graphics.pose().popPose();
        }
    }

    /** Applies viewport clipping in the current pose, also used for tooltip/input bounds. */
    public static void drawClipped(GuiGraphics graphics, RecipeDetailsLayout.Viewport viewport, Runnable draw) {
        GuiClipContext.drawClipped(graphics, viewport.x(), viewport.y(), viewport.width(), viewport.height(), draw);
    }
}

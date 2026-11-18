package cn.howxu.mmcr.compat.ae2lt.client;

import cn.howxu.mmcr.api.compat.ae2lt.LightningTier;
import cn.howxu.mmcr.api.recipe.modifier.RecipeModifier.IOType;
import cn.howxu.mmcr.compat.ae2lt.LightningRequirement;
import cn.howxu.mmcr.compat.jei.MachineRecipeDisplay;
import cn.howxu.mmcr.compat.jei.JeiRecipeLine;
import cn.howxu.mmcr.compat.ae2lt.LightningRecipeTypes;
import java.util.Optional;
import com.moakiee.ae2lt.integration.jei.LightningJeiIngredients;
import com.moakiee.ae2lt.me.key.LightningKey;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.recipe.RecipeIngredientRole;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.neoforged.fml.ModList;

import java.util.Comparator;
import java.util.List;

/**
 * Client-only lightning metadata and lazily linked native JEI ingredients.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class LightningJeiDisplay {
    private LightningJeiDisplay() {
    }

    public static List<LightningRequirement> requirements(MachineRecipeDisplay display) {
        return display.recipe().runtimeRequirements().stream()
                .filter(LightningRequirement.class::isInstance)
                .map(LightningRequirement.class::cast)
                .sorted(Comparator.comparingInt(requirement -> requirement.io() == IOType.INPUT ? 0 : 1))
                .toList();
    }

    private static boolean registered;
    public static synchronized void register() {
        if (registered) return;
        for (var tier : LightningTier.values()) {
            JeiRecipeLine.Registry.register(LightningRecipeTypes.requirementType(tier), LightningRequirement.class, requirement -> new JeiRecipeLine() {
                @Override public void draw(GuiGraphicsExtractor graphics, int x, int y, int width) {
                    drawLine(graphics, requirement, x, y, width);
                }
                @Override public Optional<Component> tooltip() {
                    return requirement.io().isInput() ? Optional.empty() : Optional.of(Component.translatable(
                            "jei.mmcr.machine_recipe.output_chance", Math.round(requirement.chance() * 100F) + "%"));
                }
                @Override public void index(IRecipeLayoutBuilder builder) {
                    if (nativeAvailable()) NativeIngredients.add(builder, List.of(requirement));
                }
            });
        }
        registered = true;
    }

    public static Component label(LightningRequirement requirement) {
        String tier = requirement.tier() == LightningTier.HIGH_VOLTAGE ? "high_voltage" : "extreme_high_voltage";
        String direction = requirement.io() == IOType.INPUT ? "input" : "output";
        return Component.translatable("jei.mmcr.machine_recipe." + tier + "_" + direction,
                Long.toString(requirement.amount()));
    }

    public static void addInvisibleIngredients(IRecipeLayoutBuilder builder, MachineRecipeDisplay display) {
        if (nativeAvailable()) NativeIngredients.add(builder, requirements(display));
    }

    /** Draws in the caller's text-scaled coordinates, fitting the label on one line. */
    public static void drawLine(GuiGraphicsExtractor graphics, LightningRequirement requirement,
                                int x, int y, int width) {
        var font = Minecraft.getInstance().font;
        if (nativeAvailable()) {
            NativeIngredients.draw(graphics, requirement.tier(), x, y, font.lineHeight);
            int iconWidth = font.lineHeight + 2;
            x += iconWidth;
            width -= iconWidth;
        }
        Component label = label(requirement);
        float scale = Math.min(1F, (float) Math.max(1, width) / Math.max(1, font.width(label)));
        graphics.pose().pushMatrix();
        graphics.pose().translate(x, y + font.lineHeight * (1F - scale) / 2F);
        graphics.pose().scale(scale, scale);
        graphics.text(font, label, 0, 0, 0xFF404040, false);
        graphics.pose().popMatrix();
    }

    private static boolean nativeAvailable() {
        ModList mods = ModList.get();
        return mods != null && mods.isLoaded("ae2") && mods.isLoaded("ae2lt");
    }

    /**
     * Kept out of outer-class signatures and static initialization when AE2LT is absent.
     *
     * @author howxu <dev@howxu.cn>
     */
    private static final class NativeIngredients {
        private static LightningKey key(LightningTier tier) {
            return tier == LightningTier.HIGH_VOLTAGE ? LightningKey.HIGH_VOLTAGE : LightningKey.EXTREME_HIGH_VOLTAGE;
        }

        private static void add(IRecipeLayoutBuilder builder, List<LightningRequirement> requirements) {
            for (LightningRequirement requirement : requirements) {
                builder.addInvisibleIngredients(requirement.io() == IOType.INPUT
                                ? RecipeIngredientRole.INPUT : RecipeIngredientRole.OUTPUT)
                        .add(LightningJeiIngredients.TYPE, key(requirement.tier()));
            }
        }

        private static void draw(GuiGraphicsExtractor graphics, LightningTier tier, int x, int y, int height) {
            graphics.pose().pushMatrix();
            graphics.pose().translate(x, y);
            float scale = height / 16F;
            graphics.pose().scale(scale, scale);
            LightningJeiIngredients.RENDERER.render(graphics, key(tier), 0, 0);
            graphics.pose().popMatrix();
        }
    }
}

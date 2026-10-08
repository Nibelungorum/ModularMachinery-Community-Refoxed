package cn.howxu.mmcr.compat.jei;

import net.minecraft.client.Minecraft;
import cn.howxu.mmcr.api.recipe.requirement.FluidRequirement;
import cn.howxu.mmcr.api.recipe.requirement.ItemRequirement;
import cn.howxu.mmcr.compat.jei.RecipeSlotLayout.Arrow;
import cn.howxu.mmcr.compat.mekanism.MekanismRecipeTypes;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.neoforge.NeoForgeTypes;
import mezz.jei.api.recipe.RecipeIngredientRole;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Deterministic slot layout for MMCR machine recipes in JEI.
 *
 * @author howxu <dev@howxu.cn>
 */
public record MachineRecipeLayout(
        int width,
        int height,
        RegionPlan inputs,
        RegionPlan outputs,
        @Nullable Arrow arrow,
        int durationTextX,
        int hostRequirementTextY,
        int durationTextY,
        int transferButtonX,
        int transferButtonY
) {

    public static final int WIDTH = 150;
    public static final int HEIGHT = 150;

    private static final int SLOT_SIZE = 18;
    static final int TEXT_LINE_SPACING = 10;

    public static MachineRecipeLayout forDisplay(MachineRecipeDisplay display) {
        return forDisplay(display, Minecraft.getInstance().getWindow().getGuiScale());
    }

    public static MachineRecipeLayout forDisplay(MachineRecipeDisplay display, int guiScale) {
        List<JeiDisplayEntry> displayEntries = display.entries();
        List<EntryPlan> inputEntries = entries(displayEntries, RecipeIngredientRole.INPUT);
        List<EntryPlan> outputEntries = entries(displayEntries, RecipeIngredientRole.OUTPUT);
        RecipeSlotLayout slots = RecipeSlotLayout.forCounts(inputEntries.size(), outputEntries.size());
        int metadataY = slots.bottom() + 4;
        return new MachineRecipeLayout(WIDTH, HEIGHT,
                bind(inputEntries, slots.inputs()), bind(outputEntries, slots.outputs()), slots.arrow(),
                8, metadataY + TEXT_LINE_SPACING * metadataLineCount(display), metadataY,
                transferButtonXForGuiScale(guiScale), transferButtonYForGuiScale(guiScale));
    }

    private static int transferButtonXForGuiScale(int guiScale) {
        return switch (guiScale) {
            case 1 -> 152;
            case 2 -> 152;
            case 3 -> 152;
            default -> 152;
        };
    }

    private static int transferButtonYForGuiScale(int guiScale) {
        return switch (guiScale) {
            case 1 -> 285;
            case 2 -> 265;
            case 3 -> 205;
            default -> 135;
        };
    }
    private static List<EntryPlan> entries(List<JeiDisplayEntry> displayEntries, RecipeIngredientRole role) {
        List<EntryPlan> entries = new ArrayList<>();
        int itemIndex = 0;
        int fluidIndex = 0;
        int customItemIndex = 0;
        int customFluidIndex = 0;
        int chemicalIndex = 0;
        int textIndex = 0;
        for (JeiDisplayEntry entry : displayEntries.stream()
                .filter(candidate -> candidate.role() == role)
                .sorted(Comparator.comparingInt(MachineRecipeLayout::kindOrder))
                .toList()) {
            if (entry.ingredientType() == VanillaTypes.ITEM_STACK) {
                entries.add(new EntryPlan(Kind.ITEM,
                        entry.typeId().equals(ItemRequirement.TYPE.id()) ? itemIndex++ : customItemIndex++, entry));
            } else if (entry.ingredientType() == NeoForgeTypes.FLUID_STACK) {
                entries.add(new EntryPlan(Kind.FLUID,
                        entry.typeId().equals(FluidRequirement.TYPE.id()) ? fluidIndex++ : customFluidIndex++, entry));
            } else if (entry.typeId().equals(MekanismRecipeTypes.CHEMICAL)) {
                entries.add(new EntryPlan(Kind.CHEMICAL, chemicalIndex++, entry));
            } else if (!entry.isTextOnly()) {
                entries.add(new EntryPlan(Kind.GENERIC, textIndex++, entry));
            } else {
                entries.add(new EntryPlan(Kind.TEXT, textIndex++, entry));
            }
        }

        return List.copyOf(entries);
    }

    private static RegionPlan bind(List<EntryPlan> entries, List<RecipeSlotLayout.Position> positions) {
        List<SlotPlan> slots = new ArrayList<>(entries.size());
        for (int index = 0; index < entries.size(); index++) {
            RecipeSlotLayout.Position position = positions.get(index);
            slots.add(new SlotPlan(entries.get(index), position.x(), position.y()));
        }
        return new RegionPlan(List.copyOf(slots));
    }

    static RegionPlan regionForEntries(List<JeiDisplayEntry> displayEntries, RecipeIngredientRole role, int guiScale) {
        List<EntryPlan> entries = entries(displayEntries, role);
        return bind(entries, RecipeSlotLayout.forCounts(entries.size(), 0).inputs());
    }

    private static int kindOrder(JeiDisplayEntry entry) {
        if (entry.ingredientType() == NeoForgeTypes.FLUID_STACK) return 0;
        if (entry.typeId().equals(MekanismRecipeTypes.CHEMICAL)) return 1;
        if (entry.ingredientType() == VanillaTypes.ITEM_STACK) return 2;
        return 3;
    }

    public int levelRequirementY(MachineRecipeDisplay display, int index) {
        return levelRequirementSlotY(display, index);
    }

    public int levelRequirementSlotY(MachineRecipeDisplay display, int index) {
        int metadataY = display.requiredHostIds().isEmpty()
                ? durationTextY + TEXT_LINE_SPACING * metadataLineCount(display)
                : hostRequirementTextY + TEXT_LINE_SPACING;
        return metadataY + SLOT_SIZE * index;
    }

    public int smartInterfaceTextY(MachineRecipeDisplay display) {
        return stageRequirementTextY(display) + TEXT_LINE_SPACING * display.recipe().stageRequirements().size();
    }

    public int stageRequirementTextY(MachineRecipeDisplay display) {
        int levelCount = display.recipe().levelRequirements().size();
        if (levelCount > 0) {
            return levelRequirementSlotY(display, levelCount - 1) + SLOT_SIZE;
        }
        return durationTextY + TEXT_LINE_SPACING * (metadataLineCount(display)
                + (display.requiredHostIds().isEmpty() ? 0 : 1));
    }

    public int lastMetadataTextY(MachineRecipeDisplay display) {
        int smartInterfaceCount = display.smartInterfaceInputs().size() + display.smartInterfaceOutputs().size();
        if (smartInterfaceCount > 0) {
            return smartInterfaceTextY(display) + TEXT_LINE_SPACING * (smartInterfaceCount - 1);
        }
        int levelCount = display.recipe().levelRequirements().size();
        int stageCount = display.recipe().stageRequirements().size();
        if (stageCount > 0) {
            return stageRequirementTextY(display) + TEXT_LINE_SPACING * (stageCount - 1);
        }
        if (levelCount > 0) {
            return levelRequirementSlotY(display, levelCount - 1);
        }
        return durationTextY + TEXT_LINE_SPACING * (metadataLineCount(display) - 1
                + (display.requiredHostIds().isEmpty() ? 0 : 1));
    }

    public int informationTextY(MachineRecipeDisplay display) {
        int smartInterfaceCount = display.smartInterfaceInputs().size() + display.smartInterfaceOutputs().size();
        return smartInterfaceTextY(display) + TEXT_LINE_SPACING * smartInterfaceCount;
    }

    public int informationLineCapacity(MachineRecipeDisplay display, int categoryHeight) {
        int availableHeight = categoryHeight - informationTextY(display);
        return Math.max(0, availableHeight / TEXT_LINE_SPACING);
    }

    private static int metadataLineCount(MachineRecipeDisplay display) {
        return 1 + display.energyInputs().size() + display.energyOutputs().size()
                + (display.minimumTemperature().isPresent() ? 1 : 0)
                + (display.outputHeat().isPresent() ? 1 : 0);
    }

    public enum Kind { ITEM, FLUID, CHEMICAL, GENERIC, TEXT }

    public record EntryPlan(Kind kind, int index, @Nullable JeiDisplayEntry displayEntry) {
        public EntryPlan(Kind kind, int index) {
            this(kind, index, null);
        }
    }

    public record SlotPlan(EntryPlan entry, int x, int y) {}

    public record RegionPlan(List<SlotPlan> slots) {}
}

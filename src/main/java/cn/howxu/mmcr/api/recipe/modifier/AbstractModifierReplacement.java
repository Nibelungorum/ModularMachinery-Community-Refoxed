package cn.howxu.mmcr.api.recipe.modifier;

import net.minecraft.world.item.ItemStack;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public abstract class AbstractModifierReplacement {

    private static final AtomicInteger DEFAULT_NAME_COUNTER = new AtomicInteger(0);

    protected final String modifierName;
    protected final List<RecipeModifier> modifiers;
    protected final List<String> description;
    protected final ItemStack descriptiveStack;

    protected AbstractModifierReplacement(List<RecipeModifier> modifiers, String description, ItemStack descriptiveStack) {
        this(null, modifiers, description, descriptiveStack);
    }

    protected AbstractModifierReplacement(String modifierName, List<RecipeModifier> modifiers, String description, ItemStack descriptiveStack) {
        this.modifierName = modifierName == null
                ? "ReplacementModifier-" + DEFAULT_NAME_COUNTER.getAndIncrement()
                : modifierName;
        this.modifiers = modifiers == null ? Collections.emptyList() : List.copyOf(modifiers);
        this.description = description == null || description.isEmpty()
                ? Collections.emptyList()
                : List.of(description.split("\n"));
        this.descriptiveStack = descriptiveStack == null ? ItemStack.EMPTY : descriptiveStack;
    }

    protected AbstractModifierReplacement(String modifierName, List<RecipeModifier> modifiers, List<String> description, ItemStack descriptiveStack) {
        this.modifierName = modifierName;
        this.modifiers = modifiers == null ? Collections.emptyList() : List.copyOf(modifiers);
        this.description = description == null ? Collections.emptyList() : List.copyOf(description);
        this.descriptiveStack = descriptiveStack == null ? ItemStack.EMPTY : descriptiveStack;
    }

    public String getModifierName() {
        return modifierName;
    }

    public List<RecipeModifier> getModifiers() {
        return modifiers;
    }

    public List<String> getDescriptionLines() {
        return description;
    }

    public ItemStack getDescriptiveStack() {
        return descriptiveStack;
    }
}

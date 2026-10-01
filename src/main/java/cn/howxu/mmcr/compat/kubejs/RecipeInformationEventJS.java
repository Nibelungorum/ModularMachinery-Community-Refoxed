package cn.howxu.mmcr.compat.kubejs;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.recipe.RecipeInformation;
import dev.latvian.mods.kubejs.event.KubeEvent;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * KubeJS client event for localized JEI recipe information.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class RecipeInformationEventJS implements KubeEvent {
    private final boolean jeiAvailable;
    private final Consumer<String> warning;
    private final List<RecipeInformation> entries = new ArrayList<>();
    private boolean warnedUnavailable;

    RecipeInformationEventJS(boolean jeiAvailable) {
        this(jeiAvailable, message -> MMCR.LOG.warn(message));
    }

    RecipeInformationEventJS(boolean jeiAvailable, Consumer<String> warning) {
        this.jeiAvailable = jeiAvailable;
        this.warning = warning;
    }

    public void addRecipePoolInfo(String poolId, String translationKey, Object... arguments) {
        if (!available()) return;
        entries.add(RecipeInformation.pool(parseResourceLocation(poolId, "poolId"), translationKey, arguments));
    }

    public void addRecipeInfo(String recipeId, String translationKey, Object... arguments) {
        if (!available()) return;
        entries.add(RecipeInformation.recipe(parseResourceLocation(recipeId, "recipeId"), translationKey, arguments));
    }

    List<RecipeInformation> entries() {
        return List.copyOf(entries);
    }

    private boolean available() {
        if (jeiAvailable) return true;
        if (!warnedUnavailable) {
            warning.accept("JEI is not loaded; MMCR client recipe information registrations are ignored");
            warnedUnavailable = true;
        }
        return false;
    }

    private static ResourceLocation parseResourceLocation(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be null or blank");
        }
        ResourceLocation identifier = ResourceLocation.tryParse(value);
        if (identifier == null) throw new IllegalArgumentException("Invalid " + name + ": " + value);
        return identifier;
    }
}

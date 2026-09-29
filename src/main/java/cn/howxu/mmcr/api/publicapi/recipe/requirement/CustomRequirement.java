package cn.howxu.mmcr.api.publicapi.recipe.requirement;

import com.google.gson.JsonElement;
import net.minecraft.resources.ResourceLocation;

/**
 * Public codec-backed custom recipe requirement.
 *
 * @author howxu <dev@howxu.cn>
 */
public interface CustomRequirement extends MachineRequirement {
    ResourceLocation typeId();

    JsonElement payload();
}

package cn.howxu.mmcr.publicapi.recipe;
import cn.howxu.mmcr.publicapi.recipe.extension.SyncPayloadCodec;
import cn.howxu.mmcr.publicapi.recipe.extension.TypePresentation;
import cn.howxu.mmcr.publicapi.recipe.modifier.RecipeAdjustmentSpec;
import cn.howxu.mmcr.publicapi.recipe.requirement.RequirementSpec;
import com.mojang.serialization.MapCodec;
import java.util.List;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;
/** Open output payload registration, including real execution-requirement conversion.
 * codec excludes the reserved type field; copy snapshots mutable payloads.
 * @author howxu <dev@howxu.cn> */
public interface OutputExtension<O> {
    ResourceLocation id(); default String serializedId() { return id().toString(); } MapCodec<O> codec();
    O copy(O value); float chance(O value); default long amount(O value) { return 0; }
    O withChance(O value, float chance); O applyModifiers(O value, List<RecipeAdjustmentSpec> modifiers);
    RequirementSpec toRequirement(O value, List<String> tags); boolean matchesRequirement(RequirementSpec requirement);
    default Optional<O> fromRequirement(RequirementSpec requirement) { return Optional.empty(); }
    default SyncPayloadCodec<O> syncCodec() { return null; }
    default TypePresentation presentation() { return new TypePresentation("output." + id(), "output." + id() + ".description"); }
}

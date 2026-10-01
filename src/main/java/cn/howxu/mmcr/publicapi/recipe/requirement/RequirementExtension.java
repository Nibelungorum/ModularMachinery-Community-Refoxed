package cn.howxu.mmcr.publicapi.recipe.requirement;
import cn.howxu.mmcr.publicapi.recipe.IoDirection;
import cn.howxu.mmcr.publicapi.recipe.extension.SyncPayloadCodec;
import cn.howxu.mmcr.publicapi.recipe.extension.TypePresentation;
import com.mojang.serialization.MapCodec;
import java.util.List;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;
/** Open requirement payload registration. codec describes payload fields excluding the reserved type field.
 * copy must snapshot mutable payloads. A null syncCodec uses the core's bounded JSON codec.
 * @author howxu <dev@howxu.cn> */
public interface RequirementExtension<R> {
    ResourceLocation id(); MapCodec<R> codec(); IoDirection io(R value); List<String> tags(R value); R copy(R value);
    RequirementExecution<R> execution();
    /** Existing capability family IDs to plan against. Core matching still applies direction and tags.
     * The declaration is snapshotted at registration; the default selects this extension's own ID. */
    default Set<ResourceLocation> capabilityIds() { return Set.of(id()); }
    default SyncPayloadCodec<R> syncCodec() { return null; }
    default TypePresentation presentation() { return new TypePresentation("requirement." + id(), "requirement." + id() + ".description"); }
}

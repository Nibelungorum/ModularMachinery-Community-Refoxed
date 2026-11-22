package cn.howxu.mmcr.publicapi.registration;

import cn.howxu.mmcr.publicapi.structure.StructureDraft;
import cn.howxu.mmcr.publicapi.structure.StructureSpec;
import cn.howxu.mmcr.publicapi.structure.level.LevelTypeSpec;
import cn.howxu.mmcr.publicapi.structure.level.MachineLevelSpec;
import java.util.Map;
import java.util.function.Consumer;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.ApiStatus;

/** MMCR-provided shared Java/KJS structure registration window.
 * @author howxu <dev@howxu.cn>
 */
@ApiStatus.NonExtendable
public interface StructureRegistrar {
    void registerStructure(Identifier machineId, Consumer<StructureDraft> configuration);
    void registerStructure(StructureSpec structure);
    void registerLevelType(LevelTypeSpec type);
    void registerLevel(MachineLevelSpec level);
    Map<Identifier, StructureSpec> structures();
    Map<Identifier, LevelTypeSpec> levelTypes();
    Map<Identifier, MachineLevelSpec> levels();
}

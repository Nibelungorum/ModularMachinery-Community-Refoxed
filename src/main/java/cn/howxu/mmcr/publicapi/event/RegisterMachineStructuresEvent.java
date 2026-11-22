package cn.howxu.mmcr.publicapi.event;

import cn.howxu.mmcr.internal.api.facade.registration.RegistrationAdapters;
import cn.howxu.mmcr.publicapi.structure.StructureDraft;
import cn.howxu.mmcr.publicapi.structure.StructureSpec;
import cn.howxu.mmcr.publicapi.structure.level.LevelTypeSpec;
import cn.howxu.mmcr.publicapi.structure.level.MachineLevelSpec;
import cn.howxu.mmcr.publicapi.registration.StructureRegistrar;
import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import net.minecraft.resources.Identifier;
import net.neoforged.bus.api.Event;

/** Structure registration event posted on NeoForge.EVENT_BUS after controllers exist.
 * @author howxu <dev@howxu.cn>
 */
public final class RegisterMachineStructuresEvent extends Event {
    private final StructureRegistrar registrar;
    public RegisterMachineStructuresEvent(Collection<Identifier> machineIds) {
        this(RegistrationAdapters.structures(machineIds));
    }
    public RegisterMachineStructuresEvent(StructureRegistrar registrar) {
        this.registrar = Objects.requireNonNull(registrar, "registrar");
    }
    public StructureRegistrar registrar() { return registrar; }
    public void registerStructure(Identifier id, Consumer<StructureDraft> configuration) {
        registrar.registerStructure(id, configuration);
    }
    public void registerStructure(StructureSpec structure) { registrar.registerStructure(structure); }
    public void registerLevelType(LevelTypeSpec type) { registrar.registerLevelType(type); }
    public void registerLevel(MachineLevelSpec level) { registrar.registerLevel(level); }
    public Map<Identifier, StructureSpec> structures() { return registrar.structures(); }
    public Map<Identifier, LevelTypeSpec> levelTypes() { return registrar.levelTypes(); }
    public Map<Identifier, MachineLevelSpec> levels() { return registrar.levels(); }
}

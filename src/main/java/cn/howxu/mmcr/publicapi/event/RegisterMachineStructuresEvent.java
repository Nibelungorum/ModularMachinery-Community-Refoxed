package cn.howxu.mmcr.publicapi.event;

import cn.howxu.mmcr.internal.api.facade.registration.RegistrationAdapters;
import cn.howxu.mmcr.publicapi.structure.StructureDraft;
import cn.howxu.mmcr.publicapi.structure.StructureSpec;
import cn.howxu.mmcr.publicapi.structure.level.LevelTypeSpec;
import cn.howxu.mmcr.publicapi.structure.level.MachineLevelSpec;
import cn.howxu.mmcr.publicapi.recipe.modifier.ModifierBundle;
import cn.howxu.mmcr.publicapi.registration.StructureRegistrar;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.Event;
import net.neoforged.fml.event.IModBusEvent;

/** Structure registration event posted on NeoForge.EVENT_BUS after controllers exist.
 * @author howxu <dev@howxu.cn>
 */
public final class RegisterMachineStructuresEvent extends Event implements IModBusEvent {
    private final StructureRegistrar registrar;
    public RegisterMachineStructuresEvent(Collection<ResourceLocation> machineIds) {
        this(RegistrationAdapters.structures(machineIds));
    }
    public RegisterMachineStructuresEvent(StructureRegistrar registrar) {
        this.registrar = Objects.requireNonNull(registrar, "registrar");
    }
    public StructureRegistrar registrar() { return registrar; }
    public void registerStructure(ResourceLocation id, Consumer<StructureDraft> configuration) {
        registrar.registerStructure(id, configuration);
    }
    public void registerStructure(StructureSpec structure) { registrar.registerStructure(structure); }
    public void registerLevelType(LevelTypeSpec type) { registrar.registerLevelType(type); }
    public void registerLevel(MachineLevelSpec level) { registrar.registerLevel(level); }
    public void registerModifier(ResourceLocation id, ModifierBundle modifier) { registrar.registerModifier(id, modifier); }
    public void registerModifierItem(ItemStack stack, ResourceLocation id) { registrar.registerModifierItem(stack, id); }
    public Map<ResourceLocation, StructureSpec> structures() { return registrar.structures(); }
    public Map<ResourceLocation, LevelTypeSpec> levelTypes() { return registrar.levelTypes(); }
    public Map<ResourceLocation, MachineLevelSpec> levels() { return registrar.levels(); }
    public Map<ResourceLocation, ModifierBundle> modifiers() { return registrar.modifiers(); }
    public Map<ResourceLocation, List<ItemStack>> modifierItems() { return registrar.modifierItems(); }
}

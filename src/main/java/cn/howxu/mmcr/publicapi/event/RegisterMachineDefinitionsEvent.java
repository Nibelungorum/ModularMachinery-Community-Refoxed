package cn.howxu.mmcr.publicapi.event;

import java.util.List;
import net.minecraft.world.item.ItemStack;
import cn.howxu.mmcr.publicapi.recipe.modifier.ModifierBundle;
import cn.howxu.mmcr.publicapi.recipe.modifier.BlockStackingMode;
import cn.howxu.mmcr.internal.api.facade.registration.RegistrationAdapters;
import cn.howxu.mmcr.publicapi.machine.MachineDraft;
import cn.howxu.mmcr.publicapi.machine.MachineSpec;
import cn.howxu.mmcr.publicapi.registration.MachineRegistrar;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.Event;

/** Startup definition event posted on NeoForge.EVENT_BUS, before controller registration.
 * @author howxu <dev@howxu.cn>
 */
public final class RegisterMachineDefinitionsEvent extends Event {
    private final MachineRegistrar registrar;
    public RegisterMachineDefinitionsEvent() { this(RegistrationAdapters.definitions()); }
    public RegisterMachineDefinitionsEvent(MachineRegistrar registrar) {
        this.registrar = Objects.requireNonNull(registrar, "registrar");
    }
    public MachineRegistrar registrar() { return registrar; }
    public void registerMachine(ResourceLocation id, Consumer<MachineDraft> configuration) {
        registrar.registerMachine(id, configuration);
    }
    public void registerMachine(MachineSpec definition) { registrar.registerMachine(definition); }
    public Map<ResourceLocation, MachineSpec> definitions() { return registrar.definitions(); }
    public void registerModifier(ResourceLocation id, ModifierBundle modifier) { registrar.registerModifier(id, modifier); }
    public void registerModifier(ResourceLocation id, ModifierBundle modifier, boolean stackable) {
        registrar.registerModifier(id, modifier, stackable);
    }
    public void registerModifier(ResourceLocation id, ModifierBundle modifier, boolean stackable, BlockStackingMode mode) {
        registrar.registerModifier(id, modifier, stackable, mode);
    }
    public void registerModifierItem(ResourceLocation itemId, ResourceLocation id) { registrar.registerModifierItem(itemId, id); }
    /** Declares a positive per-machine cap on effective upgrade items, not on inventory or structure blocks. */
    public void limitModifierItems(ResourceLocation modifierId, int maxCount) { registrar.limitModifierItems(modifierId, maxCount); }
    public Map<ResourceLocation, ModifierBundle> modifiers() { return registrar.modifiers(); }
    public Map<ResourceLocation, List<ItemStack>> modifierItems() { return registrar.modifierItems(); }
    public Map<ResourceLocation, ResourceLocation> modifierItemIds() { return registrar.modifierItemIds(); }
}

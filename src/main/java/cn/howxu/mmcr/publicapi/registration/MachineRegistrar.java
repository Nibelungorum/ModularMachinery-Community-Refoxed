package cn.howxu.mmcr.publicapi.registration;

import java.util.List;
import net.minecraft.world.item.ItemStack;
import cn.howxu.mmcr.publicapi.recipe.modifier.ModifierBundle;
import cn.howxu.mmcr.publicapi.recipe.modifier.BlockStackingMode;
import cn.howxu.mmcr.publicapi.machine.MachineDraft;
import cn.howxu.mmcr.publicapi.machine.MachineSpec;
import java.util.Map;
import java.util.function.Consumer;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.ApiStatus;

/** MMCR-provided registration window; returned maps are read-only snapshots.
 * @author howxu <dev@howxu.cn>
 */
@ApiStatus.NonExtendable
public interface MachineRegistrar {
    void registerMachine(Identifier id, Consumer<MachineDraft> configuration);
    void registerMachine(MachineSpec definition);
    Map<Identifier, MachineSpec> definitions();
    /** Legacy: one structure contribution plus the stacked Upgrade Bus contribution. */
    void registerModifier(Identifier id, ModifierBundle modifier);
    /** When enabled, blocks use additive stacking; otherwise each property takes one source's better result. */
    default void registerModifier(Identifier id, ModifierBundle modifier, boolean stackable) {
        registerModifier(id, modifier, stackable, BlockStackingMode.ADD);
    }
    /** Disabling stacking limits this ID across both structure blocks and upgrade items. */
    default void registerModifier(Identifier id, ModifierBundle modifier, boolean stackable, BlockStackingMode blockMode) {
        throw new UnsupportedOperationException("This registrar does not support explicit modifier stacking");
    }
    void registerModifierItem(Identifier itemId, Identifier modifierId);
    /** Caps the combined effective item count for this modifier across one machine's upgrade buses. */
    default void limitModifierItems(Identifier modifierId, int maxCount) {
        throw new UnsupportedOperationException("This registrar does not support modifier item limits");
    }
    Map<Identifier, ModifierBundle> modifiers();
    Map<Identifier, List<ItemStack>> modifierItems();
    Map<Identifier, Identifier> modifierItemIds();
}

package cn.howxu.mmcr.publicapi.event;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.machine.definition.ModifierDefinition;
import cn.howxu.mmcr.api.machine.level.LevelType;
import cn.howxu.mmcr.api.registration.StructureRegistration;
import cn.howxu.mmcr.internal.api.facade.registration.RegistrationAdapters;
import cn.howxu.mmcr.publicapi.recipe.modifier.Modifiers;
import cn.howxu.mmcr.publicapi.registration.RegistrationException;
import cn.howxu.mmcr.publicapi.structure.BlockConditions;
import cn.howxu.mmcr.publicapi.structure.level.Levels;
import cn.howxu.mmcr.test.TestBootstrap;
import java.util.Set;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Public declarations and core/KJS declarations use the same authoritative structure collector.
 * @author howxu <dev@howxu.cn>
 */
class StructureRegistrationCollectorTest {
    @BeforeAll
    static void bootstrapMinecraft() throws Exception { TestBootstrap.bootstrap(); }
    @BeforeEach
    void reset() { RegistrationAdapters.resetCollector(); }
    @AfterEach
    void cleanup() { RegistrationAdapters.resetCollector(); }

    @Test
    void prepare_preserves_early_core_declarations_and_late_core_writes_share_freeze() {
        var machine = MMCR.id("shared_collector_machine");
        var type = MMCR.id("shared_collector_type");
        var level = MMCR.id("shared_collector_level");
        var modifier = MMCR.id("shared_collector_modifier");
        StructureRegistration.current().registerLevelType(new LevelType(type, Component.translatable("test.mmcr.coil")));
        var event = RegistrationAdapters.prepare(Set.of(machine));
        event.registerLevel(Levels.level(level, type, 1, BlockConditions.block(Blocks.FURNACE),
                ItemStack.EMPTY, Modifiers.bundle()));
        // Models KJS writing its core collector after Java event subscribers have returned.
        StructureRegistration.current().registerModifier(modifier, ModifierDefinition.EMPTY);
        event.registerStructure(machine, draft -> draft.fullStructure(stage -> stage
                .pattern(pattern -> pattern.layer("F").where('F', BlockConditions.block(Blocks.FURNACE)).controller('F'))
                .requirements(requirements -> requirements.levelSlot('F', type).modifier('F', modifier))));
        var snapshot = RegistrationAdapters.freeze(event);
        assertThat(snapshot.levelTypes()).containsKey(type);
        assertThat(snapshot.levels()).containsKey(level);
        assertThat(snapshot.modifiers()).containsKey(modifier);
        assertThat(RegistrationAdapters.freeze(RegistrationAdapters.current())).isSameAs(snapshot);
        assertThat(event.levels().get(level).id()).isEqualTo(level);
        assertThatThrownBy(() -> StructureRegistration.current().registerModifier(MMCR.id("late"), ModifierDefinition.EMPTY))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> event.registerModifier(MMCR.id("late"), Modifiers.bundle()))
                .isInstanceOf(RegistrationException.class);
    }

    @Test
    void unresolved_modifier_binding_rejects_freeze_without_closing_collector() {
        var modifier = MMCR.id("unresolved_public_modifier");
        var event = new RegisterMachineStructuresEvent(Set.of());
        event.registerModifierItem(new ItemStack(Items.EMERALD), modifier);
        assertThatThrownBy(() -> RegistrationAdapters.freeze(event)).isInstanceOf(RegistrationException.class)
                .hasMessageContaining(modifier.toString());
        event.registerModifier(modifier, Modifiers.bundle());
        var snapshot = RegistrationAdapters.freeze(event);
        assertThat(snapshot.modifierItems()).containsKey(modifier);
        var copy = event.modifierItems().get(modifier).getFirst();
        copy.setCount(5);
        assertThat(event.modifierItems().get(modifier).getFirst().getCount()).isEqualTo(1);
    }
}

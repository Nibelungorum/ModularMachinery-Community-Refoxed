package cn.howxu.mmcr.publicapi.event;

import cn.howxu.mmcr.api.registration.ApiRegistrationException;
import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.machine.level.LevelType;
import cn.howxu.mmcr.api.machine.modifier.ModifierStacking;
import cn.howxu.mmcr.publicapi.recipe.modifier.BlockStackingMode;
import cn.howxu.mmcr.api.registration.StructureRegistration;
import cn.howxu.mmcr.internal.api.facade.registration.RegistrationAdapters;
import cn.howxu.mmcr.publicapi.recipe.modifier.Modifiers;
import cn.howxu.mmcr.publicapi.registration.RegistrationException;
import cn.howxu.mmcr.publicapi.structure.BlockConditions;
import cn.howxu.mmcr.publicapi.structure.level.Levels;
import cn.howxu.mmcr.test.TestBootstrap;
import java.util.Set;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
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
        var event = RegistrationAdapters.prepare(Set.of(machine));
        event.registerLevel(Levels.level(level, type, 1, BlockConditions.state("minecraft:furnace"),
                Modifiers.bundle()));
        assertThat(event.levels()).containsKey(level);
        assertThatThrownBy(() -> event.levels().clear()).isInstanceOf(UnsupportedOperationException.class);
        event.registerStructure(machine, draft -> draft.fullStructure(stage -> stage
                .pattern(pattern -> pattern.layer("F").where('F', BlockConditions.block(Blocks.FURNACE)).controller('F'))
                .requirements(requirements -> requirements.levelSlot('F', type))));
        // Models KJS writing dependencies after Java event subscribers have returned.
        StructureRegistration.current().registerLevelType(new LevelType(type, Component.translatable("test.mmcr.coil")));
        var snapshot = RegistrationAdapters.freeze(event);
        assertThat(snapshot.levels().get(level).statePredicate().matches(Blocks.FURNACE.defaultBlockState())).isTrue();
        assertThat(snapshot.levelTypes()).containsKey(type);
        assertThat(snapshot.levels()).containsKey(level);
        assertThat(RegistrationAdapters.freeze(RegistrationAdapters.current())).isSameAs(snapshot);
        assertThat(event.levels().get(level).id()).isEqualTo(level);
        assertThatThrownBy(() -> StructureRegistration.current().registerLevelType(new LevelType(type, Component.literal("late"))))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> event.registerLevelType(Levels.type(type, Component.literal("late"))))
                .isInstanceOf(RegistrationException.class);
    }

    @Test
    void typed_overloads_attach_policy_to_the_id_not_the_shared_bundle() {
        var event = new RegisterMachineDefinitionsEvent();
        var legacy = MMCR.id("legacy");
        var unique = MMCR.id("unique");
        var linear = MMCR.id("linear");
        var product = MMCR.id("product");
        var shared = Modifiers.bundle();
        event.registerModifier(legacy, shared);
        event.registerModifier(unique, shared, false);
        event.registerModifier(linear, shared, true);
        event.registerModifier(product, shared, true, BlockStackingMode.MULTIPLY);
        RegistrationAdapters.freeze(event);
        var snapshot = RegistrationAdapters.core(event).modifierSnapshot();
        assertThat(snapshot.stacking()).doesNotContainKey(legacy).containsEntry(unique, ModifierStacking.UNIQUE)
                .containsEntry(linear, ModifierStacking.ADD).containsEntry(product, ModifierStacking.MULTIPLY);
        assertThat(snapshot.modifiers().get(legacy)).isSameAs(snapshot.modifiers().get(unique));
    }

    @Test
    void unresolved_level_type_is_wrapped_at_freeze_and_can_be_completed_later() {
        var event = new RegisterMachineStructuresEvent(Set.of());
        var type = MMCR.id("public_forward_type");
        var level = MMCR.id("public_forward_level");
        event.registerLevel(Levels.level(level, type, 0, BlockConditions.state("minecraft:copper_block"),
                Modifiers.bundle()));
        assertThatThrownBy(() -> RegistrationAdapters.freeze(event)).isInstanceOf(RegistrationException.class)
                .hasMessageContaining(level.toString()).hasMessageContaining(type.toString());
        assertThat(event.levels()).containsKey(level);
        event.registerLevelType(Levels.type(type, Component.translatable("test.mmcr.coil")));
        assertThat(RegistrationAdapters.freeze(event).levels().get(level).typeId()).isEqualTo(type);
    }

    @Test
    void item_limit_allows_a_forward_reference_but_not_an_invalid_or_duplicate_declaration() {
        var speed = MMCR.id("limited_speed");
        var event = new RegisterMachineDefinitionsEvent();
        assertThatThrownBy(() -> event.limitModifierItems(speed, 0))
                .isInstanceOf(RegistrationException.class).hasMessageContaining("positive");
        event.limitModifierItems(speed, 4);
        assertThatThrownBy(() -> event.limitModifierItems(speed, 8))
                .isInstanceOf(RegistrationException.class).hasMessageContaining("Duplicate")
                .hasMessageContaining(speed.toString());
        assertThatThrownBy(() -> RegistrationAdapters.core(event).modifierSnapshot())
                .isInstanceOf(ApiRegistrationException.class).hasMessageContaining("unknown machine modifier")
                .hasMessageContaining(speed.toString());

        event.registerModifier(speed, Modifiers.bundle());
        RegistrationAdapters.freeze(event);
        assertThat(RegistrationAdapters.core(event).modifierSnapshot().modifierItemLimits()).containsEntry(speed, 4);
        assertThatThrownBy(() -> event.limitModifierItems(speed, 2))
                .isInstanceOf(RegistrationException.class).hasMessageContaining("frozen");
    }

    @Test
    void unresolved_modifier_binding_is_rejected_at_link_time() {
        var modifier = MMCR.id("unresolved_public_modifier");
        var event = new RegisterMachineDefinitionsEvent();
        var itemId = ResourceLocation.parse("minecraft:emerald");
        event.registerModifierItem(itemId, modifier);
        assertThatThrownBy(() -> RegistrationAdapters.core(event).modifierSnapshot()).isInstanceOf(ApiRegistrationException.class)
                .hasMessageContaining(modifier.toString());
        event.registerModifier(modifier, Modifiers.bundle());
        RegistrationAdapters.freeze(event);
        var snapshot = RegistrationAdapters.core(event).modifierSnapshot();
        assertThat(snapshot.modifierItemIds()).containsEntry(itemId, modifier);
        assertThat(event.modifierItemIds()).containsEntry(itemId, modifier);
        assertThatThrownBy(() -> event.modifierItemIds().clear()).isInstanceOf(UnsupportedOperationException.class);
    }
}

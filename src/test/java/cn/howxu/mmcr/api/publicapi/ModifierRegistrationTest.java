package cn.howxu.mmcr.api.publicapi;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.publicapi.event.MMCRMachineStructuresEvent;
import cn.howxu.mmcr.api.publicapi.machine.BlockPredicate;
import cn.howxu.mmcr.api.publicapi.machine.ModifierDefinition;
import cn.howxu.mmcr.api.publicapi.machine.ModifierUse;
import cn.howxu.mmcr.api.recipe.modifier.ModifierItemKey;
import cn.howxu.mmcr.api.recipe.modifier.ModifierRegistry;
import cn.howxu.mmcr.test.TestBootstrap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Verifies canonical modifier item registration and snapshot isolation.
 * @author howxu <dev@howxu.cn>
 */
class ModifierRegistrationTest {
    @BeforeAll
    static void bootstrapMinecraft() throws Exception {
        TestBootstrap.bootstrap();
    }

    @BeforeEach
    void resetRegistry() {
        ModifierRegistry.installSnapshot(Map.of());
    }

    @AfterEach
    void cleanupRegistry() {
        ModifierRegistry.installSnapshot(Map.of());
    }

    @Test
    void item_binding_ignores_count_and_distinguishes_data_components() {
        Identifier modifierId = id("component_modifier");
        ItemStack namedStack = new ItemStack(Items.IRON_INGOT, 1);
        namedStack.set(DataComponents.MAX_STACK_SIZE, 32);
        MMCRMachineStructuresEvent event = new MMCRMachineStructuresEvent(List.of());
        event.registerModifier(modifierId, new ModifierDefinition(List.of()));
        event.registerModifierItem(namedStack, modifierId);

        MMCRMachineStructuresEvent.Snapshot snapshot = event.freeze();
        ModifierRegistry.installSnapshot(snapshot.modifiers(), snapshot.modifierItems());

        assertThat(snapshot.modifiers()).containsEntry(modifierId, new ModifierDefinition(List.of()));
        assertThat(snapshot.modifierItems().get(modifierId))
                .usingElementComparator((actual, expected) -> ItemStack.matches(actual, expected) ? 0 : 1)
                .containsExactly(namedStack.copyWithCount(1));
        assertThat(ModifierRegistry.modifierFor(namedStack.copyWithCount(32))).isEqualTo(modifierId);

        ItemStack differentComponent = namedStack.copyWithCount(1);
        differentComponent.set(DataComponents.MAX_STACK_SIZE, 33);
        assertThat(ModifierRegistry.modifierFor(differentComponent)).isNull();
    }

    @Test
    void duplicate_item_binding_is_rejected_after_count_normalization() {
        Identifier modifierId = id("duplicate_modifier");
        ItemStack stack = new ItemStack(Items.GOLD_INGOT);
        MMCRMachineStructuresEvent event = new MMCRMachineStructuresEvent(List.of());
        event.registerModifier(modifierId, new ModifierDefinition(List.of()));
        event.registerModifierItem(stack, modifierId);

        assertThatThrownBy(() -> event.registerModifierItem(stack.copyWithCount(64), id("other_modifier")))
                .isInstanceOf(ApiRegistrationException.class)
                .hasMessageContaining("Duplicate machine modifier item");
    }

    @Test
    void unknown_modifier_item_binding_is_rejected_when_frozen() {
        MMCRMachineStructuresEvent event = new MMCRMachineStructuresEvent(List.of());
        event.registerModifierItem(new ItemStack(Items.COPPER_INGOT), id("unknown_modifier"));

        assertThatThrownBy(event::freeze)
                .isInstanceOf(ApiRegistrationException.class)
                .hasMessageContaining("unknown_modifier");
    }

    @Test
    void modifier_snapshot_copies_item_stacks_and_is_immutable() {
        Identifier modifierId = id("isolated_modifier");
        ItemStack source = new ItemStack(Items.DIAMOND, 1);
        source.set(DataComponents.MAX_STACK_SIZE, 32);
        MMCRMachineStructuresEvent event = new MMCRMachineStructuresEvent(List.of());
        event.registerModifier(modifierId, new ModifierDefinition(List.of()));
        event.registerModifierItem(source, modifierId);

        MMCRMachineStructuresEvent.Snapshot snapshot = event.freeze();
        source.set(DataComponents.MAX_STACK_SIZE, 64);
        assertThat(snapshot.modifierItems().get(modifierId).getFirst().get(DataComponents.MAX_STACK_SIZE))
                .isEqualTo(32);

        ItemStack exposed = snapshot.modifierItems().get(modifierId).getFirst();
        exposed.set(DataComponents.MAX_STACK_SIZE, 48);
        assertThat(snapshot.modifierItems().get(modifierId).getFirst().get(DataComponents.MAX_STACK_SIZE))
                .isEqualTo(32);
        assertThatThrownBy(() -> snapshot.modifierItems().clear())
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> snapshot.modifierItems().get(modifierId).clear())
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void modifier_item_key_copies_mutable_component_state() {
        ItemStack source = new ItemStack(Items.DIAMOND, 1);
        source.set(DataComponents.MAX_STACK_SIZE, 32);

        ModifierItemKey key = ModifierItemKey.of(source);
        source.set(DataComponents.MAX_STACK_SIZE, 64);

        assertThat(key.components().get(DataComponents.MAX_STACK_SIZE)).isEqualTo(32);
    }

    @Test
    void modifier_use_requires_both_identifier_and_replacement() {
        Identifier modifierId = id("use_modifier");
        ModifierUse use = ModifierUse.of(modifierId, BlockPredicate.block(Blocks.IRON_BLOCK));

        assertThat(use.modifierId()).isEqualTo(modifierId);
        assertThat(use.replacement()).isEqualTo(BlockPredicate.block(Blocks.IRON_BLOCK));
        assertThatThrownBy(() -> ModifierUse.of(null, use.replacement()))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> ModifierUse.of(modifierId, null))
                .isInstanceOf(NullPointerException.class);
    }

    private static Identifier id(String path) {
        return MMCR.id(path);
    }
}

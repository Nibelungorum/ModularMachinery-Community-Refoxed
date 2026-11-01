package cn.howxu.mmcr.compat.kubejs;

import cn.howxu.mmcr.api.machine.definition.ModifierDefinition;
import cn.howxu.mmcr.api.machine.definition.BlockPredicate;
import cn.howxu.mmcr.api.machine.level.MachineLevelRegistry;
import cn.howxu.mmcr.api.recipe.modifier.ModifierRegistry;
import cn.howxu.mmcr.api.registration.StructureRegistration;
import cn.howxu.mmcr.internal.registration.ContentRegistrationCoordinator;
import cn.howxu.mmcr.internal.registration.MachineRecipeConverter;
import cn.howxu.mmcr.api.recipe.MachineRecipeBuilder;
import cn.howxu.mmcr.test.TestBootstrap;
import cn.howxu.mmcr.internal.api.facade.registration.RegistrationAdapters;
import cn.howxu.mmcr.publicapi.event.RegisterMachineStructuresEvent;
import cn.howxu.mmcr.publicapi.recipe.modifier.Modifiers;
import cn.howxu.mmcr.publicapi.structure.BlockConditions;
import cn.howxu.mmcr.publicapi.structure.level.Levels;
import net.minecraft.ResourceLocationException;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.Map;
import java.util.Set;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Tests declaration capture and the component-safe item binding boundary.
 * @author howxu <dev@howxu.cn>
 */
class StartupRegistryReferenceTest {
    private static final ResourceLocation MODIFIER = ResourceLocation.parse("test:startup_speed");

    @BeforeAll
    static void bootstrapMinecraft() throws Exception {
        TestBootstrap.bootstrap();
    }

    @BeforeEach
    void begin() {
        ContentRegistrationCoordinator.resetForTesting();
        ModifierRegistry.installSnapshot(Map.of());
    }

    @AfterEach
    void cleanup() {
        ContentRegistrationCoordinator.resetForTesting();
        ModifierRegistry.installSnapshot(Map.of());
    }

    @Test
    void item_ids_survive_prepare_and_are_published_only_at_the_item_binding_boundary() {
        var event = new MMCRStartupEventJS();
        event.registerModifier(MODIFIER.toString(), ModifierDefinition.EMPTY);
        event.registerModifierItem("minecraft:diamond", MODIFIER.toString());
        ContentRegistrationCoordinator.beginStartup();
        ContentRegistrationCoordinator.collectStructures(StructureRegistration.prepare(Set.of()));
        ContentRegistrationCoordinator.commitStructures();

        assertThat(ModifierRegistry.get(MODIFIER)).isEqualTo(ModifierDefinition.EMPTY);
        assertThat(ModifierRegistry.modifierItems()).isEmpty();

        ContentRegistrationCoordinator.commitModifierItemsAfterComponentsBound();
        assertThat(ModifierRegistry.modifierFor(new ItemStack(Items.DIAMOND))).isEqualTo(MODIFIER);
    }

    @Test
    void missing_item_is_reported_at_resolution_without_replacing_published_bindings() {
        var event = new MMCRStartupEventJS();
        event.registerModifier(MODIFIER.toString(), ModifierDefinition.EMPTY);
        event.registerModifierItem("missing_mod:upgrade", MODIFIER.toString());
        ContentRegistrationCoordinator.beginStartup();
        ContentRegistrationCoordinator.collectStructures(StructureRegistration.current());
        ContentRegistrationCoordinator.commitStructures();
        ModifierRegistry.installSnapshot(Map.of(MODIFIER, ModifierDefinition.EMPTY),
                Map.of(MODIFIER, List.of(new ItemStack(Items.DIAMOND))));

        assertThatThrownBy(ContentRegistrationCoordinator::commitModifierItemsAfterComponentsBound)
                .hasMessageContaining("missing_mod:upgrade").hasMessageContaining(MODIFIER.toString());
        assertThat(ModifierRegistry.modifierFor(new ItemStack(Items.DIAMOND))).isEqualTo(MODIFIER);
    }

    @Test
    void duplicate_item_ids_and_unknown_modifiers_are_rejected() {
        var event = new MMCRStartupEventJS();
        event.registerModifierItem("minecraft:diamond", MODIFIER.toString());
        assertThatThrownBy(() -> event.registerModifierItem("minecraft:diamond", "test:another_modifier"))
                .hasMessageContaining("Duplicate").hasMessageContaining("minecraft:diamond");
        assertThatThrownBy(() -> StructureRegistration.current().freeze())
                .hasMessageContaining("unknown machine modifier").hasMessageContaining(MODIFIER.toString());
    }

    @Test
    void pending_and_java_stack_bindings_cannot_claim_the_same_item() {
        var collector = StructureRegistration.current();
        var javaModifier = ResourceLocation.parse("test:java_stack_modifier");
        collector.registerModifier(MODIFIER, ModifierDefinition.EMPTY);
        collector.registerModifier(javaModifier, ModifierDefinition.EMPTY);
        collector.registerModifierItem(new ItemStack(Items.DIAMOND), javaModifier);
        collector.registerModifierItem(ResourceLocation.parse("minecraft:diamond"), MODIFIER);
        ContentRegistrationCoordinator.beginStartup();
        ContentRegistrationCoordinator.collectStructures(collector);
        ContentRegistrationCoordinator.commitStructures();

        assertThat(ModifierRegistry.modifierFor(new ItemStack(Items.DIAMOND))).isNull();
        assertThatThrownBy(ContentRegistrationCoordinator::commitModifierItemsAfterComponentsBound)
                .hasMessageContaining("Duplicate machine modifier item binding")
                .hasMessageContaining("minecraft:diamond")
                .hasMessageContaining(javaModifier.toString()).hasMessageContaining(MODIFIER.toString());
        assertThat(ModifierRegistry.modifierFor(new ItemStack(Items.DIAMOND))).isNull();
    }

    @Test
    void missing_item_cannot_publish_the_valid_java_stack_subset() {
        var collector = StructureRegistration.current();
        collector.registerModifier(MODIFIER, ModifierDefinition.EMPTY);
        collector.registerModifierItem(new ItemStack(Items.DIAMOND), MODIFIER);
        collector.registerModifierItem(ResourceLocation.parse("missing_mod:upgrade"), MODIFIER);
        ContentRegistrationCoordinator.beginStartup();
        ContentRegistrationCoordinator.collectStructures(collector);
        ContentRegistrationCoordinator.commitStructures();

        assertThat(ModifierRegistry.modifierFor(new ItemStack(Items.DIAMOND))).isNull();
        assertThatThrownBy(ContentRegistrationCoordinator::commitModifierItemsAfterComponentsBound)
                .hasMessageContaining("missing_mod:upgrade");
        assertThat(ModifierRegistry.modifierItems()).isEmpty();
    }

    @Test
    void frozen_item_declarations_do_not_follow_later_collectors() {
        var event = new MMCRStartupEventJS();
        event.registerModifier(MODIFIER.toString(), ModifierDefinition.EMPTY);
        event.registerModifierItem("minecraft:diamond", MODIFIER.toString());
        var snapshot = StructureRegistration.current().freeze();
        StructureRegistration.prepare(Set.of()).registerModifierItem(ResourceLocation.parse("minecraft:emerald"), MODIFIER);

        assertThat(snapshot.resolveModifierItems().get(MODIFIER)).singleElement()
                .satisfies(stack -> assertThat(stack.getItem()).isSameAs(Items.DIAMOND));
    }

    @Test
    void startup_level_slot_is_validated_when_the_registered_structure_is_frozen() {
        var machineId = ResourceLocation.parse("test:forward_structure");
        var typeId = ResourceLocation.parse("test:startup_coil");
        var collector = StructureRegistration.prepare(Set.of(machineId));
        var event = new MMCRStartupEventJS();
        var slot = event.levelSlot(typeId);
        assertThat(event.levelSlot(typeId.toString())).isEqualTo(slot);
        collector.registerStructure(machineId, builder -> builder.fullStructure(stage -> stage
                .pattern(pattern -> pattern.layer("F").where('F', BlockPredicate.block(Blocks.FURNACE)).controller('F'))
                .requirements(requirements -> requirements.levelSlot('F', slot.typeId()))));

        assertThatThrownBy(collector::freeze).hasMessageContaining(machineId.toString())
                .hasMessageContaining(typeId.toString());
        event.createLevelType(typeId).register();
        assertThat(collector.freeze().levelTypes()).containsKey(typeId);
    }

    @Test
    void reference_factories_do_not_need_a_published_registry_or_track_unused_references() {
        var event = new MMCRStartupEventJS();
        var api = event.getAPI();
        var typeId = ResourceLocation.parse("test:future_type");
        var levelId = ResourceLocation.parse("test:future_level");
        assertThat(MachineLevelRegistry.getType(typeId)).isNull();
        assertThat(api.levelSlot(typeId.toString())).isEqualTo(event.levelSlot(typeId));
        assertThat(api.levelSlot(typeId)).isEqualTo(event.levelSlot(typeId.toString()));
        var requirement = api.levelRequirement(typeId.toString(), levelId.toString());
        assertThat(requirement).isEqualTo(api.levelRequirement(typeId, levelId));
        assertThat(requirement.typeId()).isEqualTo(typeId);
        assertThat(requirement.levelId()).isEqualTo(levelId);
        assertThatThrownBy(() -> event.levelSlot((ResourceLocation) null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> api.levelSlot((ResourceLocation) null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> api.levelRequirement(typeId, null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> api.levelSlot("INVALID TYPE")).isInstanceOf(ResourceLocationException.class);
        assertThatThrownBy(() -> api.levelRequirement(typeId.toString(), "INVALID LEVEL"))
                .isInstanceOf(ResourceLocationException.class);
        assertThat(StructureRegistration.current().freeze().levelTypes()).isEmpty();

        var recipe = new MachineRecipeBuilderJS("test:missing_level_recipe");
        recipe.recipePoolId = ResourceLocation.parse("test:pool");
        recipe.addRequirement(requirement);
        assertThatThrownBy(recipe::createObject).hasMessageContaining(levelId.toString());
    }

    @ParameterizedTest
    @CsvSource({"true,true", "true,false", "false,true", "false,false"})
    void level_and_modifier_declarations_accept_either_dependency_order(boolean typeFirst, boolean modifierFirst) {
        var event = new MMCRStartupEventJS();
        var typeId = ResourceLocation.parse("test:order_type");
        var levelId = ResourceLocation.parse("test:order_level");
        if (typeFirst) event.createLevelType(typeId).register();
        event.createLevel(levelId).type(typeId).state("minecraft:copper_block").register();
        if (!typeFirst) event.createLevelType(typeId).register();
        if (modifierFirst) event.registerModifier(MODIFIER, ModifierDefinition.EMPTY);
        event.registerModifierItem("minecraft:diamond", MODIFIER.toString());
        if (!modifierFirst) event.registerModifier(MODIFIER, ModifierDefinition.EMPTY);

        var snapshot = StructureRegistration.current().freeze();
        assertThat(snapshot.levels().get(levelId).typeId()).isEqualTo(typeId);
        assertThat(snapshot.levels().get(levelId).statePredicate().matches(Blocks.COPPER_BLOCK.defaultBlockState())).isTrue();
        assertThat(snapshot.modifiers()).containsEntry(MODIFIER, ModifierDefinition.EMPTY);
        assertThat(snapshot.resolveModifierItems().get(MODIFIER)).singleElement()
                .satisfies(stack -> assertThat(stack.getItem()).isSameAs(Items.DIAMOND));
    }

    @Test
    void modifier_references_link_after_declaration_but_missing_definitions_still_fail() {
        var machineId = ResourceLocation.parse("test:modifier_structure");
        var recipeId = ResourceLocation.parse("test:modifier_recipe");
        var collector = StructureRegistration.prepare(Set.of(machineId));
        var api = new KubeJSApi();
        var use = api.modifierUse(MODIFIER, api.block("minecraft:diamond_block"));
        var recipe = MachineRecipeBuilder.recipe(recipeId).recipePool(machineId).modifier(MODIFIER).build();
        collector.registerStructure(machineId, builder -> builder.fullStructure(stage -> stage
                .pattern(pattern -> pattern.layer("F").where('F', BlockPredicate.block(Blocks.FURNACE)).controller('F'))
                .modifier('F', use)));
        assertThatThrownBy(collector::freeze).hasMessageContaining(machineId.toString())
                .hasMessageContaining(MODIFIER.toString());
        var empty = new StructureRegistration(Set.of()).freeze();
        assertThatThrownBy(() -> MachineRecipeConverter.toRecipe(recipe, empty))
                .hasMessageContaining(recipeId.toString()).hasMessageContaining(MODIFIER.toString());

        collector.registerModifier(MODIFIER, ModifierDefinition.EMPTY);
        var snapshot = collector.freeze();
        assertThat(snapshot.structures()).containsKey(machineId);
        assertThat(MachineRecipeConverter.toRecipe(recipe, snapshot).id()).isEqualTo(recipeId);
    }

    @Test
    void public_level_factory_and_registration_do_not_resolve_ids_until_freeze() {
        var event = new RegisterMachineStructuresEvent(Set.of());
        var typeId = ResourceLocation.parse("test:public_pending_type");
        var levelId = ResourceLocation.parse("test:public_pending_level");
        event.registerLevelType(Levels.type(typeId, Component.translatable("level.test.public_pending")));
        event.registerLevel(Levels.level(levelId, typeId, 0,
                BlockConditions.state("missing_mod:coil[axis=x]"), Modifiers.bundle()));

        assertThat(event.levels()).containsKey(levelId);
        assertThat(event.levels().get(levelId).id()).isEqualTo(levelId);
        assertThatThrownBy(() -> event.levels().clear()).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> RegistrationAdapters.freeze(event))
                .hasMessageContaining(levelId.toString()).hasMessageContaining("missing_mod:coil");
    }

    @Test
    void public_modifier_item_ids_use_the_same_deferred_snapshot() {
        var event = new RegisterMachineStructuresEvent(Set.of());
        var itemId = ResourceLocation.parse("missing_mod:public_upgrade");
        event.registerModifier(MODIFIER, Modifiers.bundle());
        event.registerModifierItem(itemId, MODIFIER);

        var snapshot = RegistrationAdapters.freeze(event);
        assertThat(event.modifierItemIds()).containsEntry(itemId, MODIFIER);
        assertThat(snapshot.modifierItems()).isEmpty();
        assertThatThrownBy(snapshot::resolveModifierItems)
                .hasMessageContaining(itemId.toString()).hasMessageContaining(MODIFIER.toString());
    }
}

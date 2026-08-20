package org.nibelungorum;

import cn.howxu.mmcr.api.machine.MachineDefinitions;
import cn.howxu.mmcr.api.machine.MachineStructureRegistry;
import cn.howxu.mmcr.api.recipe.RecipeRegistry;
import cn.howxu.mmcr.internal.api.PublicApiBootstrap;
import cn.howxu.mmcr.api.publicapi.event.MMCRInstallMachineStructuresEvent;
import cn.howxu.mmcr.api.publicapi.event.MMCRInstallRecipesEvent;
import cn.howxu.mmcr.api.publicapi.event.MMCRRegisterMachinesEvent;
import cn.howxu.mmcr.api.publicapi.event.MMCRRegisterRecipesEvent;
import cn.howxu.mmcr.internal.reload.DynamicContentReloadService;
import cn.howxu.mmcr.test.TestBootstrap;
import net.minecraft.resources.Identifier;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.common.NeoForge;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.nibelungorum.builtin.PublicBuiltinDefinitions;
import org.nibelungorum.builtin.PublicBuiltinMachineDefinitions;
import org.nibelungorum.builtin.PublicBuiltinRecipeDefinitions;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the built-in declarations exercise the public API without internal dependencies.
 *
 * @author howxu <dev@howxu.cn>
 */
class PublicBuiltinDefinitionsTest {
    private static final String ENTRYPOINT = "org.nibelungorum.builtin.PublicBuiltinDefinitions";

    @BeforeAll
    static void bootstrapMinecraft() throws Exception {
        TestBootstrap.bootstrap();
    }

    @BeforeEach
    void reset() {
        PublicApiBootstrap.clearForTesting();
        MachineDefinitions.clearForTesting();
        RecipeRegistry.clearForTesting();
        MachineStructureRegistry.clearForTesting();
        Fluids.WATER.builtInRegistryHolder().bindComponents(DataComponentMap.EMPTY);
    }

    @AfterEach
    void cleanup() {
        PublicApiBootstrap.clearForTesting();
        MachineDefinitions.clearForTesting();
        RecipeRegistry.clearForTesting();
        MachineStructureRegistry.clearForTesting();
    }

    @Test
    void public_builtins_install_representative_machine_and_recipe_definitions() throws Exception {
        PublicApiBootstrap.begin();
        MachineDefinitions.beginRegistryPhase();

        registerPublicBuiltins();
        PublicApiBootstrap.freezeAndInstall();

        assertThat(MachineDefinitions.getRegistration(id("blast_furnace"))).isNotNull();
        assertThat(MachineDefinitions.getRegistration(id("alloy_furnace"))).isNotNull();
        assertThat(RecipeRegistry.getRecipe(id("blast_furnace_iron_to_nugget"))).isNotNull();
        assertThat(RecipeRegistry.getRecipe(id("cracker_coal_lapis")).requirements())
                .anyMatch(requirement -> requirement instanceof cn.howxu.mmcr.api.recipe.requirement.FluidRequirement);
        assertThat(RecipeRegistry.getRecipe(id("cracker_coal_lapis")).machineId()).isEqualTo(id("cracker"));
    }

    @Test
    void public_structure_definitions_are_installed_from_public_declarations() throws Exception {
        PublicApiBootstrap.begin();
        MachineDefinitions.beginRegistryPhase();
        registerPublicBuiltins();
        PublicApiBootstrap.freezeAndInstall();

        DynamicContentReloadService.reload(candidate -> NeoForge.EVENT_BUS.post(
                new MMCRInstallMachineStructuresEvent(candidate)));

        assertThat(MachineStructureRegistry.dynamicSnapshot()).containsOnlyKeys(
                PublicBuiltinDefinitions.machineDefinitions().keySet());
        assertThat(MachineStructureRegistry.dynamicSnapshot().get(id("blast_furnace")).pattern())
                .isEqualTo(cn.howxu.mmcr.internal.api.PublicMachineAdapter
                        .toStructureDefinition(PublicBuiltinDefinitions.machineDefinitions().get(id("blast_furnace"))).pattern());
    }

    @Test
    void public_runtime_registration_is_repeatable() throws Exception {
        PublicApiBootstrap.begin();
        MachineDefinitions.beginRegistryPhase();
        registerPublicBuiltins();
        NeoForge.EVENT_BUS.post(new MMCRInstallRecipesEvent());
        PublicApiBootstrap.freezeAndInstall();

        NeoForge.EVENT_BUS.post(new MMCRInstallRecipesEvent());
        int recipeCount = RecipeRegistry.registeredRecipeCount();
        DynamicContentReloadService.reload(candidate -> NeoForge.EVENT_BUS.post(
                new MMCRInstallMachineStructuresEvent(candidate)));
        DynamicContentReloadService.reload(candidate -> NeoForge.EVENT_BUS.post(
                new MMCRInstallMachineStructuresEvent(candidate)));

        assertThat(RecipeRegistry.registeredRecipeCount()).isEqualTo(recipeCount);
        assertThat(MachineStructureRegistry.dynamicSnapshot()).containsOnlyKeys(
                PublicBuiltinDefinitions.machineDefinitions().keySet());
    }

    @Test
    void mod_startup_does_not_reference_legacy_builtin_registration() throws Exception {
        Class<?> entrypoint = Class.forName("cn.howxu.mmcr.MMCR");
        String bytecode = new String(entrypoint.getResourceAsStream("MMCR.class").readAllBytes(),
                StandardCharsets.ISO_8859_1);

        assertThat(bytecode).doesNotContain("LegacyBuiltinMachines");
        assertThat(bytecode).doesNotContain("LegacyDefaultRecipes");
    }

    @Test
    void public_builtins_entrypoint_has_no_internal_or_legacy_machine_dependencies() throws Exception {
        Class<?> entrypoint = Class.forName(ENTRYPOINT);
        String bytecode = new String(entrypoint.getResourceAsStream("PublicBuiltinDefinitions.class").readAllBytes(),
                StandardCharsets.ISO_8859_1);

        assertThat(bytecode).doesNotContain("cn/howxu/mmcr/api/machine/");
        assertThat(bytecode).doesNotContain("cn/howxu/mmcr/internal/");
    }

    private static void registerPublicBuiltins() throws Exception {
        NeoForge.EVENT_BUS.addListener((MMCRRegisterMachinesEvent event) -> PublicBuiltinMachineDefinitions.register(event));
        NeoForge.EVENT_BUS.addListener((MMCRInstallMachineStructuresEvent event) -> PublicBuiltinMachineDefinitions.install(event));
        NeoForge.EVENT_BUS.addListener((MMCRRegisterRecipesEvent event) -> PublicBuiltinRecipeDefinitions.register(event));
        NeoForge.EVENT_BUS.addListener((MMCRInstallRecipesEvent event) -> PublicBuiltinRecipeDefinitions.install(event));
        NeoForge.EVENT_BUS.post(new MMCRRegisterMachinesEvent());
        NeoForge.EVENT_BUS.post(new MMCRRegisterRecipesEvent());
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath("mmcr", path);
    }
}

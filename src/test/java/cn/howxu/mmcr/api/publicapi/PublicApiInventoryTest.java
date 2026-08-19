package cn.howxu.mmcr.api.publicapi;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guards the planned public API inventory before concrete public types are implemented.
 *
 * @author howxu &lt;dev@howxu.cn&gt;
 */
class PublicApiInventoryTest {

    private static final String PUBLIC_API_PACKAGE = "cn.howxu.mmcr.api.publicapi";

    private static final Set<String> PLANNED_PUBLIC_TYPES = Set.of(
            "cn.howxu.mmcr.api.publicapi.MachineApi",
            "cn.howxu.mmcr.api.publicapi.RecipeApi",
            "cn.howxu.mmcr.api.publicapi.ApiRegistrationException",
            "cn.howxu.mmcr.api.publicapi.machine.MachineDefinition",
            "cn.howxu.mmcr.api.publicapi.machine.MachineBuilder",
            "cn.howxu.mmcr.api.publicapi.machine.PatternBuilder",
            "cn.howxu.mmcr.api.publicapi.machine.PatternDefinition",
            "cn.howxu.mmcr.api.publicapi.machine.BlockPredicate",
            "cn.howxu.mmcr.api.publicapi.machine.ControllerSpec",
            "cn.howxu.mmcr.api.publicapi.machine.AppearanceSpec",
            "cn.howxu.mmcr.api.publicapi.machine.PortRequirements",
            "cn.howxu.mmcr.api.publicapi.machine.PortTiers",
            "cn.howxu.mmcr.api.publicapi.machine.StructureStage",
            "cn.howxu.mmcr.api.publicapi.machine.StructureRequirements",
            "cn.howxu.mmcr.api.publicapi.machine.FactorySpec",
            "cn.howxu.mmcr.api.publicapi.machine.LevelRequirement",
            "cn.howxu.mmcr.api.publicapi.recipe.MachineRecipeDefinition",
            "cn.howxu.mmcr.api.publicapi.recipe.MachineRecipeBuilder",
            "cn.howxu.mmcr.api.publicapi.recipe.ItemInput",
            "cn.howxu.mmcr.api.publicapi.recipe.FluidInput",
            "cn.howxu.mmcr.api.publicapi.recipe.EnergyInput",
            "cn.howxu.mmcr.api.publicapi.recipe.ItemOutput",
            "cn.howxu.mmcr.api.publicapi.recipe.FluidOutput");

    private static final Map<String, String> PUBLIC_JAVADOC_ROLES = Map.ofEntries(
            Map.entry("cn.howxu.mmcr.api.publicapi.MachineApi", "Startup machine registration entry point."),
            Map.entry("cn.howxu.mmcr.api.publicapi.RecipeApi", "Startup recipe registration entry point."),
            Map.entry("cn.howxu.mmcr.api.publicapi.ApiRegistrationException", "Public registration validation failure."),
            Map.entry("cn.howxu.mmcr.api.publicapi.machine.MachineDefinition", "Immutable machine declaration."),
            Map.entry("cn.howxu.mmcr.api.publicapi.machine.MachineBuilder", "Fluent machine declaration builder."),
            Map.entry("cn.howxu.mmcr.api.publicapi.machine.PatternBuilder", "Layered structure pattern builder."),
            Map.entry("cn.howxu.mmcr.api.publicapi.machine.PatternDefinition", "Immutable layered structure pattern."),
            Map.entry("cn.howxu.mmcr.api.publicapi.machine.BlockPredicate", "Public block matching predicate value."),
            Map.entry("cn.howxu.mmcr.api.publicapi.machine.ControllerSpec", "Controller texture and facing declaration."),
            Map.entry("cn.howxu.mmcr.api.publicapi.machine.AppearanceSpec", "Machine appearance declaration."),
            Map.entry("cn.howxu.mmcr.api.publicapi.machine.PortRequirements", "Basic port count declaration."),
            Map.entry("cn.howxu.mmcr.api.publicapi.machine.PortTiers", "Port category and tier declaration."),
            Map.entry("cn.howxu.mmcr.api.publicapi.machine.StructureStage", "Structure stage declaration."),
            Map.entry("cn.howxu.mmcr.api.publicapi.machine.StructureRequirements", "Level slot and modifier declaration."),
            Map.entry("cn.howxu.mmcr.api.publicapi.machine.FactorySpec", "Factory parallelism and thread declaration."),
            Map.entry("cn.howxu.mmcr.api.publicapi.machine.LevelRequirement", "Recipe level requirement value."),
            Map.entry("cn.howxu.mmcr.api.publicapi.recipe.MachineRecipeDefinition", "Immutable machine recipe declaration."),
            Map.entry("cn.howxu.mmcr.api.publicapi.recipe.MachineRecipeBuilder", "Fluent machine recipe declaration builder."),
            Map.entry("cn.howxu.mmcr.api.publicapi.recipe.ItemInput", "Item or tag input declaration."),
            Map.entry("cn.howxu.mmcr.api.publicapi.recipe.FluidInput", "Fluid input declaration."),
            Map.entry("cn.howxu.mmcr.api.publicapi.recipe.EnergyInput", "Energy input or output declaration."),
            Map.entry("cn.howxu.mmcr.api.publicapi.recipe.ItemOutput", "Item output declaration."),
            Map.entry("cn.howxu.mmcr.api.publicapi.recipe.FluidOutput", "Fluid output declaration."));

    private static final Set<String> FORBIDDEN_PUBLIC_SIGNATURE_FRAGMENTS = Set.of(
            "MachineRegistry",
            "RecipeRegistry",
            "CompiledMachinePattern",
            "BlockArrayCache",
            "DynamicContentReloadService",
            ".internal.",
            ".client.");

    @Test
    void public_api_package_root_exists() {
        Path packageInfo = Path.of("src/main/java", PUBLIC_API_PACKAGE.replace('.', '/'), "package-info.java");
        assertThat(Files.isRegularFile(packageInfo)).isTrue();
    }

    @Test
    void planned_public_types_have_javadoc_roles_and_safe_signature_names() {
        assertThat(PUBLIC_JAVADOC_ROLES.keySet()).containsExactlyInAnyOrderElementsOf(PLANNED_PUBLIC_TYPES);
        assertThat(PUBLIC_JAVADOC_ROLES.values()).allSatisfy(role -> assertThat(role).isNotBlank());
        assertThat(PLANNED_PUBLIC_TYPES).allSatisfy(type -> assertThat(FORBIDDEN_PUBLIC_SIGNATURE_FRAGMENTS)
                .noneMatch(type::contains));
    }
}

package org.nibelungorum;

import cn.howxu.mmcr.api.machine.MachineDefinitions;
import cn.howxu.mmcr.api.recipe.RecipeRegistry;
import cn.howxu.mmcr.internal.api.PublicApiBootstrap;
import cn.howxu.mmcr.test.TestBootstrap;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.nibelungorum.builtin.PublicBuiltinDefinitions;

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
    }

    @AfterEach
    void cleanup() {
        PublicApiBootstrap.clearForTesting();
        MachineDefinitions.clearForTesting();
        RecipeRegistry.clearForTesting();
    }

    @Test
    void public_builtins_install_representative_machine_and_recipe_definitions() throws Exception {
        PublicApiBootstrap.begin();
        MachineDefinitions.beginRegistryPhase();

        registerPublicBuiltins();
        PublicBuiltinDefinitions.registerRecipes();
        PublicApiBootstrap.freezeAndInstall();

        assertThat(MachineDefinitions.getRegistration(id("blast_furnace"))).isNotNull();
        assertThat(MachineDefinitions.getRegistration(id("alloy_furnace"))).isNotNull();
        assertThat(RecipeRegistry.getRecipe(id("blast_furnace_iron_to_nugget"))).isNotNull();
        assertThat(RecipeRegistry.getRecipe(id("cracker_coal_lapis")).requirements())
                .anyMatch(requirement -> requirement instanceof cn.howxu.mmcr.api.recipe.requirement.FluidRequirement);
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
        Class<?> entrypoint = Class.forName(ENTRYPOINT);
        Method register = entrypoint.getMethod("register");
        register.invoke(null);
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath("mmcr", path);
    }
}

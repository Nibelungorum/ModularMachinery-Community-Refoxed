package cn.howxu.mmcr.api.publicapi;

import cn.howxu.mmcr.api.machine.MachineDefinitions;
import cn.howxu.mmcr.api.machine.MachineStructureRegistry;
import cn.howxu.mmcr.api.publicapi.event.MMCRRegisterMachinesEvent;
import cn.howxu.mmcr.api.publicapi.event.MMCRRegisterRecipesEvent;
import cn.howxu.mmcr.api.publicapi.machine.BlockPredicate;
import cn.howxu.mmcr.api.publicapi.machine.MachineBuilder;
import cn.howxu.mmcr.api.publicapi.machine.MachineDefinition;
import cn.howxu.mmcr.api.publicapi.recipe.MachineRecipeBuilder;
import cn.howxu.mmcr.api.publicapi.recipe.MachineRecipeDefinition;
import cn.howxu.mmcr.api.recipe.RecipeRegistry;
import cn.howxu.mmcr.internal.api.PublicApiBootstrap;
import cn.howxu.mmcr.test.TestBootstrap;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.common.NeoForge;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Verifies the NeoForge event bus delivers self-created public machine and recipe registration events.
 *
 * @author howxu <dev@howxu.cn>
 */
class PublicEventSubscribersTest {
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
    }

    @AfterEach
    void cleanup() {
        PublicApiBootstrap.clearForTesting();
        MachineDefinitions.clearForTesting();
        RecipeRegistry.clearForTesting();
        MachineStructureRegistry.clearForTesting();
    }

    @Test
    void subscriber_receives_register_event_on_neoforge_event_bus() {
        AtomicInteger callCount = new AtomicInteger();
        AtomicBoolean active = new AtomicBoolean(true);
        NeoForge.EVENT_BUS.addListener((MMCRRegisterMachinesEvent event) -> {
            if (active.get()) callCount.incrementAndGet();
        });
        try {
            NeoForge.EVENT_BUS.post(new MMCRRegisterMachinesEvent());

            assertThat(callCount.get()).isEqualTo(1);
        } finally {
            active.set(false);
        }
    }

    @Test
    void registering_machine_via_event_outside_window_throws() {
        AtomicReference<Throwable> captured = new AtomicReference<>();
        AtomicBoolean active = new AtomicBoolean(true);
        NeoForge.EVENT_BUS.addListener((MMCRRegisterMachinesEvent event) -> {
            if (!active.get()) return;
            try {
                event.registerMachine(machine("outside_window"));
            } catch (Throwable t) {
                captured.set(t);
            }
        });

        try {
            NeoForge.EVENT_BUS.post(new MMCRRegisterMachinesEvent());

            assertThat(captured.get())
                    .isInstanceOf(ApiRegistrationException.class)
                    .hasMessageContaining("outside_window");
        } finally {
            active.set(false);
        }
    }

    @Test
    void repeat_post_with_same_machine_id_throws_duplicate() {
        PublicApiBootstrap.begin();
        AtomicInteger attempt = new AtomicInteger();
        AtomicBoolean active = new AtomicBoolean(true);
        NeoForge.EVENT_BUS.addListener((MMCRRegisterMachinesEvent event) -> {
            if (!active.get()) return;
            attempt.incrementAndGet();
            event.registerMachine(machine("repeat_id"));
        });

        try {
            NeoForge.EVENT_BUS.post(new MMCRRegisterMachinesEvent());

            assertThatThrownBy(() -> NeoForge.EVENT_BUS.post(new MMCRRegisterMachinesEvent()))
                    .isInstanceOf(ApiRegistrationException.class)
                    .hasMessageContaining("repeat_id");
        } finally {
            active.set(false);
        }
    }

    @Test
    void repeat_post_with_same_recipe_id_throws_duplicate() {
        PublicApiBootstrap.begin();
        AtomicInteger attempt = new AtomicInteger();
        AtomicBoolean active = new AtomicBoolean(true);
        NeoForge.EVENT_BUS.addListener((MMCRRegisterRecipesEvent event) -> {
            if (!active.get()) return;
            attempt.incrementAndGet();
            event.registerRecipe(recipe("repeat_recipe", machine("recipe_target").id()));
        });

        try {
            NeoForge.EVENT_BUS.post(new MMCRRegisterRecipesEvent());

            assertThatThrownBy(() -> NeoForge.EVENT_BUS.post(new MMCRRegisterRecipesEvent()))
                    .isInstanceOf(ApiRegistrationException.class)
                    .hasMessageContaining("repeat_recipe");
        } finally {
            active.set(false);
        }
    }

    @Test
    void machine_event_installs_before_recipe_event() {
        PublicApiBootstrap.begin();
        MachineDefinitions.beginRegistryPhase();
        MachineDefinition machine = machine("event_machine");
        MachineRecipeDefinition recipe = recipe("event_recipe", machine.id());
        AtomicBoolean active = new AtomicBoolean(true);
        NeoForge.EVENT_BUS.addListener((MMCRRegisterMachinesEvent event) -> {
            if (active.get()) event.registerMachine(machine);
        });
        NeoForge.EVENT_BUS.addListener((MMCRRegisterRecipesEvent event) -> {
            if (active.get()) event.registerRecipe(recipe);
        });

        try {
            NeoForge.EVENT_BUS.post(new MMCRRegisterMachinesEvent());
            PublicApiBootstrap.freezeAndInstallMachines();

            assertThat(MachineDefinitions.getRegistration(machine.id())).isNotNull();
            assertThat(RecipeRegistry.getRecipe(recipe.id())).isNull();

            NeoForge.EVENT_BUS.post(new MMCRRegisterRecipesEvent());
            PublicApiBootstrap.installRecipes();

            assertThat(RecipeRegistry.getRecipe(recipe.id())).isNotNull();
        } finally {
            active.set(false);
        }
    }

    private static MachineDefinition machine(String path) {
        return MachineBuilder.machine(id(path))
                .pattern(pattern -> pattern
                        .layer("F")
                        .where('F', BlockPredicate.block(Blocks.FURNACE))
                        .controller('F'))
                .build();
    }

    private static MachineRecipeDefinition recipe(String path, Identifier machineId) {
        return MachineRecipeBuilder.recipe(id(path), machineId)
                .duration(1)
                .build();
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath("mmcr", path);
    }
}

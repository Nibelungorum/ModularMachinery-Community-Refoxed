package cn.howxu.mmcr.publicapi.event;

import cn.howxu.mmcr.api.registration.ApiRegistrationException;
import cn.howxu.mmcr.api.registration.MachineDefinitionRegistration;
import cn.howxu.mmcr.api.machine.definition.MachineBuilder;
import cn.howxu.mmcr.publicapi.Machines;
import cn.howxu.mmcr.internal.api.facade.registration.RegistrationAdapters;
import cn.howxu.mmcr.internal.registration.ContentRegistrationCoordinator;
import cn.howxu.mmcr.publicapi.Recipes;
import cn.howxu.mmcr.publicapi.registration.RegistrationException;
import cn.howxu.mmcr.test.TestBootstrap;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Type;
import java.nio.charset.StandardCharsets;
import static org.junit.jupiter.api.Assertions.*;

/** Core collectors own rejection order and retain callback/build diagnostics.
 * @author howxu <dev@howxu.cn>
 */
class PublicRegistrationDiagnosticsTest {
    private static final ResourceLocation ID = ResourceLocation.parse("test:registration_diagnostics");

    @BeforeAll
    static void bootstrap() throws Exception { TestBootstrap.bootstrap(); }

    @Test
    void coordinator_bytecode_has_no_public_event_or_facade_dependency() throws Exception {
        try (var stream = ContentRegistrationCoordinator.class.getResourceAsStream("ContentRegistrationCoordinator.class")) {
            assertNotNull(stream);
            byte[] bytes = stream.readAllBytes();
            var model = new ClassReader(bytes);
            char[] buffer = new char[model.getMaxStringLength()];
            for (int index = 1; index < model.getItemCount(); index++) {
                int offset = model.getItem(index);
                if (offset == 0) continue;
                int tag = model.readByte(offset - 1);
                if (tag == 7) {
                    var type = (Type) model.readConst(index, buffer);
                    assertFalse(type.getInternalName().startsWith("cn/howxu/mmcr/publicapi/"));
                    assertFalse(type.getInternalName().startsWith("cn/howxu/mmcr/internal/api/facade/"));
                }
                if (tag == 1) {
                    String descriptor = new String(bytes, offset + 2, model.readUnsignedShort(offset), StandardCharsets.UTF_8);
                    if (!descriptor.startsWith("(")) continue;
                    assertFalse(descriptor.contains("Lcn/howxu/mmcr/publicapi/"));
                    assertFalse(descriptor.contains("Lcn/howxu/mmcr/internal/api/facade/"));
                }
            }
        }
    }

    @Test
    void machine_overloads_and_direct_core_share_rejection_diagnostics_before_callback() {
        var event = new RegisterMachineDefinitionsEvent();
        var machine = Machines.machine(ID).build();
        event.registerMachine(machine);
        var direct = new MachineDefinitionRegistration();
        var definition = MachineBuilder.machine(ID).build();
        direct.registerMachine(definition);
        var calls = new AtomicInteger();
        for (boolean frozen : new boolean[] {false, true}) {
            if (frozen) { RegistrationAdapters.freeze(event); direct.freeze(); }
            var spec = assertThrows(RegistrationException.class, () -> event.registerMachine(machine));
            var callback = assertThrows(RegistrationException.class,
                    () -> event.registerMachine(ID, draft -> calls.incrementAndGet()));
            var coreSpec = assertThrows(ApiRegistrationException.class, () -> direct.registerMachine(definition));
            var coreCallback = assertThrows(ApiRegistrationException.class,
                    () -> direct.registerMachine(ID, builder -> { calls.incrementAndGet(); return builder; }));
            assertInstanceOf(ApiRegistrationException.class, spec.getCause());
            assertInstanceOf(ApiRegistrationException.class, callback.getCause());
            assertEquals(coreSpec.getMessage(), spec.getMessage());
            assertEquals(coreCallback.getMessage(), callback.getMessage());
            assertEquals(spec.getMessage(), callback.getMessage());
            assertTrue(callback.getMessage().contains(ID.toString()));
            assertTrue(callback.getMessage().contains(frozen ? "frozen" : "Duplicate"));
            assertTrue(callback.getMessage().contains("registration"));
            assertEquals(0, calls.get());
            assertEquals(Set.of(ID), event.definitions().keySet());
            assertEquals(Set.of(ID), direct.definitions().keySet());
        }
    }

    @Test
    void machine_callback_and_build_failures_are_owned_by_core_with_phase_and_root_cause() {
        var event = new RegisterMachineDefinitionsEvent();
        var direct = new MachineDefinitionRegistration();
        for (RuntimeException original : new RuntimeException[] {
                new IllegalArgumentException("callback validation"), new ApiRegistrationException("callback registration")}) {
            var failure = assertThrows(RegistrationException.class,
                    () -> event.registerMachine(ID, draft -> { throw original; }));
            var coreFailure = assertThrows(ApiRegistrationException.class,
                    () -> direct.registerMachine(ID, builder -> { throw original; }));
            assertInstanceOf(ApiRegistrationException.class, failure.getCause());
            assertEquals(coreFailure.getMessage(), failure.getMessage());
            assertTrue(failure.getMessage().contains(ID.toString()));
            assertTrue(failure.getMessage().contains("configuration"));
            assertSame(original, root(failure));
            assertSame(original, coreFailure.getCause());
        }
        var calls = new AtomicInteger();
        var buildFailure = assertThrows(RegistrationException.class,
                () -> event.registerMachine(ID, draft -> { calls.incrementAndGet(); draft.maxParallelism(0); }));
        var coreBuildFailure = assertThrows(ApiRegistrationException.class,
                () -> direct.registerMachine(ID, builder -> { calls.incrementAndGet(); return builder.maxParallelism(0); }));
        assertEquals(2, calls.get());
        assertEquals(coreBuildFailure.getMessage(), buildFailure.getMessage());
        assertTrue(buildFailure.getMessage().contains(ID.toString()));
        assertTrue(buildFailure.getMessage().contains("build"));
        assertInstanceOf(IllegalArgumentException.class, root(buildFailure));
        assertSame(root(buildFailure), buildFailure.getCause().getCause());
        assertSame(root(coreBuildFailure), coreBuildFailure.getCause());
        assertEquals(root(coreBuildFailure).getMessage(), root(buildFailure).getMessage());
        assertTrue(event.definitions().isEmpty());
        assertTrue(direct.definitions().isEmpty());
        assertThrows(NullPointerException.class, () -> event.registerMachine(ID, null));
        assertThrows(NullPointerException.class, () -> direct.registerMachine(ID, null));
        event.registerMachine(Machines.machine(ID).build());
        direct.registerMachine(MachineBuilder.machine(ID).build());
    }

    @Test
    void recipe_overloads_share_duplicate_and_frozen_core_causes_and_never_run_rejected_callbacks() {
        var event = new RegisterMachineRecipesEvent();
        var recipe = Recipes.recipe(ID).recipePool(ID).build();
        event.registerRecipe(recipe);
        var calls = new AtomicInteger();
        for (boolean frozen : new boolean[] {false, true}) {
            if (frozen) RegistrationAdapters.freeze(event);
            var specFailure = assertThrows(RegistrationException.class, () -> event.registerRecipe(recipe));
            var callbackFailure = assertThrows(RegistrationException.class,
                    () -> event.registerRecipe(ID, draft -> calls.incrementAndGet()));
            assertInstanceOf(ApiRegistrationException.class, specFailure.getCause());
            assertInstanceOf(ApiRegistrationException.class, callbackFailure.getCause());
            assertEquals(specFailure.getMessage(), callbackFailure.getMessage());
            assertTrue(callbackFailure.getMessage().contains(ID.toString()));
            assertTrue(callbackFailure.getMessage().contains(frozen ? "frozen" : "Duplicate"));
            assertEquals(0, calls.get());
            assertEquals(Set.of(ID), event.recipes().keySet());
        }
    }

    @Test
    void recipe_and_structure_build_failures_keep_validation_root_and_do_not_register() {
        var recipes = new RegisterMachineRecipesEvent();
        var recipeCalls = new AtomicInteger();
        var recipeFailure = assertThrows(RegistrationException.class,
                () -> recipes.registerRecipe(ID, draft -> recipeCalls.incrementAndGet()));
        assertEquals(1, recipeCalls.get());
        assertTrue(recipeFailure.getMessage().contains(ID.toString()));
        assertTrue(recipeFailure.getMessage().contains("recipe pool"));
        assertInstanceOf(IllegalStateException.class, root(recipeFailure));
        assertTrue(recipes.recipes().isEmpty());

        var structures = new RegisterMachineStructuresEvent(Set.of(ID));
        var structureCalls = new AtomicInteger();
        var structureFailure = assertThrows(RegistrationException.class,
                () -> structures.registerStructure(ID, draft -> structureCalls.incrementAndGet()));
        assertEquals(1, structureCalls.get());
        assertTrue(structureFailure.getMessage().contains(ID.toString()));
        assertTrue(structureFailure.getMessage().contains("Main machine structure"));
        assertInstanceOf(IllegalArgumentException.class, root(structureFailure));
        assertTrue(structures.structures().isEmpty());
    }

    @Test
    void callback_thrown_registration_exception_without_id_preserves_identity_in_cause_chain() {
        var structures = new RegisterMachineStructuresEvent(Set.of(ID));
        var original = new ApiRegistrationException("callback validation");
        var failure = assertThrows(RegistrationException.class,
                () -> structures.registerStructure(ID, draft -> { throw original; }));
        assertTrue(failure.getMessage().contains(ID.toString()));
        assertSame(original, root(failure));
        assertTrue(structures.structures().isEmpty());

        var recipes = new RegisterMachineRecipesEvent();
        var recipeFailure = assertThrows(RegistrationException.class,
                () -> recipes.registerRecipe(ID, draft -> { throw original; }));
        assertSame(original, root(recipeFailure));
        assertTrue(recipes.recipes().isEmpty());
    }

    private static Throwable root(Throwable failure) {
        while (failure.getCause() != null) failure = failure.getCause();
        return failure;
    }
}

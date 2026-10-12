package cn.howxu.mmcr.internal.client;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.jei.JeiWorkstationRegistration;
import cn.howxu.mmcr.api.recipe.RecipeInformation;
import cn.howxu.mmcr.client.recipe.MachineRecipeData;
import cn.howxu.mmcr.compat.jei.JeiPlugin;
import cn.howxu.mmcr.publicapi.event.RegisterJeiRecipeInformationEvent;
import cn.howxu.mmcr.publicapi.event.RegisterJeiWorkstationsEvent;
import cn.howxu.mmcr.test.RecipeTestSupport;
import cn.howxu.mmcr.test.TestBootstrap;
import mezz.jei.api.helpers.IJeiHelpers;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionException;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Deterministic registration lifecycle checks without a live Minecraft client.
 * @author howxu <dev@howxu.cn>
 */
class RecipeViewerRegistrationTest {
    private static final ResourceLocation POOL = MMCR.id("viewer_pool");
    private static final ResourceLocation RECIPE = MMCR.id("viewer_recipe");

    @BeforeAll
    static void bootstrap() throws Exception { TestBootstrap.bootstrap(); }

    @AfterEach
    void clearRegistries() {
        RecipeInformationRegistry.clearForTesting();
        JeiWorkstationRegistry.replaceKubeJS(List.of());
    }

    @Test
    void worker_capture_posts_each_event_once_only_when_client_executor_runs() {
        List<Runnable> queued = new ArrayList<>();
        AtomicBoolean clientThread = new AtomicBoolean();
        AtomicInteger informationEvents = new AtomicInteger();
        AtomicInteger workstationEvents = new AtomicInteger();
        RecipeViewerRegistration coordinator = coordinator(queued, clientThread, event -> {
            assertThat(clientThread).isTrue();
            informationEvents.incrementAndGet();
            event.registerRecipePool(POOL, "test.public.pool");
        }, event -> {
            assertThat(clientThread).isTrue();
            workstationEvents.incrementAndGet();
        });

        var future = coordinator.capture();
        assertThat(future).isNotDone();
        assertThat(informationEvents).hasValue(0);
        assertThat(workstationEvents).hasValue(0);
        clientThread.set(true);
        queued.getFirst().run();

        assertThat(informationEvents).hasValue(1);
        assertThat(workstationEvents).hasValue(1);
        assertThat(future.join().informationFor(POOL, RECIPE)).containsExactly(Component.translatable("test.public.pool"));
        assertThat(RecipeInformationRegistry.publicSnapshot().viewer()).isSameAs(future.join());
    }

    @Test
    void each_cycle_updates_registration_even_when_content_version_is_unchanged() {
        AtomicInteger cycle = new AtomicInteger();
        RecipeViewerRegistration coordinator = coordinator(new ArrayList<>(), new AtomicBoolean(true),
                event -> event.registerRecipePool(POOL, "test.cycle." + cycle.incrementAndGet()), ignored -> { });
        RecipeViewerSnapshot first = coordinator.capture().join();
        coordinator.changeInformation();
        RecipeViewerSnapshot second = coordinator.capture().join();

        assertThat(first.version().content()).isEqualTo(second.version().content());
        assertThat(second.version().registration()).isGreaterThan(first.version().registration());
        assertThat(second.version().information()).isGreaterThan(first.version().information());
        assertThat(second.version()).isNotEqualTo(first.version());
        assertThat(first.informationFor(POOL, RECIPE)).containsExactly(Component.translatable("test.cycle.1"));
        assertThat(second.informationFor(POOL, RECIPE)).containsExactly(Component.translatable("test.cycle.2"));
    }

    @Test
    void workstation_event_failure_retains_all_of_the_previous_public_commit() {
        AtomicBoolean fail = new AtomicBoolean();
        RecipeViewerRegistration coordinator = coordinator(new ArrayList<>(), new AtomicBoolean(true),
                event -> event.registerRecipePool(POOL, fail.get() ? "test.new" : "test.old"), event -> {
                    event.addRecipePoolWorkstation(POOL, stack());
                    if (fail.get()) throw new IllegalStateException("workstation failure");
                });
        RecipeViewerSnapshot previous = coordinator.capture().join();
        var publicCommit = RecipeInformationRegistry.publicSnapshot();
        fail.set(true);

        assertThatThrownBy(() -> coordinator.capture().join()).isInstanceOf(CompletionException.class)
                .hasCauseInstanceOf(IllegalStateException.class);
        assertThat(RecipeInformationRegistry.publicSnapshot()).isSameAs(publicCommit);
        assertThat(publicCommit.viewer()).isSameAs(previous);
        assertThat(RecipeInformationRegistry.componentsFor(POOL, RECIPE)).containsExactly(Component.translatable("test.old"));
        assertThat(JeiWorkstationRegistry.publicEntries()).isEqualTo(previous.workstations());
    }

    @Test
    void content_resolution_failure_never_publishes_candidate_information_or_workstations() {
        RecipeInformationRegistry.replacePublic(List.of(RecipeInformation.pool(POOL, "test.old")));
        var previous = RecipeInformationRegistry.publicSnapshot();
        RecipeViewerRegistration coordinator = new RecipeViewerRegistration(Runnable::run, () -> true,
                event -> event.registerRecipePool(POOL, "test.new"), event -> event.addRecipePoolWorkstation(POOL, stack()),
                entries -> {
                    assertThat(RecipeInformationRegistry.componentsFor(POOL, RECIPE))
                            .containsExactly(Component.translatable("test.new"));
                    assertThat(RecipeInformationRegistry.publicSnapshot()).isSameAs(previous);
                    throw new IllegalStateException("content failure");
                }, () -> 7L);

        assertThatThrownBy(() -> coordinator.capture().join()).isInstanceOf(CompletionException.class);
        assertThat(RecipeInformationRegistry.publicSnapshot()).isSameAs(previous);
        assertThat(RecipeInformationRegistry.componentsFor(POOL, RECIPE)).containsExactly(Component.translatable("test.old"));
    }

    @Test
    void recipe_dtos_use_candidate_information_and_do_not_follow_later_registry_replacements() {
        RecipeInformationRegistry.replacePublic(List.of(RecipeInformation.pool(POOL, "test.stale")));
        RecipeInformationRegistry.replaceKubeJS(List.of(RecipeInformation.recipe(RECIPE, "test.kubejs")));
        RecipeViewerRegistration coordinator = new RecipeViewerRegistration(Runnable::run, () -> true,
                event -> event.registerRecipePool(POOL, "test.candidate"), ignored -> { }, entries -> {
                    var recipe = RecipeTestSupport.create(RECIPE, POOL, 20, List.of(), List.of(stack()));
                    var data = MachineRecipeData.from(recipe, null);
                    return new RecipeViewerRegistration.Content(Map.of(POOL, List.of(data)), List.of(), Map.of(), entries);
                }, () -> 7L);
        RecipeViewerSnapshot snapshot = coordinator.capture().join();
        RecipeInformationRegistry.replacePublic(List.of(RecipeInformation.pool(POOL, "test.later.public")));
        RecipeInformationRegistry.replaceKubeJS(List.of(RecipeInformation.recipe(RECIPE, "test.later.kubejs")));

        assertThat(snapshot.informationFor(POOL, RECIPE)).containsExactly(
                Component.translatable("test.candidate"), Component.translatable("test.kubejs"));
        assertThat(snapshot.recipesByPool().get(POOL).getFirst().metadata()).extracting(row -> row.text())
                .contains(Component.translatable("test.candidate"), Component.translatable("test.kubejs"))
                .doesNotContain(Component.translatable("test.stale"), Component.translatable("test.later.public"),
                        Component.translatable("test.later.kubejs"));
    }

    @Test
    void reset_cancels_queued_future_and_callback_without_publishing_events() {
        List<Runnable> queued = new ArrayList<>();
        AtomicInteger events = new AtomicInteger();
        AtomicBoolean client = new AtomicBoolean();
        RecipeViewerRegistration coordinator = coordinator(queued, client,
                ignored -> events.incrementAndGet(), ignored -> events.incrementAndGet());
        var old = coordinator.capture();
        coordinator.reset();
        assertThat(old).isCancelled();
        client.set(true);
        queued.getFirst().run();

        assertThat(events).hasValue(0);
        assertThat(RecipeInformationRegistry.publicSnapshot().viewer()).isNull();
        assertThat(coordinator.capture().join().version().session()).isEqualTo(1L);
        assertThat(events).hasValue(2);
    }

    @Test
    void capture_bound_to_worker_start_session_is_cancelled_if_reset_precedes_capture() {
        List<Runnable> queued = new ArrayList<>();
        AtomicInteger events = new AtomicInteger();
        AtomicBoolean client = new AtomicBoolean();
        RecipeViewerRegistration coordinator = coordinator(queued, client,
                ignored -> events.incrementAndGet(), ignored -> events.incrementAndGet());
        long workerSession = 0L;

        coordinator.reset(); // Between the native started callback and capture submission.
        var obsolete = coordinator.capture(workerSession);
        assertThat(obsolete).isCancelled();
        assertThat(queued).isEmpty();
        assertThat(events).hasValue(0);
        assertThat(RecipeInformationRegistry.publicSnapshot().viewer()).isNull();

        var current = coordinator.capture(1L);
        client.set(true);
        queued.getFirst().run();
        assertThat(current.join().version().session()).isEqualTo(1L);
        assertThat(events).hasValue(2);
    }

    @Test
    void bound_capture_future_is_cancelled_when_reset_happens_after_submission() {
        List<Runnable> queued = new ArrayList<>();
        AtomicInteger events = new AtomicInteger();
        AtomicBoolean client = new AtomicBoolean();
        RecipeViewerRegistration coordinator = coordinator(queued, client,
                ignored -> events.incrementAndGet(), ignored -> events.incrementAndGet());
        var obsolete = coordinator.capture(0L);
        coordinator.reset();
        client.set(true);
        queued.getFirst().run();
        assertThat(obsolete).isCancelled();
        assertThat(events).hasValue(0);
    }

    @Test
    void reset_during_information_event_cancels_future_and_skips_second_event() {
        RecipeViewerRegistration[] reference = new RecipeViewerRegistration[1];
        AtomicInteger workstationEvents = new AtomicInteger();
        reference[0] = coordinator(new ArrayList<>(), new AtomicBoolean(true),
                ignored -> reference[0].reset(), ignored -> workstationEvents.incrementAndGet());

        assertThat(reference[0].capture()).isCancelled();
        assertThat(workstationEvents).hasValue(0);
        assertThat(RecipeInformationRegistry.publicSnapshot().viewer()).isNull();
    }

    @Test
    void reset_during_content_resolution_prevents_old_session_commit() {
        RecipeViewerRegistration[] reference = new RecipeViewerRegistration[1];
        reference[0] = new RecipeViewerRegistration(Runnable::run, () -> true,
                event -> event.registerRecipePool(POOL, "test.old.session"), ignored -> { }, entries -> {
                    reference[0].reset();
                    return emptyContent(entries);
                }, () -> 7L);

        assertThat(reference[0].capture()).isCancelled();
        assertThat(RecipeInformationRegistry.publicSnapshot().viewer()).isNull();
        assertThat(RecipeInformationRegistry.componentsFor(POOL, RECIPE)).isEmpty();
    }

    @Test
    void rejecting_executor_and_collection_error_finish_the_future() {
        RecipeViewerRegistration rejecting = new RecipeViewerRegistration(
                ignored -> { throw new RejectedExecutionException("rejected"); }, () -> false,
                ignored -> { }, ignored -> { }, RecipeViewerRegistrationTest::emptyContent, () -> 7L);
        assertThatThrownBy(() -> rejecting.capture().join()).isInstanceOf(CompletionException.class)
                .hasCauseInstanceOf(RejectedExecutionException.class);
        RecipeViewerRegistration broken = coordinator(new ArrayList<>(), new AtomicBoolean(true),
                ignored -> { throw new AssertionError("collector error"); }, ignored -> { });
        assertThatThrownBy(() -> broken.capture().join()).isInstanceOf(CompletionException.class)
                .hasCauseInstanceOf(AssertionError.class);
    }

    @Test
    void snapshot_preserves_source_order_and_owns_lists_stacks_and_nested_components() {
        Component argument = Component.literal("original");
        List<RecipeInformation> publicInformation = new ArrayList<>(List.of(
                RecipeInformation.pool(POOL, "test.public.pool", argument),
                RecipeInformation.recipe(RECIPE, "test.public.recipe")));
        List<RecipeInformation> kubeJSInformation = new ArrayList<>(List.of(
                RecipeInformation.pool(POOL, "test.kubejs.pool"), RecipeInformation.recipe(RECIPE, "test.kubejs.recipe")));
        List<ResourceLocation> machineIds = new ArrayList<>(List.of(MMCR.id("machine_b"), MMCR.id("machine_a")));
        Map<ResourceLocation, List<ResourceLocation>> machines = new LinkedHashMap<>();
        machines.put(POOL, machineIds);
        machines.put(MMCR.id("second_pool"), new ArrayList<>());
        ItemStack workstation = stack();
        List<JeiWorkstationRegistration> workstations = new ArrayList<>(List.of(
                new JeiWorkstationRegistration.RecipePoolStack(POOL, workstation)));
        MachineRecipeData data = MachineRecipeData.from(RecipeTestSupport.create(RECIPE, POOL, 20, List.of(),
                List.of(stack())), null);
        List<MachineRecipeData> recipes = new ArrayList<>(List.of(data));
        RecipeViewerSnapshot snapshot = new RecipeViewerSnapshot(new RecipeViewerSnapshot.Version(0, 7, 0, 1),
                Map.of(POOL, recipes), List.of(), machines, workstations, publicInformation, kubeJSInformation);

        machineIds.clear();
        machines.clear();
        recipes.clear();
        publicInformation.clear();
        kubeJSInformation.clear();
        workstations.clear();
        argument.getSiblings().add(Component.literal("mutated"));
        workstation.setCount(32);
        var returnedStack = ((JeiWorkstationRegistration.RecipePoolStack) snapshot.workstations().getFirst()).workstation();
        returnedStack.set(DataComponents.CUSTOM_NAME, Component.literal("mutated"));
        snapshot.informationFor(POOL, RECIPE).getFirst().getSiblings().add(Component.literal("mutated"));
        data.entries().getFirst().items().getFirst().setCount(32);

        assertThat(snapshot.machinesByPool().keySet()).containsExactly(POOL, MMCR.id("second_pool"));
        assertThat(snapshot.machinesByPool().get(POOL)).containsExactly(MMCR.id("machine_b"), MMCR.id("machine_a"));
        assertThat(snapshot.recipesByPool().get(POOL)).containsExactly(data);
        assertThat(snapshot.informationFor(POOL, RECIPE)).containsExactly(
                Component.translatable("test.public.pool", Component.literal("original")),
                Component.translatable("test.kubejs.pool"), Component.translatable("test.public.recipe"),
                Component.translatable("test.kubejs.recipe"));
        assertThat(((JeiWorkstationRegistration.RecipePoolStack) snapshot.workstations().getFirst()).workstation()
                .get(DataComponents.CUSTOM_NAME)).isNull();
        assertThat(data.entries().getFirst().items().getFirst().getCount()).isEqualTo(1);
        assertThatThrownBy(() -> snapshot.machinesByPool().clear()).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> snapshot.recipesByPool().get(POOL).clear()).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void workstations_deduplicate_item_and_components_only_within_the_same_pool() {
        ItemStack named = stack();
        named.set(DataComponents.CUSTOM_NAME, Component.literal("distinct"));
        List<JeiWorkstationRegistration> normalized = RecipeViewerRegistration.normalizeWorkstations(List.of(
                new JeiWorkstationRegistration.RecipePoolStack(POOL, stack()),
                new JeiWorkstationRegistration.RecipePoolStack(POOL, stack().copyWithCount(16)),
                new JeiWorkstationRegistration.RecipePoolStack(POOL, named),
                new JeiWorkstationRegistration.RecipePoolStack(MMCR.id("other_pool"), stack())));

        assertThat(normalized).hasSize(3).allSatisfy(entry ->
                assertThat(((JeiWorkstationRegistration.RecipePoolStack) entry).workstation().getCount()).isEqualTo(1));
    }

    @Test
    void jei_direct_catalyst_callbacks_reuse_the_cycle_without_reposting_events() throws Exception {
        AtomicInteger informationEvents = new AtomicInteger();
        AtomicInteger workstationEvents = new AtomicInteger();
        RecipeViewerRegistration coordinator = coordinator(new ArrayList<>(), new AtomicBoolean(true),
                ignored -> informationEvents.incrementAndGet(), ignored -> workstationEvents.incrementAndGet());
        var constructor = JeiPlugin.class.getDeclaredConstructor(Supplier.class);
        constructor.setAccessible(true);
        JeiPlugin plugin = constructor.newInstance((Supplier<RecipeViewerSnapshot>) () -> coordinator.capture().join());
        var catalysts = (IRecipeCatalystRegistration) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[]{IRecipeCatalystRegistration.class}, (proxy, method, arguments) -> {
                    throw new AssertionError("Empty snapshot should not register catalysts: " + method.getName());
                });

        plugin.registerRecipeCatalysts(catalysts);
        plugin.registerRecipeCatalysts(catalysts);

        assertThat(informationEvents).hasValue(1);
        assertThat(workstationEvents).hasValue(1);
    }

    @Test
    void jei_categories_start_a_new_cycle_and_catalysts_consume_that_same_cycle() throws Exception {
        AtomicInteger informationEvents = new AtomicInteger();
        AtomicInteger workstationEvents = new AtomicInteger();
        AtomicInteger categories = new AtomicInteger();
        RecipeViewerRegistration coordinator = coordinator(new ArrayList<>(), new AtomicBoolean(true),
                ignored -> informationEvents.incrementAndGet(), ignored -> workstationEvents.incrementAndGet());
        var constructor = JeiPlugin.class.getDeclaredConstructor(Supplier.class);
        constructor.setAccessible(true);
        JeiPlugin plugin = constructor.newInstance((Supplier<RecipeViewerSnapshot>) () -> coordinator.capture().join());
        var gui = (IGuiHelper) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{IGuiHelper.class},
                (proxy, method, arguments) -> {
                    if (method.getName().equals("createDrawableItemLike")) return null;
                    throw new AssertionError(method.getName());
                });
        var helpers = (IJeiHelpers) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{IJeiHelpers.class},
                (proxy, method, arguments) -> switch (method.getName()) {
                    case "getGuiHelper" -> gui;
                    case "getIngredientManager" -> null;
                    default -> throw new AssertionError(method.getName());
                });
        var categoryRegistration = (IRecipeCategoryRegistration) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[]{IRecipeCategoryRegistration.class}, (proxy, method, arguments) -> {
                    if (method.getName().equals("getJeiHelpers")) return helpers;
                    if (method.getName().equals("addRecipeCategories")) {
                        categories.incrementAndGet();
                        return null;
                    }
                    throw new AssertionError(method.getName());
                });
        var catalysts = (IRecipeCatalystRegistration) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[]{IRecipeCatalystRegistration.class}, (proxy, method, arguments) -> {
                    throw new AssertionError(method.getName());
                });

        plugin.registerCategories(categoryRegistration);
        plugin.registerRecipeCatalysts(catalysts);
        assertThat(informationEvents).hasValue(1);
        assertThat(workstationEvents).hasValue(1);
        plugin.registerCategories(categoryRegistration);
        plugin.registerRecipeCatalysts(catalysts);
        assertThat(informationEvents).hasValue(2);
        assertThat(workstationEvents).hasValue(2);
        assertThat(categories).hasValue(2);
    }

    private static RecipeViewerRegistration coordinator(List<Runnable> queued, AtomicBoolean client,
            Consumer<RegisterJeiRecipeInformationEvent> information, Consumer<RegisterJeiWorkstationsEvent> workstations) {
        return new RecipeViewerRegistration(queued::add, client::get, information, workstations,
                RecipeViewerRegistrationTest::emptyContent, () -> 7L);
    }

    private static RecipeViewerRegistration.Content emptyContent(List<JeiWorkstationRegistration> entries) {
        return new RecipeViewerRegistration.Content(Map.of(), List.of(), Map.of(), entries);
    }

    private static ItemStack stack() { return new ItemStack(Holder.direct(Items.IRON_INGOT), 1); }
}

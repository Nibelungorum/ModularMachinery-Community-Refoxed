package cn.howxu.mmcr.internal.client;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.jei.JeiWorkstationRegistration;
import cn.howxu.mmcr.api.machine.MachineDefinitions;
import cn.howxu.mmcr.api.machine.MachineRegistry;
import cn.howxu.mmcr.api.recipe.RecipeRegistry;
import cn.howxu.mmcr.client.RuntimeContentClientApplier;
import cn.howxu.mmcr.client.recipe.MachineRecipeData;
import cn.howxu.mmcr.compat.jei.MachineStructureDisplay;
import cn.howxu.mmcr.internal.api.facade.client.ClientRegistrationAdapters;
import cn.howxu.mmcr.publicapi.event.RegisterJeiRecipeInformationEvent;
import cn.howxu.mmcr.publicapi.event.RegisterJeiWorkstationsEvent;
import cn.howxu.mmcr.registry.ModBlocks;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.NeoForge;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.LongSupplier;

/** Collects viewer events on the client thread and publishes complete registration cycles.
 * @author howxu <dev@howxu.cn>
 */
public final class RecipeViewerRegistration {
    private static final RecipeViewerRegistration INSTANCE = new RecipeViewerRegistration(
            task -> Minecraft.getInstance().execute(task), () -> Minecraft.getInstance().isSameThread(),
            event -> NeoForge.EVENT_BUS.post(event), event -> NeoForge.EVENT_BUS.post(event),
            RecipeViewerRegistration::collectContent, RuntimeContentClientApplier::appliedContentVersion);

    private final Object lock = new Object();
    private final Executor executor;
    private final BooleanSupplier onClientThread;
    private final Consumer<RegisterJeiRecipeInformationEvent> informationCollector;
    private final Consumer<RegisterJeiWorkstationsEvent> workstationCollector;
    private final Function<List<JeiWorkstationRegistration>, Content> contentCollector;
    private final LongSupplier contentVersion;
    private final Set<CompletableFuture<RecipeViewerSnapshot>> pending = new LinkedHashSet<>();
    private long session;
    private long information;
    private long registration;

    record Content(Map<ResourceLocation, List<MachineRecipeData>> recipes,
                   List<MachineStructureDisplay> structures,
                   Map<ResourceLocation, List<ResourceLocation>> machines,
                   List<JeiWorkstationRegistration> workstations) { }

    RecipeViewerRegistration(Executor executor, BooleanSupplier onClientThread,
            Consumer<RegisterJeiRecipeInformationEvent> informationCollector,
            Consumer<RegisterJeiWorkstationsEvent> workstationCollector,
            Function<List<JeiWorkstationRegistration>, Content> contentCollector, LongSupplier contentVersion) {
        this.executor = executor;
        this.onClientThread = onClientThread;
        this.informationCollector = informationCollector;
        this.workstationCollector = workstationCollector;
        this.contentCollector = contentCollector;
        this.contentVersion = contentVersion;
    }

    public static CompletableFuture<RecipeViewerSnapshot> captureAsync() { return INSTANCE.capture(); }
    public static CompletableFuture<RecipeViewerSnapshot> captureAsync(long expectedSession) {
        return INSTANCE.capture(expectedSession);
    }
    public static void resetForConnection() { INSTANCE.reset(); }
    public static void informationChanged() { INSTANCE.changeInformation(); }
    public static long informationVersion() {
        synchronized (INSTANCE.lock) { return INSTANCE.information; }
    }

    /** Reads counters only; unlike captureAsync this never collects events or resolves world data. */
    public static RecipeViewerSnapshot.Version currentVersion() {
        synchronized (INSTANCE.lock) {
            return new RecipeViewerSnapshot.Version(INSTANCE.session, INSTANCE.contentVersion.getAsLong(),
                    INSTANCE.information, INSTANCE.registration);
        }
    }

    CompletableFuture<RecipeViewerSnapshot> capture() {
        long capturedSession;
        synchronized (lock) { capturedSession = session; }
        return capture(capturedSession);
    }

    CompletableFuture<RecipeViewerSnapshot> capture(long capturedSession) {
        CompletableFuture<RecipeViewerSnapshot> future = new CompletableFuture<>();
        synchronized (lock) {
            if (capturedSession != session) {
                future.cancel(false);
                return future;
            }
            pending.add(future);
        }
        future.whenComplete((ignored, failure) -> {
            synchronized (lock) { pending.remove(future); }
        });
        Runnable collect = () -> {
            try {
                checkSession(capturedSession, future);
                future.complete(captureOnClientThread(capturedSession, future));
            } catch (RuntimeException | Error failure) {
                future.completeExceptionally(failure);
            }
        };
        try {
            if (onClientThread.getAsBoolean()) collect.run();
            else executor.execute(collect);
        } catch (RuntimeException | Error failure) {
            future.completeExceptionally(failure);
        }
        return future;
    }

    private RecipeViewerSnapshot captureOnClientThread(long capturedSession,
                                                       CompletableFuture<RecipeViewerSnapshot> future) {
        RegisterJeiRecipeInformationEvent informationEvent = new RegisterJeiRecipeInformationEvent();
        informationCollector.accept(informationEvent);
        ClientRegistrationAdapters.freeze(informationEvent);
        checkSession(capturedSession, future);
        RegisterJeiWorkstationsEvent workstationEvent = new RegisterJeiWorkstationsEvent();
        workstationCollector.accept(workstationEvent);
        ClientRegistrationAdapters.freeze(workstationEvent);
        checkSession(capturedSession, future);

        var publicInformation = ClientRegistrationAdapters.coreEntries(informationEvent);
        var kubeJSInformation = RecipeInformationRegistry.kubeJSEntries();
        List<JeiWorkstationRegistration> workstations = new ArrayList<>(ClientRegistrationAdapters.coreEntries(workstationEvent));
        workstations.addAll(JeiWorkstationRegistry.kubeJSEntries());
        Content content = RecipeInformationRegistry.collecting(publicInformation, kubeJSInformation,
                () -> contentCollector.apply(List.copyOf(workstations)));
        synchronized (lock) {
            checkSession(capturedSession, future);
            RecipeViewerSnapshot snapshot = new RecipeViewerSnapshot(new RecipeViewerSnapshot.Version(
                    session, contentVersion.getAsLong(), information, registration + 1), content.recipes(),
                    content.structures(), content.machines(), normalizeWorkstations(content.workstations()),
                    publicInformation, kubeJSInformation);
            RecipeInformationRegistry.commit(publicInformation, snapshot);
            registration++;
            return snapshot;
        }
    }

    private void checkSession(long capturedSession, CompletableFuture<?> future) {
        synchronized (lock) {
            if (capturedSession != session || future.isDone()) {
                throw new CancellationException("Recipe viewer registration session ended");
            }
        }
    }

    void reset() {
        List<CompletableFuture<RecipeViewerSnapshot>> cancelled;
        synchronized (lock) {
            session++;
            registration = 0;
            cancelled = List.copyOf(pending);
            RecipeInformationRegistry.resetPublic();
        }
        cancelled.forEach(future -> future.cancel(false));
    }

    void changeInformation() {
        synchronized (lock) { information++; }
    }

    public static Set<ResourceLocation> machineIds() {
        Set<ResourceLocation> ids = new LinkedHashSet<>(MachineRegistry.getAll().keySet());
        ids.addAll(MachineDefinitions.effectiveSnapshot().keySet());
        return Collections.unmodifiableSet(ids);
    }

    public static Map<ResourceLocation, List<ResourceLocation>> machineIdsByPool() {
        Map<ResourceLocation, List<ResourceLocation>> result = new LinkedHashMap<>();
        machineIds().stream().sorted().forEach(machineId -> MachineRegistry.recipePoolsForMachine(machineId)
                .forEach(poolId -> result.computeIfAbsent(poolId, ignored -> new ArrayList<>()).add(machineId)));
        result.replaceAll((ignored, ids) -> List.copyOf(ids));
        return Collections.unmodifiableMap(result);
    }

    private static Content collectContent(List<JeiWorkstationRegistration> manualWorkstations) {
        Minecraft minecraft = Minecraft.getInstance();
        var registries = minecraft.level == null ? null : minecraft.level.registryAccess();
        Map<ResourceLocation, List<MachineRecipeData>> recipes = new LinkedHashMap<>();
        RecipeRegistry.recipes().stream().map(recipe -> MachineRecipeData.from(recipe, registries))
                .sorted(MachineRecipeData.ORDER).forEach(data -> recipes
                        .computeIfAbsent(data.recipePoolId(), ignored -> new ArrayList<>()).add(data));
        List<MachineStructureDisplay> structures = MachineRegistry.getAll().values().stream()
                .map(MachineStructureDisplay::from).toList();
        structures.forEach(display -> {
            display.materials();
            display.defaultSchema().previewCandidates();
        });
        Map<ResourceLocation, List<ResourceLocation>> machines = machineIdsByPool();
        List<JeiWorkstationRegistration> workstations = new ArrayList<>();
        machines.forEach((pool, ids) -> ids.stream().filter(ModBlocks::hasControllerFor).forEach(id ->
                workstations.add(new JeiWorkstationRegistration.RecipePoolStack(pool,
                        new ItemStack(ModBlocks.controllerFor(id).get())))));
        manualWorkstations.forEach(entry -> {
            switch (entry) {
                case JeiWorkstationRegistration.RecipePoolItem item -> {
                    if (!machines.containsKey(item.recipePoolId())) {
                        MMCR.LOG.warn("Skipping recipe viewer workstation for unknown pool {}", item.recipePoolId());
                    } else if (!BuiltInRegistries.ITEM.containsKey(item.itemId())) {
                        MMCR.LOG.warn("Skipping unknown recipe viewer workstation item {}", item.itemId());
                    } else {
                        ItemStack stack = new ItemStack(BuiltInRegistries.ITEM.get(item.itemId()));
                        if (!stack.isEmpty()) workstations.add(new JeiWorkstationRegistration.RecipePoolStack(item.recipePoolId(), stack));
                    }
                }
                case JeiWorkstationRegistration.RecipePoolStack stack -> {
                    if (machines.containsKey(stack.recipePoolId())) workstations.add(stack);
                    else MMCR.LOG.warn("Skipping recipe viewer workstation for unknown pool {}", stack.recipePoolId());
                }
                case JeiWorkstationRegistration.Machine machine -> {
                    if (ModBlocks.hasControllerFor(machine.machineId())) workstations.add(machine);
                    else MMCR.LOG.warn("Skipping recipe viewer workstation for unknown machine {}", machine.machineId());
                }
            }
        });
        return new Content(recipes, structures, machines, workstations);
    }

    static List<JeiWorkstationRegistration> normalizeWorkstations(List<JeiWorkstationRegistration> entries) {
        List<JeiWorkstationRegistration> result = new ArrayList<>();
        for (JeiWorkstationRegistration entry : entries) {
            boolean duplicate = result.stream().anyMatch(existing -> {
                if (existing instanceof JeiWorkstationRegistration.RecipePoolStack left
                        && entry instanceof JeiWorkstationRegistration.RecipePoolStack right) {
                    return left.recipePoolId().equals(right.recipePoolId())
                            && ItemStack.isSameItemSameComponents(left.workstation(), right.workstation());
                }
                return existing.equals(entry);
            });
            if (!duplicate) result.add(entry);
        }
        return List.copyOf(result);
    }
}

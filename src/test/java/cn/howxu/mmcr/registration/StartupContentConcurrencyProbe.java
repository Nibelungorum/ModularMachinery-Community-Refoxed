package cn.howxu.mmcr.registration;

import cn.howxu.mmcr.api.machine.MachineControllerSpec;
import cn.howxu.mmcr.api.machine.definition.MachineDefinition;
import cn.howxu.mmcr.api.port.PortDefinition;
import cn.howxu.mmcr.internal.api.PublicApiBootstrap;
import cn.howxu.mmcr.internal.port.IOPortKind;
import cn.howxu.mmcr.internal.registration.StartupContentRegistration;
import cn.howxu.mmcr.registry.ModBlockEntities;
import cn.howxu.mmcr.registry.ModBlocks;
import cn.howxu.mmcr.registry.ModItems;
import cn.howxu.mmcr.registry.PortKinds;
import cn.howxu.mmcr.registry.RegistrationLock;
import cn.howxu.mmcr.util.IOType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.common.NeoForge;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.util.AbstractSet;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Child-process fixture exercising the real registry initializers and startup registration path.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class StartupContentConcurrencyProbe {
    private StartupContentConcurrencyProbe() {
    }

    public static void main(String[] arguments) throws Exception {
        try {
            bootstrapMinecraft();
            switch (arguments[0]) {
                case "items-first" -> itemsInitializeBeforeScriptRegistration();
                case "scripts-first" -> scriptsInitializeBeforeItems();
                case "concurrent" -> concurrentRegistrationsPreserveContent();
                default -> throw new IllegalArgumentException(arguments[0]);
            }
        } catch (Throwable failure) {
            for (ThreadInfo info : ManagementFactory.getThreadMXBean().dumpAllThreads(true, true)) {
                System.err.println(info);
                for (StackTraceElement frame : info.getStackTrace()) System.err.println("    at " + frame);
            }
            throw failure;
        }
    }

    private static void itemsInitializeBeforeScriptRegistration() throws Exception {
        // Blocks also initialize their entity holders; leave only ModItems cold.
        ModBlocks.REGISTER.getRegistryKey();
        MachineDefinition machine = machine("items_first");
        Worker items;
        Worker script;
        synchronized (RegistrationLock.LOCK) {
            items = start("items-initializer", () -> {
                Class.forName("cn.howxu.mmcr.registry.ModItems");
                return null;
            });
            awaitMutexWait(items);
            script = start("script-registrant", () -> {
                StartupContentRegistration.registerKubeJSStartupMachine(machine);
                return null;
            });
            // A lock-before-initialize implementation would block on LOCK here instead.
            ThreadInfo info = awaitFrame(script, StartupContentRegistration.class.getName(), "registerDynamicControllers");
            assertThat(info.getThreadState()).isEqualTo(Thread.State.RUNNABLE);
        }
        items.await();
        script.await();
        assertRegistered(machine.id());
    }

    private static void scriptsInitializeBeforeItems() throws Exception {
        CountDownLatch blocksInitializing = new CountDownLatch(1);
        CountDownLatch releaseBlocks = new CountDownLatch(1);
        PortKinds.register(new GatedPortKind(blocksInitializing, releaseBlocks));
        MachineDefinition machine = machine("scripts_first");
        Worker script = start("script-registrant", () -> {
            StartupContentRegistration.registerKubeJSStartupMachine(machine);
            return null;
        });
        Worker items;
        try {
            await(blocksInitializing);
            items = start("items-initializer", () -> {
                Class.forName("cn.howxu.mmcr.registry.ModItems");
                return null;
            });
            awaitFrame(items, ModItems.class.getName(), "<clinit>");
            // Neither a registrant nor ModItems may hold LOCK while awaiting ModBlocks initialization.
            start("mutex-observer", () -> {
                synchronized (RegistrationLock.LOCK) {
                    return null;
                }
            }).await();
        } finally {
            releaseBlocks.countDown();
        }
        script.await();
        items.await();
        assertRegistered(machine.id());
    }

    private static void concurrentRegistrationsPreserveContent() throws Exception {
        Class.forName("cn.howxu.mmcr.registry.ModItems");
        MachineDefinition first = machine("concurrent_first");
        Worker registrant;
        synchronized (RegistrationLock.LOCK) {
            var iterator = ModBlocks.BLOCKS.entrySet().iterator();
            iterator.next();
            registrant = start("script-registrant", () -> {
                StartupContentRegistration.registerKubeJSStartupMachine(first);
                return null;
            });
            awaitMutexWait(registrant);
            while (iterator.hasNext()) iterator.next();
        }
        registrant.await();
        assertRegistered(first.id());

        // Keep the first registration inside its first step while a second machine arrives.
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        ResourceLocation second = machine("concurrent_second").id();
        Set<ResourceLocation> batch = new AbstractSet<>() {
            @Override
            public Iterator<ResourceLocation> iterator() {
                return Set.of(second).iterator();
            }

            @Override
            public int size() {
                return 1;
            }

            @Override
            public void forEach(Consumer<? super ResourceLocation> action) {
                entered.countDown();
                await(release);
                action.accept(second);
            }
        };
        Worker firstBatch = start("first-batch", () -> {
            registerBatch(batch);
            return null;
        });
        MachineDefinition third = machine("concurrent_third");
        Worker secondBatch;
        try {
            await(entered);
            secondBatch = start("second-batch", () -> {
                StartupContentRegistration.registerKubeJSStartupMachine(third);
                return null;
            });
            awaitMutexWait(secondBatch);
        } finally {
            release.countDown();
        }
        firstBatch.await();
        secondBatch.await();
        assertRegistered(second);
        assertRegistered(third.id());

        String name = MachineControllerSpec.defaultsFor(second).id().getPath();
        var block = ModBlocks.BLOCKS.get(name);
        var item = ModItems.ITEMS.get(name);
        var entity = ModBlockEntities.BES.get(name);
        var blockEntries = List.copyOf(ModBlocks.REGISTER.getEntries());
        var itemEntries = List.copyOf(ModItems.REGISTER.getEntries());
        var entityEntries = List.copyOf(ModBlockEntities.REGISTER.getEntries());
        // Test helpers can call individual registry methods without the startup coordinator.
        for (Consumer<Collection<ResourceLocation>> registration : List.<Consumer<Collection<ResourceLocation>>>of(
                ModBlocks::registerMachineControllers, ModBlockEntities::registerMachineControllers,
                ModItems::registerMachineControllerItems)) {
            Worker direct;
            synchronized (RegistrationLock.LOCK) {
                direct = start("direct-registrant", () -> {
                    registration.accept(Set.of(second));
                    return null;
                });
                awaitMutexWait(direct);
            }
            direct.await();
        }
        Worker duplicateA = start("duplicate-a", () -> {
            registerBatch(Set.of(second));
            return null;
        });
        Worker duplicateB = start("duplicate-b", () -> {
            registerBatch(Set.of(second));
            return null;
        });
        duplicateA.await();
        duplicateB.await();
        assertThat(ModBlocks.BLOCKS.get(name)).isSameAs(block);
        assertThat(ModItems.ITEMS.get(name)).isSameAs(item);
        assertThat(ModBlockEntities.BES.get(name)).isSameAs(entity);
        assertThat(ModBlocks.REGISTER.getEntries()).containsExactlyElementsOf(blockEntries);
        assertThat(ModItems.REGISTER.getEntries()).containsExactlyElementsOf(itemEntries);
        assertThat(ModBlockEntities.REGISTER.getEntries()).containsExactlyElementsOf(entityEntries);
    }

    private static void registerBatch(Set<ResourceLocation> ids) throws Exception {
        var method = StartupContentRegistration.class.getDeclaredMethod("registerDynamicControllers", Set.class);
        method.setAccessible(true);
        method.invoke(null, ids);
    }

    private static void assertRegistered(ResourceLocation id) {
        String name = MachineControllerSpec.defaultsFor(id).id().getPath();
        assertThat(ModBlocks.BLOCKS).containsKey(name);
        assertThat(ModItems.ITEMS).containsKey(name);
        assertThat(ModBlockEntities.BES).containsKey(name);
        assertThat(ModBlocks.REGISTER.getEntries()).contains(ModBlocks.BLOCKS.get(name));
        assertThat(ModItems.REGISTER.getEntries()).contains(ModItems.ITEMS.get(name));
        assertThat(ModBlockEntities.REGISTER.getEntries()).contains(ModBlockEntities.BES.get(name));
        assertThat(ModBlockEntities.controllerMachineIds()).contains(id);
    }

    private static MachineDefinition machine(String name) {
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath("test", name);
        return new MachineDefinition(id, id, null, null, null, null, null, Set.of(), 1, false, null);
    }

    private static Worker start(String name, Callable<Void> action) {
        FutureTask<Void> task = new FutureTask<>(action);
        Thread thread = Thread.ofPlatform().daemon().name(name).unstarted(task);
        thread.start();
        return new Worker(thread, task);
    }

    private static ThreadInfo awaitFrame(Worker worker, String className, String method) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (System.nanoTime() < deadline) {
            if (worker.task().isDone()) {
                worker.await();
                throw new AssertionError("Worker completed instead of waiting: " + worker.thread().getName());
            }
            ThreadInfo info = ManagementFactory.getThreadMXBean().getThreadInfo(worker.thread().threadId(), 64);
            if (info != null) {
                for (StackTraceElement frame : info.getStackTrace()) {
                    if (frame.getClassName().equals(className) && frame.getMethodName().equals(method)) return info;
                }
            }
            Thread.yield();
        }
        throw new AssertionError("Worker did not reach " + className + "." + method);
    }

    private static void awaitMutexWait(Worker worker) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (System.nanoTime() < deadline) {
            if (worker.task().isDone()) {
                worker.await();
                throw new AssertionError("Registry mutation did not wait for the mutex");
            }
            ThreadInfo info = ManagementFactory.getThreadMXBean().getThreadInfo(worker.thread().threadId());
            if (info != null && info.getThreadState() == Thread.State.BLOCKED
                    && info.getLockInfo().getIdentityHashCode() == System.identityHashCode(RegistrationLock.LOCK)) return;
            Thread.yield();
        }
        throw new AssertionError("Worker did not wait for the registration mutex");
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(10, TimeUnit.SECONDS)) throw new AssertionError("Latch timed out");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AssertionError(exception);
        }
    }

    private static void bootstrapMinecraft() throws Exception {
        // Do not use TestBootstrap: it initializes all three MMCR registries before returning.
        Class<?> loaderType = Class.forName("net.neoforged.fml.loading.FMLLoader");
        Class<?> modListType = Class.forName("net.neoforged.fml.loading.LoadingModList");
        loaderType.getConstructor().newInstance();
        Object emptyModList = modListType.getMethod("of", List.class, List.class, List.class,
                List.class, Map.class).invoke(null, List.of(), List.of(), List.of(), List.of(), Map.of());
        var modList = loaderType.getDeclaredField("loadingModList");
        modList.setAccessible(true);
        modList.set(null, emptyModList);
        Class.forName("net.minecraft.SharedConstants").getMethod("tryDetectVersion").invoke(null);
        NeoForge.EVENT_BUS.start();
        Bootstrap.bootStrap();
        PublicApiBootstrap.begin();
    }

    /** @author howxu <dev@howxu.cn> */
    private record Worker(Thread thread, FutureTask<Void> task) {
        void await() throws Exception {
            task.get(10, TimeUnit.SECONDS);
        }
    }

    /** @author howxu <dev@howxu.cn> */
    private record GatedPortKind(CountDownLatch entered, CountDownLatch release) implements IOPortKind {
        @Override
        public String id() {
            entered.countDown();
            await(release);
            return "cold_initialization_probe";
        }

        @Override
        public IOType ioType() {
            return IOType.INPUT;
        }

        @Override
        public BlockEntityType.BlockEntitySupplier<? extends BlockEntity> entityFactory() {
            return PortKinds.ITEM_INPUT.entityFactory();
        }

        @Override
        public PortDefinition definition() {
            return PortDefinition.of(ResourceLocation.parse("test:cold_initialization_probe"),
                    PortKinds.ITEM_INPUT.bindings());
        }
    }
}

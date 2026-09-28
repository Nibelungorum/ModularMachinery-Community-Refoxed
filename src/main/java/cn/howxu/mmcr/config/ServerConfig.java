package cn.howxu.mmcr.config;

import cn.howxu.mmcr.internal.assembly.MultiblockAssemblyService;
import cn.howxu.mmcr.internal.runtime.MachineWorkMode;
import net.neoforged.neoforge.common.ModConfigSpec;

/** Defines server-authoritative MMCR configuration values.
 * @author howxu <dev@howxu.cn>
 */
public final class ServerConfig {
    public static final int DEFAULT_MACHINE_CHECK_INTERVAL_TICKS = 40;
    public static final int DEFAULT_TERMINAL_MAX_DEMOLISH_BLOCKS = MultiblockAssemblyService.MAX_BLOCKS_PER_OPERATION;
    public static final int DEFAULT_BUILD_BLOCKS_PER_TICK = 256;
    public static final int DEFAULT_BUILD_TASK_TIMEOUT_TICKS = 20 * 60;
    public static final int DEFAULT_STRUCTURE_SCAN_BATCHES = 10;
    public static final int DEFAULT_STRUCTURE_SENTINEL_COUNT = 16;
    public static final int DEFAULT_STRUCTURE_SYNC_MAX_BLOCKS = 524_288;
    public static final int DEFAULT_MAX_REQUESTS_PER_TICK = 1024;
    public static final int DEFAULT_STRUCTURE_SAFETY_CHECK_INTERVAL_TICKS = 120;
    public static final int DEFAULT_STRUCTURE_SAFETY_SCAN_BATCHES = 40;
    public static final int DEFAULT_MODULE_FALLBACK_SCAN_INTERVAL_TICKS = 60;
    public static final int DEFAULT_LINK_APPEARANCE_CHECK_INTERVAL_TICKS = 40;
    public static final int DEFAULT_LINK_DATA_STORAGE_CHECK_INTERVAL_TICKS = 40;
    public static final int DEFAULT_LINK_NETWORK_INTERFACE_HEARTBEAT_INTERVAL_TICKS = 40;
    public static final int DEFAULT_LINK_SMART_INTERFACE_CHECK_INTERVAL_TICKS = 20;
    public static final int DEFAULT_AUTO_IO_MIN_DELAY_TICKS = 5;
    public static final int DEFAULT_AUTO_IO_MAX_DELAY_TICKS = 60;
    public static final int DEFAULT_AUTO_IO_SUCCESS_DELAY_STEP_TICKS = 5;
    public static final int DEFAULT_ASSEMBLY_MAX_BLOCKS_PER_OPERATION = MultiblockAssemblyService.MAX_BLOCKS_PER_OPERATION;
    public static final int DEFAULT_ASYNC_WORKER_COUNT = Math.min(Math.max(Runtime.getRuntime().availableProcessors() / 4, 4), 8);
    public static final int DEFAULT_ASYNC_WORKER_PROGRESS_WAIT_MS = 10;
    public static final int DEFAULT_ASYNC_WORKER_QUEUE_CAPACITY = Math.max(256, DEFAULT_ASYNC_WORKER_COUNT * 64);
    public static final int DEFAULT_ASYNC_MAIN_THREAD_STEPS_PER_LEVEL_TICK = 1_024;
    public static final int DEFAULT_ASYNC_SHARED_IO_REQUESTS_PER_LEVEL_TICK = 4_096;
    public static final int DEFAULT_ASYNC_SHARED_IO_WORKSET_ENTRIES = 256;
    public static final int DEFAULT_FACTORY_IDLE_TIMEOUT_TICKS = 200;
    public static final int DEFAULT_TERMINAL_CONTROLLER_ACCESS_RADIUS = 96;
    public static final ModConfigSpec.IntValue MACHINE_CHECK_INTERVAL_TICKS;
    public static final ModConfigSpec.IntValue TERMINAL_MAX_DEMOLISH_BLOCKS;
    public static final ModConfigSpec.IntValue BUILD_BLOCKS_PER_TICK;
    public static final ModConfigSpec.IntValue BUILD_TASK_TIMEOUT_TICKS;
    public static final ModConfigSpec.IntValue STRUCTURE_SCAN_BATCHES;
    public static final ModConfigSpec.IntValue STRUCTURE_SENTINEL_COUNT;
    public static final ModConfigSpec.BooleanValue STRUCTURE_SENTINEL_ENABLED;
    public static final ModConfigSpec.IntValue STRUCTURE_SYNC_MAX_BLOCKS;
    public static final ModConfigSpec.DoubleValue ENERGY_CONSUMPTION_MULTIPLIER;
    public static final ModConfigSpec.IntValue MAX_REQUESTS_PER_TICK;
    public static final ModConfigSpec.IntValue STRUCTURE_SAFETY_CHECK_INTERVAL_TICKS;
    public static final ModConfigSpec.IntValue STRUCTURE_SAFETY_SCAN_BATCHES;
    public static final ModConfigSpec.IntValue MODULE_FALLBACK_SCAN_INTERVAL_TICKS;
    public static final ModConfigSpec.IntValue LINK_APPEARANCE_CHECK_INTERVAL_TICKS;
    public static final ModConfigSpec.IntValue LINK_DATA_STORAGE_CHECK_INTERVAL_TICKS;
    public static final ModConfigSpec.IntValue LINK_NETWORK_INTERFACE_HEARTBEAT_INTERVAL_TICKS;
    public static final ModConfigSpec.IntValue LINK_SMART_INTERFACE_CHECK_INTERVAL_TICKS;
    public static final ModConfigSpec.IntValue AUTO_IO_MIN_DELAY_TICKS;
    public static final ModConfigSpec.IntValue AUTO_IO_MAX_DELAY_TICKS;
    public static final ModConfigSpec.IntValue AUTO_IO_SUCCESS_DELAY_STEP_TICKS;
    public static final ModConfigSpec.IntValue ASSEMBLY_MAX_BLOCKS_PER_OPERATION;
    public static final ModConfigSpec.EnumValue<MachineWorkMode> MACHINE_WORK_MODE;
    public static final ModConfigSpec.IntValue ASYNC_WORKER_COUNT;
    public static final ModConfigSpec.IntValue ASYNC_WORKER_PROGRESS_WAIT_MS;
    public static final ModConfigSpec.IntValue ASYNC_WORKER_QUEUE_CAPACITY;
    public static final ModConfigSpec.IntValue ASYNC_MAIN_THREAD_STEPS_PER_LEVEL_TICK;
    public static final ModConfigSpec.IntValue ASYNC_SHARED_IO_REQUESTS_PER_LEVEL_TICK;
    public static final ModConfigSpec.IntValue ASYNC_SHARED_IO_WORKSET_ENTRIES;
    public static final ModConfigSpec.IntValue FACTORY_IDLE_TIMEOUT_TICKS;
    public static final ModConfigSpec.IntValue TERMINAL_CONTROLLER_ACCESS_RADIUS;
    public static final ModConfigSpec SPEC;

    static {
        var builder = new ModConfigSpec.Builder();

        builder.translation("mmcr.configuration.server.machine").push("machine");
        MACHINE_WORK_MODE = builder
                .translation("mmcr.configuration.server.machine.work_mode")
                .comment("Machine execution mode")
                .worldRestart()
                .defineEnum("work_mode", MachineWorkMode.ASYNC);
        builder.pop();

        builder.translation("mmcr.configuration.server.terminal").push("terminal");
        TERMINAL_MAX_DEMOLISH_BLOCKS = builder
                .translation("mmcr.configuration.server.terminal.max_demolish_blocks")
                .comment("Maximum blocks removed by one terminal demolish operation")
                .defineInRange("max_demolish_blocks", DEFAULT_TERMINAL_MAX_DEMOLISH_BLOCKS, 1, 1_000_000);
        TERMINAL_CONTROLLER_ACCESS_RADIUS = builder
                .translation("mmcr.configuration.server.terminal.controller_access_radius")
                .comment("Maximum distance from a bound controller at which a terminal may operate")
                .defineInRange("controller_access_radius", DEFAULT_TERMINAL_CONTROLLER_ACCESS_RADIUS, 1, 512);
        builder.pop();

        builder.translation("mmcr.configuration.server.async").push("async");
        ASYNC_WORKER_COUNT = builder
                .translation("mmcr.configuration.server.async.worker_count")
                .comment("Worker threads used for asynchronous machine execution; requires restarting the server process")
                .worldRestart()
                .defineInRange("worker_count", DEFAULT_ASYNC_WORKER_COUNT, 1, 256);
        ASYNC_WORKER_PROGRESS_WAIT_MS = builder
                .translation("mmcr.configuration.server.async.worker_progress_wait_ms")
                .comment("Milliseconds to wait for asynchronous worker progress before another fence pass")
                .worldRestart()
                .defineInRange("worker_progress_wait_ms", DEFAULT_ASYNC_WORKER_PROGRESS_WAIT_MS, 1, 1_000);
        ASYNC_WORKER_QUEUE_CAPACITY = builder
                .translation("mmcr.configuration.server.async.worker_queue_capacity")
                .comment("Maximum queued asynchronous worker segments; requires restarting the server process")
                .worldRestart()
                .defineInRange("worker_queue_capacity", DEFAULT_ASYNC_WORKER_QUEUE_CAPACITY, 1, 1_000_000);
        ASYNC_MAIN_THREAD_STEPS_PER_LEVEL_TICK = builder
                .translation("mmcr.configuration.server.async.main_thread_steps_per_level_tick")
                .comment("Maximum asynchronous main-thread logic units processed per level tick")
                .defineInRange("main_thread_steps_per_level_tick", DEFAULT_ASYNC_MAIN_THREAD_STEPS_PER_LEVEL_TICK,
                        1, 1_000_000);
        ASYNC_SHARED_IO_REQUESTS_PER_LEVEL_TICK = builder
                .translation("mmcr.configuration.server.async.shared_io_requests_per_level_tick")
                .comment("Maximum shared IO requests processed per level tick")
                .defineInRange("shared_io_requests_per_level_tick", DEFAULT_ASYNC_SHARED_IO_REQUESTS_PER_LEVEL_TICK,
                        1, 1_000_000);
        ASYNC_SHARED_IO_WORKSET_ENTRIES = builder
                .translation("mmcr.configuration.server.async.shared_io_workset_entries")
                .comment("Maximum entries submitted in one shared IO workset")
                .defineInRange("shared_io_workset_entries", DEFAULT_ASYNC_SHARED_IO_WORKSET_ENTRIES, 1, 1_000_000);
        builder.pop();

        builder.translation("mmcr.configuration.server.factory").push("factory");
        FACTORY_IDLE_TIMEOUT_TICKS = builder
                .translation("mmcr.configuration.server.factory.idle_timeout_ticks")
                .comment("Ticks an idle non-core factory thread is kept before it is released")
                .defineInRange("idle_timeout_ticks", DEFAULT_FACTORY_IDLE_TIMEOUT_TICKS, 1, 1_000_000);
        builder.pop();

        builder.translation("mmcr.configuration.server.build").push("build");
        BUILD_BLOCKS_PER_TICK = builder
                .translation("mmcr.configuration.server.build.blocks_per_tick")
                .comment("Maximum structure blocks placed by one controller per tick")
                .defineInRange("blocks_per_tick", DEFAULT_BUILD_BLOCKS_PER_TICK, 1, 1_000_000);
        BUILD_TASK_TIMEOUT_TICKS = builder
                .translation("mmcr.configuration.server.build.task_timeout_ticks")
                .comment("Maximum age of a pending structure build task")
                .defineInRange("task_timeout_ticks", DEFAULT_BUILD_TASK_TIMEOUT_TICKS, 1, 1_000_000);
        builder.pop();

        builder.translation("mmcr.configuration.server.structure").push("structure");
        MACHINE_CHECK_INTERVAL_TICKS = builder
                .translation("mmcr.configuration.server.structure.check_interval_ticks")
                .comment("Ticks between controller structure-check passes")
                .defineInRange("check_interval_ticks", DEFAULT_MACHINE_CHECK_INTERVAL_TICKS, 1, 600);
        STRUCTURE_SCAN_BATCHES = builder
                .translation("mmcr.configuration.server.structure.scan_batches")
                .comment("Number of batches used to scan a structure across server ticks")
                .defineInRange("scan_batches", DEFAULT_STRUCTURE_SCAN_BATCHES, 1, 32);
        STRUCTURE_SENTINEL_COUNT = builder
                .translation("mmcr.configuration.server.structure.sentinel_count")
                .comment("Number of deterministic structure entries checked before each scan batch")
                .defineInRange("sentinel_count", DEFAULT_STRUCTURE_SENTINEL_COUNT, 0, 128);
        STRUCTURE_SENTINEL_ENABLED = builder
                .translation("mmcr.configuration.server.structure.sentinel_enabled")
                .comment("Whether deterministic structure sentinel checks are enabled")
                .define("sentinel_enabled", true);
        STRUCTURE_SAFETY_CHECK_INTERVAL_TICKS = builder
                .translation("mmcr.configuration.server.structure.safety_check_interval_ticks")
                .comment("Ticks between formed-structure safety checks")
                .defineInRange("safety_check_interval_ticks", DEFAULT_STRUCTURE_SAFETY_CHECK_INTERVAL_TICKS, 1, 6_000);
        STRUCTURE_SAFETY_SCAN_BATCHES = builder
                .translation("mmcr.configuration.server.structure.safety_scan_batches")
                .comment("Batches used by one formed-structure safety check")
                .defineInRange("safety_scan_batches", DEFAULT_STRUCTURE_SAFETY_SCAN_BATCHES, 1, 1_024);
        builder.pop();

        builder.translation("mmcr.configuration.server.module").push("module");
        MODULE_FALLBACK_SCAN_INTERVAL_TICKS = builder
                .translation("mmcr.configuration.server.module.fallback_scan_interval_ticks")
                .comment("Ticks between fallback scans for module coupler connections")
                .defineInRange("fallback_scan_interval_ticks", DEFAULT_MODULE_FALLBACK_SCAN_INTERVAL_TICKS, 1, 6_000);
        builder.pop();

        builder.translation("mmcr.configuration.server.link").push("link");
        LINK_APPEARANCE_CHECK_INTERVAL_TICKS = builder
                .translation("mmcr.configuration.server.link.appearance_check_interval_ticks")
                .comment("Ticks between linked appearance controller checks")
                .defineInRange("appearance_check_interval_ticks", DEFAULT_LINK_APPEARANCE_CHECK_INTERVAL_TICKS, 1, 6_000);
        LINK_DATA_STORAGE_CHECK_INTERVAL_TICKS = builder
                .translation("mmcr.configuration.server.link.data_storage_check_interval_ticks")
                .comment("Ticks between data storage controller binding checks")
                .defineInRange("data_storage_check_interval_ticks", DEFAULT_LINK_DATA_STORAGE_CHECK_INTERVAL_TICKS, 1, 6_000);
        LINK_NETWORK_INTERFACE_HEARTBEAT_INTERVAL_TICKS = builder
                .translation("mmcr.configuration.server.link.network_interface_heartbeat_interval_ticks")
                .comment("Ticks between network interface owner heartbeats")
                .defineInRange("network_interface_heartbeat_interval_ticks",
                        DEFAULT_LINK_NETWORK_INTERFACE_HEARTBEAT_INTERVAL_TICKS, 1, 6_000);
        LINK_SMART_INTERFACE_CHECK_INTERVAL_TICKS = builder
                .translation("mmcr.configuration.server.link.smart_interface_check_interval_ticks")
                .comment("Ticks between smart interface controller binding checks")
                .defineInRange("smart_interface_check_interval_ticks",
                        DEFAULT_LINK_SMART_INTERFACE_CHECK_INTERVAL_TICKS, 1, 6_000);
        builder.pop();

        builder.translation("mmcr.configuration.server.auto_io").push("auto_io");
        AUTO_IO_MIN_DELAY_TICKS = builder
                .translation("mmcr.configuration.server.auto_io.min_delay_ticks")
                .comment("Minimum ticks between automatic IO transfer attempts")
                .defineInRange("min_delay_ticks", DEFAULT_AUTO_IO_MIN_DELAY_TICKS, 1, 6_000);
        AUTO_IO_MAX_DELAY_TICKS = builder
                .translation("mmcr.configuration.server.auto_io.max_delay_ticks")
                .comment("Maximum ticks between automatic IO transfer attempts")
                .defineInRange("max_delay_ticks", DEFAULT_AUTO_IO_MAX_DELAY_TICKS, 1, 6_000);
        AUTO_IO_SUCCESS_DELAY_STEP_TICKS = builder
                .translation("mmcr.configuration.server.auto_io.success_delay_step_ticks")
                .comment("Delay reduction per successful automatic IO transfer")
                .defineInRange("success_delay_step_ticks", DEFAULT_AUTO_IO_SUCCESS_DELAY_STEP_TICKS, 1, 6_000);
        builder.pop();

        builder.translation("mmcr.configuration.server.assembly").push("assembly");
        ASSEMBLY_MAX_BLOCKS_PER_OPERATION = builder
                .translation("mmcr.configuration.server.assembly.max_blocks_per_operation")
                .comment("Maximum blocks handled by one build or demolish operation")
                .defineInRange("max_blocks_per_operation", DEFAULT_ASSEMBLY_MAX_BLOCKS_PER_OPERATION, 1, 1_000_000);
        builder.pop();

        builder.translation("mmcr.configuration.server.energy").push("energy");
        ENERGY_CONSUMPTION_MULTIPLIER = builder
                .translation("mmcr.configuration.server.energy.consumption_multiplier")
                .comment("Global multiplier on energy consumption")
                .defineInRange("consumption_multiplier", 1.0, 0.0, 100.0);
        builder.pop();

        builder.translation("mmcr.configuration.server.network").push("network");
        MAX_REQUESTS_PER_TICK = builder
                .translation("mmcr.configuration.server.network.max_requests_per_tick")
                .comment("Maximum machine network requests processed per server tick")
                .defineInRange("max_requests_per_tick", DEFAULT_MAX_REQUESTS_PER_TICK, 1, 1_000_000);
        STRUCTURE_SYNC_MAX_BLOCKS = builder
                .translation("mmcr.configuration.server.network.sync_max_blocks")
                .comment("Maximum block pattern entries synchronized for one machine structure")
                .defineInRange("sync_max_blocks", DEFAULT_STRUCTURE_SYNC_MAX_BLOCKS, 1, Integer.MAX_VALUE);
        builder.pop();

        SPEC = builder.build();
    }

    private ServerConfig() {
    }

    public static int structureSafetyCheckIntervalTicks() {
        return valueOrDefault(STRUCTURE_SAFETY_CHECK_INTERVAL_TICKS, DEFAULT_STRUCTURE_SAFETY_CHECK_INTERVAL_TICKS);
    }

    public static int structureSafetyScanBatches() {
        return valueOrDefault(STRUCTURE_SAFETY_SCAN_BATCHES, DEFAULT_STRUCTURE_SAFETY_SCAN_BATCHES);
    }

    public static int moduleFallbackScanIntervalTicks() {
        return valueOrDefault(MODULE_FALLBACK_SCAN_INTERVAL_TICKS, DEFAULT_MODULE_FALLBACK_SCAN_INTERVAL_TICKS);
    }

    public static int linkAppearanceCheckIntervalTicks() {
        return valueOrDefault(LINK_APPEARANCE_CHECK_INTERVAL_TICKS, DEFAULT_LINK_APPEARANCE_CHECK_INTERVAL_TICKS);
    }

    public static int linkDataStorageCheckIntervalTicks() {
        return valueOrDefault(LINK_DATA_STORAGE_CHECK_INTERVAL_TICKS, DEFAULT_LINK_DATA_STORAGE_CHECK_INTERVAL_TICKS);
    }

    public static int linkNetworkInterfaceHeartbeatIntervalTicks() {
        return valueOrDefault(LINK_NETWORK_INTERFACE_HEARTBEAT_INTERVAL_TICKS,
                DEFAULT_LINK_NETWORK_INTERFACE_HEARTBEAT_INTERVAL_TICKS);
    }

    public static int linkSmartInterfaceCheckIntervalTicks() {
        return valueOrDefault(LINK_SMART_INTERFACE_CHECK_INTERVAL_TICKS,
                DEFAULT_LINK_SMART_INTERFACE_CHECK_INTERVAL_TICKS);
    }

    public static int autoIoMinDelayTicks() {
        return valueOrDefault(AUTO_IO_MIN_DELAY_TICKS, DEFAULT_AUTO_IO_MIN_DELAY_TICKS);
    }

    public static int autoIoMaxDelayTicks() {
        return Math.max(autoIoMinDelayTicks(), valueOrDefault(AUTO_IO_MAX_DELAY_TICKS, DEFAULT_AUTO_IO_MAX_DELAY_TICKS));
    }

    public static int autoIoSuccessDelayStepTicks() {
        return valueOrDefault(AUTO_IO_SUCCESS_DELAY_STEP_TICKS, DEFAULT_AUTO_IO_SUCCESS_DELAY_STEP_TICKS);
    }

    public static int assemblyMaxBlocksPerOperation() {
        return valueOrDefault(ASSEMBLY_MAX_BLOCKS_PER_OPERATION, DEFAULT_ASSEMBLY_MAX_BLOCKS_PER_OPERATION);
    }

    public static MachineWorkMode machineWorkMode() {
        try {
            return MACHINE_WORK_MODE.get();
        } catch (IllegalStateException ignored) {
            return MachineWorkMode.ASYNC;
        }
    }

    public static int asyncWorkerCount() {
        return valueOrDefault(ASYNC_WORKER_COUNT, DEFAULT_ASYNC_WORKER_COUNT);
    }

    public static long asyncWorkerProgressWaitMillis() {
        return valueOrDefault(ASYNC_WORKER_PROGRESS_WAIT_MS, DEFAULT_ASYNC_WORKER_PROGRESS_WAIT_MS);
    }

    public static int asyncWorkerQueueCapacity() {
        return valueOrDefault(ASYNC_WORKER_QUEUE_CAPACITY, DEFAULT_ASYNC_WORKER_QUEUE_CAPACITY);
    }

    public static int asyncMainThreadStepsPerLevelTick() {
        return valueOrDefault(ASYNC_MAIN_THREAD_STEPS_PER_LEVEL_TICK,
                DEFAULT_ASYNC_MAIN_THREAD_STEPS_PER_LEVEL_TICK);
    }

    public static int asyncSharedIoRequestsPerLevelTick() {
        return valueOrDefault(ASYNC_SHARED_IO_REQUESTS_PER_LEVEL_TICK,
                DEFAULT_ASYNC_SHARED_IO_REQUESTS_PER_LEVEL_TICK);
    }

    public static int asyncSharedIoWorksetEntries() {
        return valueOrDefault(ASYNC_SHARED_IO_WORKSET_ENTRIES, DEFAULT_ASYNC_SHARED_IO_WORKSET_ENTRIES);
    }

    public static int factoryIdleTimeoutTicks() {
        return valueOrDefault(FACTORY_IDLE_TIMEOUT_TICKS, DEFAULT_FACTORY_IDLE_TIMEOUT_TICKS);
    }

    public static int terminalControllerAccessRadius() {
        return valueOrDefault(TERMINAL_CONTROLLER_ACCESS_RADIUS, DEFAULT_TERMINAL_CONTROLLER_ACCESS_RADIUS);
    }

    private static int valueOrDefault(ModConfigSpec.IntValue value, int defaultValue) {
        try {
            return value.get();
        } catch (IllegalStateException ignored) {
            return defaultValue;
        }
    }
}

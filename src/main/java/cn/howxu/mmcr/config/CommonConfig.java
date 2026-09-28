package cn.howxu.mmcr.config;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Defines MMCR configuration values shared by both physical sides.
 * @author howxu <dev@howxu.cn>
 */
public final class CommonConfig {
    public static final int DEFAULT_PREVIEW_MAX_ENTRIES = 131_072;
    public static final int DEFAULT_PREVIEW_DURATION_TICKS = 200;
    public static final int DEFAULT_PORT_STORAGE_MAX_ENTRIES = 1_024;
    public static final int DEFAULT_PORT_STORAGE_MAX_PAYLOAD_BYTES = 1_048_576;
    public static final int DEFAULT_DATA_VALUE_MAX_ENTRIES = 1_024;
    public static final int DEFAULT_DATA_VALUE_MAX_DEPTH = 16;
    public static final int DEFAULT_MAX_STRING_LENGTH = 256;
    public static final int DEFAULT_SCREEN_TEXT_MAX_LINES = 1_024;
    public static final int DEFAULT_SCREEN_TEXT_MAX_ENCODED_BYTES = 65_536;
    public static final int DEFAULT_RUNTIME_CONTENT_MAX_STRUCTURES = 4_096;
    public static final int DEFAULT_RUNTIME_CONTENT_MAX_RECIPES = 16_384;
    public static final int DEFAULT_RUNTIME_CONTENT_MAX_SPECS = 4_096;
    public static final int DEFAULT_RUNTIME_CONTENT_MAX_TOOLTIP_LINES = 1_024;
    public static final int DEFAULT_MACHINE_STATE_MAX_LEVEL_SNAPSHOTS = 1_024;
    public static final int DEFAULT_MACHINE_STATE_MAX_INSTALLED_MODULES = 1_024;
    public static final int DEFAULT_FACTORY_STATE_MAX_THREAD_SNAPSHOTS = 1_024;
    public static final int DEFAULT_FACTORY_STATE_MAX_LANE_SNAPSHOTS = 1_024;
    public static final int DEFAULT_FACTORY_STATE_MAX_LEVEL_SNAPSHOTS = 1_024;
    public static final int DEFAULT_FAILURE_MAX_DETAILS = 64;
    public static final int DEFAULT_TERMINAL_STATE_MAX_PREVIEW_LAYERS = 128;
    public static final int DEFAULT_STRUCTURE_SYNC_MAX_COLLECTION_ENTRIES = 1_024;
    public static final int DEFAULT_STRUCTURE_SYNC_MAX_SYMBOL_REQUIREMENTS = 256;
    public static final int DEFAULT_RECIPE_SYNC_MAX_REQUIREMENTS = 4_096;
    public static final int DEFAULT_RECIPE_SYNC_MAX_OUTPUTS = 4_096;
    public static final ModConfigSpec.IntValue PREVIEW_MAX_ENTRIES;
    public static final ModConfigSpec.IntValue PREVIEW_DURATION_TICKS;
    public static final ModConfigSpec.IntValue PORT_STORAGE_MAX_ENTRIES;
    public static final ModConfigSpec.IntValue PORT_STORAGE_MAX_PAYLOAD_BYTES;
    public static final ModConfigSpec.IntValue DATA_VALUE_MAX_ENTRIES;
    public static final ModConfigSpec.IntValue DATA_VALUE_MAX_DEPTH;
    public static final ModConfigSpec.IntValue MAX_STRING_LENGTH;
    public static final ModConfigSpec.IntValue SCREEN_TEXT_MAX_LINES;
    public static final ModConfigSpec.IntValue SCREEN_TEXT_MAX_ENCODED_BYTES;
    public static final ModConfigSpec.IntValue RUNTIME_CONTENT_MAX_STRUCTURES;
    public static final ModConfigSpec.IntValue RUNTIME_CONTENT_MAX_RECIPES;
    public static final ModConfigSpec.IntValue RUNTIME_CONTENT_MAX_SPECS;
    public static final ModConfigSpec.IntValue RUNTIME_CONTENT_MAX_TOOLTIP_LINES;
    public static final ModConfigSpec.IntValue MACHINE_STATE_MAX_LEVEL_SNAPSHOTS;
    public static final ModConfigSpec.IntValue MACHINE_STATE_MAX_INSTALLED_MODULES;
    public static final ModConfigSpec.IntValue FACTORY_STATE_MAX_THREAD_SNAPSHOTS;
    public static final ModConfigSpec.IntValue FACTORY_STATE_MAX_LANE_SNAPSHOTS;
    public static final ModConfigSpec.IntValue FACTORY_STATE_MAX_LEVEL_SNAPSHOTS;
    public static final ModConfigSpec.IntValue FAILURE_MAX_DETAILS;
    public static final ModConfigSpec.IntValue TERMINAL_STATE_MAX_PREVIEW_LAYERS;
    public static final ModConfigSpec.IntValue STRUCTURE_SYNC_MAX_COLLECTION_ENTRIES;
    public static final ModConfigSpec.IntValue STRUCTURE_SYNC_MAX_SYMBOL_REQUIREMENTS;
    public static final ModConfigSpec.IntValue RECIPE_SYNC_MAX_REQUIREMENTS;
    public static final ModConfigSpec.IntValue RECIPE_SYNC_MAX_OUTPUTS;
    public static final ModConfigSpec.IntValue RECIPE_SYNC_MAX_MODIFIERS;
    public static final ModConfigSpec.IntValue RECIPE_SYNC_MAX_REQUIRED_HOSTS;
    public static final ModConfigSpec SPEC;

    static {
        var builder = new ModConfigSpec.Builder();
        builder.translation("mmcr.configuration.common.network").push("network");
        builder.translation("mmcr.configuration.common.network.preview").push("preview");
        PREVIEW_MAX_ENTRIES = define(builder.translation("mmcr.configuration.common.network.preview.max_entries"), "max_entries", DEFAULT_PREVIEW_MAX_ENTRIES,
                "Maximum multiblock preview entries; client and server values must match");
        PREVIEW_DURATION_TICKS = define(builder.translation("mmcr.configuration.common.network.preview.duration_ticks"), "duration_ticks", DEFAULT_PREVIEW_DURATION_TICKS,
                "Default multiblock preview duration in ticks");
        builder.pop();
        builder.translation("mmcr.configuration.common.network.port_storage").push("port_storage");
        PORT_STORAGE_MAX_ENTRIES = define(builder.translation("mmcr.configuration.common.network.port_storage.max_entries"), "max_entries", DEFAULT_PORT_STORAGE_MAX_ENTRIES,
                "Maximum port storage entries per payload; client and server values must match");
        PORT_STORAGE_MAX_PAYLOAD_BYTES = define(builder.translation("mmcr.configuration.common.network.port_storage.max_payload_bytes"), "max_payload_bytes", DEFAULT_PORT_STORAGE_MAX_PAYLOAD_BYTES,
                "Maximum encoded port storage payload size; client and server values must match");
        builder.pop();
        builder.translation("mmcr.configuration.common.network.data_value").push("data_value");
        DATA_VALUE_MAX_ENTRIES = define(builder.translation("mmcr.configuration.common.network.data_value.max_entries"), "max_entries", DEFAULT_DATA_VALUE_MAX_ENTRIES,
                "Maximum entries in one serialized data value collection");
        DATA_VALUE_MAX_DEPTH = define(builder.translation("mmcr.configuration.common.network.data_value.max_depth"), "max_depth", DEFAULT_DATA_VALUE_MAX_DEPTH,
                "Maximum nesting depth in one serialized data value");
        MAX_STRING_LENGTH = define(builder.translation("mmcr.configuration.common.network.data_value.max_string_length"), "max_string_length", DEFAULT_MAX_STRING_LENGTH,
                "Maximum UTF-8 string length in data and machine state payloads");
        builder.pop();
        builder.translation("mmcr.configuration.common.network.screen_text").push("screen_text");
        SCREEN_TEXT_MAX_LINES = define(builder.translation("mmcr.configuration.common.network.screen_text.max_lines"), "max_lines", DEFAULT_SCREEN_TEXT_MAX_LINES,
                "Maximum controller screen text lines per payload");
        SCREEN_TEXT_MAX_ENCODED_BYTES = define(builder.translation("mmcr.configuration.common.network.screen_text.max_encoded_bytes"), "max_encoded_bytes", DEFAULT_SCREEN_TEXT_MAX_ENCODED_BYTES,
                "Maximum encoded controller screen text size");
        builder.pop();
        builder.translation("mmcr.configuration.common.network.runtime_content").push("runtime_content");
        RUNTIME_CONTENT_MAX_STRUCTURES = define(builder.translation("mmcr.configuration.common.network.runtime_content.max_structures"), "max_structures", DEFAULT_RUNTIME_CONTENT_MAX_STRUCTURES,
                "Maximum synchronized machine structures");
        RUNTIME_CONTENT_MAX_RECIPES = define(builder.translation("mmcr.configuration.common.network.runtime_content.max_recipes"), "max_recipes", DEFAULT_RUNTIME_CONTENT_MAX_RECIPES,
                "Maximum synchronized machine recipes");
        RUNTIME_CONTENT_MAX_SPECS = define(builder.translation("mmcr.configuration.common.network.runtime_content.max_specs"), "max_specs", DEFAULT_RUNTIME_CONTENT_MAX_SPECS,
                "Maximum synchronized controller or appearance specs");
        RUNTIME_CONTENT_MAX_TOOLTIP_LINES = define(builder.translation("mmcr.configuration.common.network.runtime_content.max_tooltip_lines"), "max_tooltip_lines", DEFAULT_RUNTIME_CONTENT_MAX_TOOLTIP_LINES,
                "Maximum synchronized tooltip lines");
        builder.pop();
        builder.translation("mmcr.configuration.common.network.machine_state").push("machine_state");
        MACHINE_STATE_MAX_LEVEL_SNAPSHOTS = define(builder.translation("mmcr.configuration.common.network.machine_state.max_level_snapshots"), "max_level_snapshots", DEFAULT_MACHINE_STATE_MAX_LEVEL_SNAPSHOTS,
                "Maximum machine level snapshots per payload");
        MACHINE_STATE_MAX_INSTALLED_MODULES = define(builder.translation("mmcr.configuration.common.network.machine_state.max_installed_modules"), "max_installed_modules", DEFAULT_MACHINE_STATE_MAX_INSTALLED_MODULES,
                "Maximum installed modules in one machine state payload");
        builder.pop();
        builder.translation("mmcr.configuration.common.network.factory_state").push("factory_state");
        FACTORY_STATE_MAX_THREAD_SNAPSHOTS = define(builder.translation("mmcr.configuration.common.network.factory_state.max_thread_snapshots"), "max_thread_snapshots", DEFAULT_FACTORY_STATE_MAX_THREAD_SNAPSHOTS,
                "Maximum factory thread snapshots per payload");
        FACTORY_STATE_MAX_LANE_SNAPSHOTS = define(builder.translation("mmcr.configuration.common.network.factory_state.max_lane_snapshots"), "max_lane_snapshots", DEFAULT_FACTORY_STATE_MAX_LANE_SNAPSHOTS,
                "Maximum factory lane snapshots per payload");
        FACTORY_STATE_MAX_LEVEL_SNAPSHOTS = define(builder.translation("mmcr.configuration.common.network.factory_state.max_level_snapshots"), "max_level_snapshots", DEFAULT_FACTORY_STATE_MAX_LEVEL_SNAPSHOTS,
                "Maximum factory level snapshots per payload");
        builder.pop();
        builder.translation("mmcr.configuration.common.network.failure").push("failure");
        FAILURE_MAX_DETAILS = define(builder.translation("mmcr.configuration.common.network.failure.max_details"), "max_details", DEFAULT_FAILURE_MAX_DETAILS,
                "Maximum failure details in machine state payloads");
        builder.pop();
        builder.translation("mmcr.configuration.common.network.terminal_state").push("terminal_state");
        TERMINAL_STATE_MAX_PREVIEW_LAYERS = define(builder.translation("mmcr.configuration.common.network.terminal_state.max_preview_layers"), "max_preview_layers", DEFAULT_TERMINAL_STATE_MAX_PREVIEW_LAYERS,
                "Maximum preview layers in terminal state payloads");
        builder.pop();
        builder.translation("mmcr.configuration.common.network.structure_sync").push("structure_sync");
        STRUCTURE_SYNC_MAX_COLLECTION_ENTRIES = define(builder.translation("mmcr.configuration.common.network.structure_sync.max_collection_entries"), "max_collection_entries",
                DEFAULT_STRUCTURE_SYNC_MAX_COLLECTION_ENTRIES, "Maximum entries in synchronized structure collections");
        STRUCTURE_SYNC_MAX_SYMBOL_REQUIREMENTS = define(builder.translation("mmcr.configuration.common.network.structure_sync.max_symbol_requirements"), "max_symbol_requirements",
                DEFAULT_STRUCTURE_SYNC_MAX_SYMBOL_REQUIREMENTS, "Maximum symbol requirements in synchronized structures");
        builder.pop();
        builder.translation("mmcr.configuration.common.network.recipe_sync").push("recipe_sync");
        RECIPE_SYNC_MAX_REQUIREMENTS = define(builder.translation("mmcr.configuration.common.network.recipe_sync.max_requirements"), "max_requirements", DEFAULT_RECIPE_SYNC_MAX_REQUIREMENTS,
                "Maximum requirements in one synchronized recipe");
        RECIPE_SYNC_MAX_OUTPUTS = define(builder.translation("mmcr.configuration.common.network.recipe_sync.max_outputs"), "max_outputs", DEFAULT_RECIPE_SYNC_MAX_OUTPUTS,
                "Maximum outputs in one synchronized recipe");
        RECIPE_SYNC_MAX_MODIFIERS = define(builder.translation("mmcr.configuration.common.network.recipe_sync.max_modifiers"), "max_modifiers", DEFAULT_STRUCTURE_SYNC_MAX_COLLECTION_ENTRIES,
                "Maximum modifiers in one synchronized recipe");
        RECIPE_SYNC_MAX_REQUIRED_HOSTS = define(builder.translation("mmcr.configuration.common.network.recipe_sync.max_required_hosts"), "max_required_hosts", DEFAULT_STRUCTURE_SYNC_MAX_COLLECTION_ENTRIES,
                "Maximum required hosts in one synchronized recipe");
        builder.pop();
        builder.pop();
        SPEC = builder.build();
    }

    private CommonConfig() {
    }

    private static ModConfigSpec.IntValue define(ModConfigSpec.Builder builder, String key, int defaultValue, String comment) {
        return builder.comment(comment).defineInRange(key, defaultValue, 1, Integer.MAX_VALUE);
    }

    public static int valueOrDefault(ModConfigSpec.IntValue value, int defaultValue) {
        try {
            return value.get();
        } catch (IllegalStateException ignored) {
            return defaultValue;
        }
    }
}

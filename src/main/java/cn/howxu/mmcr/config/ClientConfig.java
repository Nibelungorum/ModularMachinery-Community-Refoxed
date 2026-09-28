package cn.howxu.mmcr.config;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Defines client-only MMCR configuration values.
 * @author howxu <dev@howxu.cn>
 */
public final class ClientConfig {
    public static final double DEFAULT_PREVIEW_RENDER_RADIUS = 64.0;
    public static final double DEFAULT_INTERACTIVE_RENDER_SCALE = 0.5;
    public static final int DEFAULT_INTERACTIVE_RESTORE_DELAY_MS = 150;
    public static final boolean DEFAULT_INTERACTIVE_SKIP_TRANSLUCENT = true;
    public static final boolean DEFAULT_INTERACTIVE_SKIP_BLOCK_ENTITIES = true;
    public static final int DEFAULT_SCENE_PARALLEL_COMPILE_THRESHOLD = 80_000;
    public static final boolean DEFAULT_SHOW_FACTORY_THREAD_OUTPUT_ICON = true;
    public static final boolean DEFAULT_SHOW_FACTORY_THREAD_RECIPE_PROGRESS = true;
    public static final ModConfigSpec.DoubleValue PREVIEW_RENDER_RADIUS;
    public static final ModConfigSpec.DoubleValue INTERACTIVE_RENDER_SCALE;
    public static final ModConfigSpec.IntValue INTERACTIVE_RESTORE_DELAY_MS;
    public static final ModConfigSpec.BooleanValue INTERACTIVE_SKIP_TRANSLUCENT;
    public static final ModConfigSpec.BooleanValue INTERACTIVE_SKIP_BLOCK_ENTITIES;
    public static final ModConfigSpec.IntValue SCENE_PARALLEL_COMPILE_THRESHOLD;
    public static final ModConfigSpec.BooleanValue SHOW_FACTORY_THREAD_OUTPUT_ICON;
    public static final ModConfigSpec.BooleanValue SHOW_FACTORY_THREAD_RECIPE_PROGRESS;
    public static final ModConfigSpec SPEC;

    static {
        var builder = new ModConfigSpec.Builder();
        builder.translation("mmcr.configuration.client.preview").push("preview");
        PREVIEW_RENDER_RADIUS = builder
                .translation("mmcr.configuration.client.preview.render_radius")
                .comment("Maximum distance at which multiblock preview blocks are rendered")
                .defineInRange("render_radius", DEFAULT_PREVIEW_RENDER_RADIUS, 1.0, 512.0);
        INTERACTIVE_RENDER_SCALE = builder
                .translation("mmcr.configuration.client.preview.interactive_render_scale")
                .comment("Off-screen preview resolution scale while rotating or zooming")
                .defineInRange("interactive_render_scale", DEFAULT_INTERACTIVE_RENDER_SCALE, 0.1, 1.0);
        INTERACTIVE_RESTORE_DELAY_MS = builder
                .translation("mmcr.configuration.client.preview.interactive_restore_delay_ms")
                .comment("Delay before restoring full preview quality after zooming")
                .defineInRange("interactive_restore_delay_ms", DEFAULT_INTERACTIVE_RESTORE_DELAY_MS, 0, 2_000);
        INTERACTIVE_SKIP_TRANSLUCENT = builder
                .translation("mmcr.configuration.client.preview.interactive_skip_translucent")
                .comment("Skip translucent preview blocks while rotating or zooming")
                .define("interactive_skip_translucent", DEFAULT_INTERACTIVE_SKIP_TRANSLUCENT);
        INTERACTIVE_SKIP_BLOCK_ENTITIES = builder
                .translation("mmcr.configuration.client.preview.interactive_skip_block_entities")
                .comment("Skip preview block entities while rotating or zooming")
                .define("interactive_skip_block_entities", DEFAULT_INTERACTIVE_SKIP_BLOCK_ENTITIES);
        SCENE_PARALLEL_COMPILE_THRESHOLD = builder
                .translation("mmcr.configuration.client.preview.scene_parallel_compile_threshold")
                .comment("Preview block count that enables parallel scene mesh compilation")
                .defineInRange("scene_parallel_compile_threshold", DEFAULT_SCENE_PARALLEL_COMPILE_THRESHOLD,
                        1, Integer.MAX_VALUE);
        builder.pop();
        builder.translation("mmcr.configuration.client.factory_controller").push("factory_controller");
        SHOW_FACTORY_THREAD_OUTPUT_ICON = builder
                .translation("mmcr.configuration.client.factory_controller.show_thread_output_icon")
                .comment("Show the first recipe output icon in active factory thread elements")
                .define("show_thread_output_icon", DEFAULT_SHOW_FACTORY_THREAD_OUTPUT_ICON);
        SHOW_FACTORY_THREAD_RECIPE_PROGRESS = builder
                .translation("mmcr.configuration.client.factory_controller.show_thread_recipe_progress")
                .comment("Show recipe progress below the output icon area in active factory thread elements")
                .define("show_thread_recipe_progress", DEFAULT_SHOW_FACTORY_THREAD_RECIPE_PROGRESS);
        builder.pop();
        SPEC = builder.build();
    }

    private ClientConfig() {
    }

    public static double interactiveRenderScale() {
        try {
            return INTERACTIVE_RENDER_SCALE.get();
        } catch (IllegalStateException ignored) {
            return DEFAULT_INTERACTIVE_RENDER_SCALE;
        }
    }

    public static long interactiveRestoreDelayNanos() {
        try {
            return INTERACTIVE_RESTORE_DELAY_MS.get() * 1_000_000L;
        } catch (IllegalStateException ignored) {
            return DEFAULT_INTERACTIVE_RESTORE_DELAY_MS * 1_000_000L;
        }
    }

    public static boolean skipTranslucentDuringInteraction() {
        try {
            return INTERACTIVE_SKIP_TRANSLUCENT.get();
        } catch (IllegalStateException ignored) {
            return DEFAULT_INTERACTIVE_SKIP_TRANSLUCENT;
        }
    }

    public static boolean skipBlockEntitiesDuringInteraction() {
        try {
            return INTERACTIVE_SKIP_BLOCK_ENTITIES.get();
        } catch (IllegalStateException ignored) {
            return DEFAULT_INTERACTIVE_SKIP_BLOCK_ENTITIES;
        }
    }

    public static int sceneParallelCompileThreshold() {
        try {
            return SCENE_PARALLEL_COMPILE_THRESHOLD.get();
        } catch (IllegalStateException ignored) {
            return DEFAULT_SCENE_PARALLEL_COMPILE_THRESHOLD;
        }
    }

    public static boolean showFactoryThreadOutputIcon() {
        try {
            return SHOW_FACTORY_THREAD_OUTPUT_ICON.get();
        } catch (IllegalStateException ignored) {
            return DEFAULT_SHOW_FACTORY_THREAD_OUTPUT_ICON;
        }
    }

    public static boolean showFactoryThreadRecipeProgress() {
        try {
            return SHOW_FACTORY_THREAD_RECIPE_PROGRESS.get();
        } catch (IllegalStateException ignored) {
            return DEFAULT_SHOW_FACTORY_THREAD_RECIPE_PROGRESS;
        }
    }
}

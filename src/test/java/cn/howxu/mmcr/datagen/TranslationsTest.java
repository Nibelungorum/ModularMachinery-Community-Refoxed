package cn.howxu.mmcr.datagen;

import static org.junit.jupiter.api.Assertions.assertEquals;

import cn.howxu.mmcr.api.recipe.ParallelTier;
import org.junit.jupiter.api.Test;

class TranslationsTest {

    @Test
    void jade_machine_controller_plugin_config_has_display_name() {
        assertEquals("Machine Controller", Translations.ALL.get("en_us").get("config.jade.plugin_mmcr.machine_controller"));
        assertEquals("机器控制器", Translations.ALL.get("zh_cn").get("config.jade.plugin_mmcr.machine_controller"));
    }

    @Test
    void cracker_controller_has_block_and_item_display_names() {
        assertEquals("Cracker Controller", Translations.ALL.get("en_us").get("block.mmcr.cracker_controller"));
        assertEquals("Cracker Controller", Translations.ALL.get("en_us").get("item.mmcr.cracker_controller"));
        assertEquals("裂化器控制器", Translations.ALL.get("zh_cn").get("block.mmcr.cracker_controller"));
        assertEquals("裂化器控制器", Translations.ALL.get("zh_cn").get("item.mmcr.cracker_controller"));
    }

    @Test
    void parallel_controllers_have_block_and_item_display_names() {
        for (ParallelTier tier : ParallelTier.values()) {
            String id = tier.idSuffix();
            String en = enDisplayName(tier);
            String zh = zhDisplayName(tier);
            assertEquals(en, Translations.ALL.get("en_us").get("block.mmcr." + id));
            assertEquals(en, Translations.ALL.get("en_us").get("item.mmcr." + id));
            assertEquals(zh, Translations.ALL.get("zh_cn").get("block.mmcr." + id));
            assertEquals(zh, Translations.ALL.get("zh_cn").get("item.mmcr." + id));
        }
    }

    private static String enDisplayName(ParallelTier tier) {
        return switch (tier) {
            case NORMAL -> "Normal Parallel Controller 4x";
            case PLUS -> "Plus Parallel Controller 16x";
            case REINFORCED -> "Reinforced Parallel Controller 64x";
            case PRO -> "Pro Parallel Controller 256x";
            case ELITE -> "Elite Parallel Controller 1024x";
            case FANTASY -> "Fantasy Parallel Controller 4096x";
            case MAX -> "Max Parallel Controller 16384x";
            case ULTIMATE -> "Ultimate Parallel Controller 2147483647x";
        };
    }

    private static String zhDisplayName(ParallelTier tier) {
        return switch (tier) {
            case NORMAL -> "普通并行器 4x";
            case PLUS -> "进阶并行器 16x";
            case REINFORCED -> "强化并行器 64x";
            case PRO -> "专业并行器 256x";
            case ELITE -> "精英并行器 1024x";
            case FANTASY -> "幻想并行器 4096x";
            case MAX -> "极限并行器 16384x";
            case ULTIMATE -> "终极并行器 2147483647x";
        };
    }

    @Test
    void factory_controller_and_thread_disperser_have_display_names() {
        assertEquals("Factory Controller", Translations.ALL.get("en_us").get("block.mmcr.factory_controller"));
        assertEquals("Factory Controller", Translations.ALL.get("en_us").get("item.mmcr.factory_controller"));
        assertEquals("Thread Disperser", Translations.ALL.get("en_us").get("item.mmcr.thread_disperser"));
        assertEquals("Factory Controller", Translations.ALL.get("en_us").get("container.mmcr.factory_controller"));
        assertEquals("工厂控制器", Translations.ALL.get("zh_cn").get("block.mmcr.factory_controller"));
        assertEquals("工厂控制器", Translations.ALL.get("zh_cn").get("item.mmcr.factory_controller"));
        assertEquals("线程分散器", Translations.ALL.get("zh_cn").get("item.mmcr.thread_disperser"));
        assertEquals("工厂控制器", Translations.ALL.get("zh_cn").get("container.mmcr.factory_controller"));
    }

    @Test
    void controller_parallel_thread_and_port_labels_are_generated_for_both_locales() {
        assertEquals("Parallel Slots", Translations.ALL.get("en_us").get("jade.mmcr.machine_controller.parallel_slots"));
        assertEquals("Multithreading", Translations.ALL.get("en_us").get("jade.mmcr.machine_controller.threads"));
        assertEquals("Parallel Slot Count: %s", Translations.ALL.get("en_us").get("gui.mmcr.controller.parallel_slots"));
        assertEquals("Multithreading: %s / %s", Translations.ALL.get("en_us").get("gui.mmcr.controller.threads"));
        assertEquals("Parallel: %s / %s", Translations.ALL.get("en_us").get("gui.mmcr.controller.parallel"));
        assertEquals("并行仓数量", Translations.ALL.get("zh_cn").get("jade.mmcr.machine_controller.parallel_slots"));
        assertEquals("多线程", Translations.ALL.get("zh_cn").get("jade.mmcr.machine_controller.threads"));
        assertEquals("并行仓数量: %s", Translations.ALL.get("zh_cn").get("gui.mmcr.controller.parallel_slots"));
        assertEquals("多线程: %s / %s", Translations.ALL.get("zh_cn").get("gui.mmcr.controller.threads"));
        assertEquals("并行数: %s / %s", Translations.ALL.get("zh_cn").get("gui.mmcr.controller.parallel"));
    }

    @Test
    void jei_recipe_labels_are_generated_for_both_locales() {
        assertEquals("Machine Recipe", Translations.ALL.get("en_us").get("jei.mmcr.machine_recipe"));
        assertEquals("Duration: %s t %s s", Translations.ALL.get("en_us").get("jei.mmcr.machine_recipe.duration"));
        assertEquals("Only item inputs can be transferred. Fluids and energy must be supplied by hatches.", Translations.ALL.get("en_us").get("jei.mmcr.transfer.fluid_energy_not_supported"));
        assertEquals("机器配方", Translations.ALL.get("zh_cn").get("jei.mmcr.machine_recipe"));
        assertEquals("耗时：%s tick %s 秒", Translations.ALL.get("zh_cn").get("jei.mmcr.machine_recipe.duration"));
        assertEquals("仅支持转移物品输入；流体和能量需要通过对应端口供应。", Translations.ALL.get("zh_cn").get("jei.mmcr.transfer.fluid_energy_not_supported"));
    }
}

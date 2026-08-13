package cn.howxu.mmcr.datagen;

import static org.junit.jupiter.api.Assertions.assertEquals;

import cn.howxu.mmcr.api.recipe.ParallelTier;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

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
            case NORMAL -> "Normal Parallel Controller";
            case PLUS -> "Plus Parallel Controller";
            case REINFORCED -> "Reinforced Parallel Controller";
            case PRO -> "Pro Parallel Controller";
            case ELITE -> "Elite Parallel Controller";
            case FANTASY -> "Fantasy Parallel Controller";
            case MAX -> "Max Parallel Controller";
            case ULTIMATE -> "Ultimate Parallel Controller";
        };
    }

    private static String zhDisplayName(ParallelTier tier) {
        return switch (tier) {
            case NORMAL -> "普通并行器";
            case PLUS -> "进阶并行器";
            case REINFORCED -> "强化并行器";
            case PRO -> "专业并行器";
            case ELITE -> "精英并行器";
            case FANTASY -> "幻想并行器";
            case MAX -> "极限并行器";
            case ULTIMATE -> "终极并行器";
        };
    }

    @Test
    void factory_controller_and_thread_disperser_have_display_names() {
        assertEquals("Factory Controller", Translations.ALL.get("en_us").get("block.mmcr.factory_controller"));
        assertEquals("Factory Controller", Translations.ALL.get("en_us").get("item.mmcr.factory_controller"));
        assertEquals("Thread Disperser", Translations.ALL.get("en_us").get("item.mmcr.thread_disperser"));
        assertEquals("Enables multithreaded recipes", Translations.ALL.get("en_us").get("tooltip.mmcr.thread_disperser.multithreading"));
        assertEquals("Factory Controller", Translations.ALL.get("en_us").get("container.mmcr.factory_controller"));
        assertEquals("工厂控制器", Translations.ALL.get("zh_cn").get("block.mmcr.factory_controller"));
        assertEquals("工厂控制器", Translations.ALL.get("zh_cn").get("item.mmcr.factory_controller"));
        assertEquals("线程分散器", Translations.ALL.get("zh_cn").get("item.mmcr.thread_disperser"));
        assertEquals("提供多线程能力", Translations.ALL.get("zh_cn").get("tooltip.mmcr.thread_disperser.multithreading"));
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

    @Test
    void translations_include_smart_interface_messages() {
        assertThat(Translations.ALL.get("en_us")).containsKeys(
                "block.mmcr.smart_interface",
                "item.mmcr.smart_interface",
                "container.mmcr.smart_interface",
                "mmcr.smart_interface.value",
                "mmcr.recipe.smart_interface_mismatch",
                "mmcr.smart_interface.empty_binding",
                "mmcr.smart_interface.previous",
                "mmcr.smart_interface.next",
                "mmcr.smart_interface.save",
                "mmcr.smart_interface.input",
                "mmcr.smart_interface.invalid_number",
                "jei.mmcr.smart_interface.requirement.input",
                "jei.mmcr.smart_interface.requirement.output");
        assertThat(Translations.ALL.get("zh_cn")).containsKeys(
                "block.mmcr.smart_interface",
                "container.mmcr.smart_interface",
                "mmcr.smart_interface.value",
                "mmcr.recipe.smart_interface_mismatch",
                "mmcr.smart_interface.empty_binding",
                "mmcr.smart_interface.previous",
                "mmcr.smart_interface.next",
                "mmcr.smart_interface.save",
                "mmcr.smart_interface.input",
                "mmcr.smart_interface.invalid_number",
                "jei.mmcr.smart_interface.requirement.input",
                "jei.mmcr.smart_interface.requirement.output");
    }

    @Test
    void translations_include_interface_tooltip_labels() {
        assertThat(Translations.ALL.get("en_us")).containsEntry("tooltip.mmcr.interface.capacity", "Capacity: %s")
                .containsEntry("tooltip.mmcr.interface.capacity_with_unit", "Capacity: %s%s")
                .containsEntry("tooltip.mmcr.interface.capacity_label", "Capacity: ")
                .containsEntry("tooltip.mmcr.interface.rate", "Rate: %s")
                .containsEntry("tooltip.mmcr.interface.rate_label", "Rate: ")
                .containsEntry("tooltip.mmcr.interface.parallel", "Parallel: %s")
                .containsEntry("tooltip.mmcr.interface.unit.slots", " slots")
                .containsEntry("tooltip.mmcr.factory_controller.multithreading", "Enables multithreaded factory scheduling")
                .containsEntry("tooltip.mmcr.thread_disperser.multithreading", "Enables multithreaded recipes");
        assertThat(Translations.ALL.get("zh_cn")).containsEntry("tooltip.mmcr.interface.capacity", "容量: %s")
                .containsEntry("tooltip.mmcr.interface.capacity_with_unit", "容量: %s%s")
                .containsEntry("tooltip.mmcr.interface.capacity_label", "容量: ")
                .containsEntry("tooltip.mmcr.interface.rate", "速率: %s")
                .containsEntry("tooltip.mmcr.interface.rate_label", "速率: ")
                .containsEntry("tooltip.mmcr.interface.parallel", "并行: %s")
                .containsEntry("tooltip.mmcr.interface.unit.slots", "格")
                .containsEntry("tooltip.mmcr.factory_controller.multithreading", "提供工厂多线程调度能力")
                .containsEntry("tooltip.mmcr.thread_disperser.multithreading", "提供多线程能力");
    }
}

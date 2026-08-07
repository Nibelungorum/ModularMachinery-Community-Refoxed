package cn.howxu.mmcr.datagen;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
    void jei_recipe_labels_are_generated_for_both_locales() {
        assertEquals("Machine Recipe", Translations.ALL.get("en_us").get("jei.mmcr.machine_recipe"));
        assertEquals("Duration: %s t %s s", Translations.ALL.get("en_us").get("jei.mmcr.machine_recipe.duration"));
        assertEquals("Only item inputs can be transferred. Fluids and energy must be supplied by hatches.", Translations.ALL.get("en_us").get("jei.mmcr.transfer.fluid_energy_not_supported"));
        assertEquals("机器配方", Translations.ALL.get("zh_cn").get("jei.mmcr.machine_recipe"));
        assertEquals("耗时：%s tick %s 秒", Translations.ALL.get("zh_cn").get("jei.mmcr.machine_recipe.duration"));
        assertEquals("仅支持转移物品输入；流体和能量需要通过对应端口供应。", Translations.ALL.get("zh_cn").get("jei.mmcr.transfer.fluid_energy_not_supported"));
    }
}

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
}

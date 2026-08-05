package cn.howxu.mmcr.datagen;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class TranslationsTest {

    @Test
    void jade_machine_controller_plugin_config_has_display_name() {
        assertEquals("Machine Controller", Translations.ALL.get("en_us").get("config.jade.plugin_mmcr.machine_controller"));
        assertEquals("机器控制器", Translations.ALL.get("zh_cn").get("config.jade.plugin_mmcr.machine_controller"));
    }
}

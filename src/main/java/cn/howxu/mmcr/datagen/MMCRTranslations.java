package cn.howxu.mmcr.datagen;

import java.util.Map;

/** 集中存放所有语言的翻译键值,与 Provider 解耦。仅作数据存储,无业务逻辑。 */
public final class MMCRTranslations {

    public static final Map<String, Map<String, String>> ALL = Map.ofEntries(
            Map.entry("en_us", Map.ofEntries(
                    Map.entry("itemGroup.mmcr",                    "Modular Machinery: Refoxed"),
                    Map.entry("block.mmcr.controller",             "Machine Controller"),
                    Map.entry("block.mmcr.casing",                 "Machine Casing"),
                    Map.entry("block.mmcr.io_port_item_basic",     "Item IO Port"),
                    Map.entry("block.mmcr.io_port_fluid_basic",    "Fluid IO Port"),
                    Map.entry("block.mmcr.io_port_energy_basic",   "Energy IO Port"))),
            Map.entry("zh_cn", Map.ofEntries(
                    Map.entry("itemGroup.mmcr",                    "模块化机械:Refoxed"),
                    Map.entry("block.mmcr.controller",             "机器控制器"),
                    Map.entry("block.mmcr.casing",                 "机器外壳"),
                    Map.entry("block.mmcr.io_port_item_basic",     "物品 IO 端口"),
                    Map.entry("block.mmcr.io_port_fluid_basic",    "流体 IO 端口"),
                    Map.entry("block.mmcr.io_port_energy_basic",   "能量 IO 端口")))
    );

    private MMCRTranslations() {}
}

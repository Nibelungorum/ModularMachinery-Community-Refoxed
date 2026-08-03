package cn.howxu.mmcr.datagen;

import java.util.Map;

/** 集中存放所有语言的翻译键值,与 Provider 解耦。仅作数据存储,无业务逻辑。 */
public final class Translations {

    public static final Map<String, Map<String, String>> ALL = Map.ofEntries(
            Map.entry("en_us", Map.ofEntries(
                    Map.entry("itemGroup.mmcr",                    "Modular Machinery Community"),
                    Map.entry("block.mmcr.blast_furnace_controller", "Blast Furnace Controller"),
                    Map.entry("block.mmcr.basic_casing",            "Basic Machine Casing"),
                    Map.entry("block.mmcr.io_port_item_basic",     "Item IO Port"),
                    Map.entry("block.mmcr.io_port_fluid_basic",    "Fluid IO Port"),
                    Map.entry("block.mmcr.io_port_energy_basic",   "Energy IO Port"),
                    Map.entry("item.mmcr.blast_furnace_controller",  "Blast Furnace Controller"),
                    Map.entry("item.mmcr.basic_casing",             "Basic Machine Casing"),
                    Map.entry("item.mmcr.io_port_item_basic",      "Item IO Port"),
                    Map.entry("item.mmcr.io_port_fluid_basic",     "Fluid IO Port"),
                    Map.entry("item.mmcr.io_port_energy_basic",    "Energy IO Port"),
                    Map.entry("container.mmcr.item_bus",           "Item IO Port"),
                    Map.entry("container.mmcr.fluid_hatch",        "Fluid IO Port"),
                    Map.entry("container.mmcr.energy_hatch",       "Energy IO Port"),
                    Map.entry("container.mmcr.machine_controller", "Machine Controller"),
                    Map.entry("gui.mmcr.controller.machine",       "Machine: %s"),
                    Map.entry("gui.mmcr.controller.formed",        "Structure: Formed"),
                    Map.entry("gui.mmcr.controller.unformed",      "Structure: Not Formed"),
                    Map.entry("gui.mmcr.controller.idle",          "Status: Idle"),
                    Map.entry("gui.mmcr.controller.progress",      "Progress: %s%%"))),
            Map.entry("zh_cn", Map.ofEntries(
                    Map.entry("itemGroup.mmcr",                    "模块化机械社区版"),
                    Map.entry("block.mmcr.blast_furnace_controller", "高炉控制器"),
                    Map.entry("block.mmcr.basic_casing",            "基础机器外壳"),
                    Map.entry("block.mmcr.io_port_item_basic",     "物品 IO 端口"),
                    Map.entry("block.mmcr.io_port_fluid_basic",    "流体 IO 端口"),
                    Map.entry("block.mmcr.io_port_energy_basic",   "能量 IO 端口"),
                    Map.entry("item.mmcr.blast_furnace_controller",  "高炉控制器"),
                    Map.entry("item.mmcr.basic_casing",             "基础机器外壳"),
                    Map.entry("item.mmcr.io_port_item_basic",      "物品 IO 端口"),
                    Map.entry("item.mmcr.io_port_fluid_basic",     "流体 IO 端口"),
                    Map.entry("item.mmcr.io_port_energy_basic",    "能量 IO 端口"),
                    Map.entry("container.mmcr.item_bus",           "物品 IO 端口"),
                    Map.entry("container.mmcr.fluid_hatch",        "流体 IO 端口"),
                    Map.entry("container.mmcr.energy_hatch",       "能量 IO 端口"),
                    Map.entry("container.mmcr.machine_controller", "机器控制器"),
                    Map.entry("gui.mmcr.controller.machine",       "机器: %s"),
                    Map.entry("gui.mmcr.controller.formed",        "结构: 已成型"),
                    Map.entry("gui.mmcr.controller.unformed",      "结构: 未成型"),
                    Map.entry("gui.mmcr.controller.idle",          "状态: 空闲"),
                    Map.entry("gui.mmcr.controller.progress",      "进度: %s%%")))
    );

    private Translations() {}
}

package cn.howxu.mmcr.datagen;

import java.util.Map;

/** 集中存放所有语言的翻译键值,与 Provider 解耦。仅作数据存储,无业务逻辑。 */
public final class Translations {

    public static final Map<String, Map<String, String>> ALL = Map.ofEntries(
            Map.entry("en_us", Map.ofEntries(
                    Map.entry("itemGroup.mmcr",                    "Modular Machinery Community"),
                    Map.entry("block.mmcr.blast_furnace_controller", "Blast Furnace Controller"),
                    Map.entry("block.mmcr.basic_casing",            "Basic Machine Casing"),
                    Map.entry("block.mmcr.item_input_bus",         "Item Input Bus"),
                    Map.entry("block.mmcr.item_output_bus",        "Item Output Bus"),
                    Map.entry("block.mmcr.fluid_input_hatch",      "Fluid Input Hatch"),
                    Map.entry("block.mmcr.fluid_output_hatch",     "Fluid Output Hatch"),
                    Map.entry("block.mmcr.energy_input_hatch",     "Energy Input Hatch"),
                    Map.entry("block.mmcr.energy_output_hatch",    "Energy Output Hatch"),
                    Map.entry("item.mmcr.blast_furnace_controller",  "Blast Furnace Controller"),
                    Map.entry("item.mmcr.basic_casing",             "Basic Machine Casing"),
                    Map.entry("item.mmcr.item_input_bus",          "Item Input Bus"),
                    Map.entry("item.mmcr.item_output_bus",         "Item Output Bus"),
                    Map.entry("item.mmcr.fluid_input_hatch",       "Fluid Input Hatch"),
                    Map.entry("item.mmcr.fluid_output_hatch",      "Fluid Output Hatch"),
                    Map.entry("item.mmcr.energy_input_hatch",      "Energy Input Hatch"),
                    Map.entry("item.mmcr.energy_output_hatch",     "Energy Output Hatch"),
                    Map.entry("container.mmcr.item_input_bus",     "Item Input Bus"),
                    Map.entry("container.mmcr.item_output_bus",    "Item Output Bus"),
                    Map.entry("container.mmcr.fluid_input_hatch",  "Fluid Input Hatch"),
                    Map.entry("container.mmcr.fluid_output_hatch", "Fluid Output Hatch"),
                    Map.entry("container.mmcr.energy_input_hatch", "Energy Input Hatch"),
                    Map.entry("container.mmcr.energy_output_hatch", "Energy Output Hatch"),
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
                    Map.entry("block.mmcr.item_input_bus",         "物品输入总线"),
                    Map.entry("block.mmcr.item_output_bus",        "物品输出总线"),
                    Map.entry("block.mmcr.fluid_input_hatch",      "流体输入仓"),
                    Map.entry("block.mmcr.fluid_output_hatch",     "流体输出仓"),
                    Map.entry("block.mmcr.energy_input_hatch",     "能量输入仓"),
                    Map.entry("block.mmcr.energy_output_hatch",    "能量输出仓"),
                    Map.entry("item.mmcr.blast_furnace_controller",  "高炉控制器"),
                    Map.entry("item.mmcr.basic_casing",             "基础机器外壳"),
                    Map.entry("item.mmcr.item_input_bus",          "物品输入总线"),
                    Map.entry("item.mmcr.item_output_bus",         "物品输出总线"),
                    Map.entry("item.mmcr.fluid_input_hatch",       "流体输入仓"),
                    Map.entry("item.mmcr.fluid_output_hatch",      "流体输出仓"),
                    Map.entry("item.mmcr.energy_input_hatch",      "能量输入仓"),
                    Map.entry("item.mmcr.energy_output_hatch",     "能量输出仓"),
                    Map.entry("container.mmcr.item_input_bus",     "物品输入总线"),
                    Map.entry("container.mmcr.item_output_bus",    "物品输出总线"),
                    Map.entry("container.mmcr.fluid_input_hatch",  "流体输入仓"),
                    Map.entry("container.mmcr.fluid_output_hatch", "流体输出仓"),
                    Map.entry("container.mmcr.energy_input_hatch", "能量输入仓"),
                    Map.entry("container.mmcr.energy_output_hatch", "能量输出仓"),
                    Map.entry("container.mmcr.machine_controller", "机器控制器"),
                    Map.entry("gui.mmcr.controller.machine",       "机器: %s"),
                    Map.entry("gui.mmcr.controller.formed",        "结构: 已成型"),
                    Map.entry("gui.mmcr.controller.unformed",      "结构: 未成型"),
                    Map.entry("gui.mmcr.controller.idle",          "状态: 空闲"),
                    Map.entry("gui.mmcr.controller.progress",      "进度: %s%%")))
    );

    private Translations() {}
}

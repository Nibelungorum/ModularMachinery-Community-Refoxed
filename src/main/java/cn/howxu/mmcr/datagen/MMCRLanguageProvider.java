package cn.howxu.mmcr.datagen;

import cn.howxu.mmcr.MMCR;
import net.minecraft.data.PackOutput;
import net.neoforged.neoforge.common.data.LanguageProvider;

import java.util.Map;

public final class MMCRLanguageProvider extends LanguageProvider {
    private static final Map<String, Map<String, String>> TRANSLATIONS = Map.of(
            "en_us", Map.of(
                    "itemGroup.mmcr", "Modular Machinery: Refoxed",
                    "block.mmcr.controller", "Machine Controller",
                    "block.mmcr.casing", "Machine Casing",
                    "block.mmcr.item_bus", "Item Bus",
                    "block.mmcr.fluid_hatch", "Fluid Hatch",
                    "block.mmcr.energy_hatch", "Energy Hatch"),
            "zh_cn", Map.of(
                    "itemGroup.mmcr", "模块化机械：Refoxed",
                    "block.mmcr.controller", "机器控制器",
                    "block.mmcr.casing", "机器外壳",
                    "block.mmcr.item_bus", "物品总线",
                    "block.mmcr.fluid_hatch", "流体仓",
                    "block.mmcr.energy_hatch", "能量仓"));

    private final String locale;

    public MMCRLanguageProvider(PackOutput output, String locale) {
        super(output, MMCR.MODID, locale);
        this.locale = locale;
    }

    @Override
    protected void addTranslations() {
        TRANSLATIONS.get(locale).forEach(this::add);
    }
}

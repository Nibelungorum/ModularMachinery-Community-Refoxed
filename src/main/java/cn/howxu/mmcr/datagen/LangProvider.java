package cn.howxu.mmcr.datagen;

import cn.howxu.mmcr.MMCR;
import net.minecraft.data.PackOutput;
import net.neoforged.neoforge.common.data.LanguageProvider;

import java.util.Map;

public final class LangProvider extends LanguageProvider {

    private final String locale;

    public LangProvider(PackOutput output, String locale) {
        super(output, MMCR.MODID, locale);
        this.locale = locale;
    }

    @Override
    protected void addTranslations() {
        Map<String, String> map = Translations.ALL.get(locale);
        if (map != null) map.forEach(this::add);
    }
}

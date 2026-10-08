package cn.howxu.mmcr.compat.extendedae.loaded.menu;

import appeng.helpers.InterfaceLogicHost;
import appeng.helpers.patternprovider.PatternProviderLogicHost;
import appeng.menu.implementations.MenuTypeBuilder;
import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.compat.appliedenergistics2.InterfaceScreenTitles;
import cn.howxu.mmcr.compat.appliedenergistics2.loaded.menu.PatternInterfaceMenu;
import cn.howxu.mmcr.compat.extendedae.loaded.kind.ExtendedInputInterfaceKind;
import cn.howxu.mmcr.compat.extendedae.loaded.kind.ExtendedOutputInterfaceKind;
import cn.howxu.mmcr.compat.extendedae.loaded.kind.ExtendedPatternInterfaceKind;
import cn.howxu.mmcr.compat.extendedae.loaded.kind.ExtendedStockingInputInterfaceKind;
import cn.howxu.mmcr.compat.extendedae.loaded.kind.OversizeInputInterfaceKind;
import cn.howxu.mmcr.compat.extendedae.loaded.kind.OversizeOutputInterfaceKind;
import cn.howxu.mmcr.internal.port.IOPortKind;
import java.util.function.BiConsumer;
import java.util.function.Supplier;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.MenuType;
import org.jetbrains.annotations.Nullable;

/**
 * Defines the extended, oversize and pattern interface types without changing native EAE menus.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class ExtendedAEMenuTypes {
    public static final MenuType<ExtendedInterfaceMenu> INTERFACE = MenuTypeBuilder
            .create(ExtendedInterfaceMenu::new, InterfaceLogicHost.class)
            .withMenuTitle(host -> InterfaceScreenTitles.titleFor(host, Component.empty()))
            .buildUnregistered(MMCR.id("eae_interface"));

    public static final MenuType<ExtendedInterfaceMenu> OVERSIZE = MenuTypeBuilder
            .create(ExtendedInterfaceMenu::new, InterfaceLogicHost.class)
            .withMenuTitle(host -> InterfaceScreenTitles.titleFor(host, Component.empty()))
            .buildUnregistered(MMCR.id("eae_oversize_interface"));

    public static final MenuType<PatternInterfaceMenu> PATTERN = MenuTypeBuilder
            .create(PatternInterfaceMenu::new, PatternProviderLogicHost.class)
            .withMenuTitle(host -> InterfaceScreenTitles.titleFor(host, Component.empty()))
            .buildUnregistered(MMCR.id("eae_pattern_interface"));

    private ExtendedAEMenuTypes() {
    }

    public static void register(BiConsumer<String, Supplier<? extends MenuType<?>>> registrar) {
        registrar.accept("eae_interface", () -> INTERFACE);
        registrar.accept("eae_oversize_interface", () -> OVERSIZE);
        registrar.accept("eae_pattern_interface", () -> PATTERN);
    }

    public static @Nullable MenuType<?> typeFor(IOPortKind kind) {
        if (kind == ExtendedPatternInterfaceKind.INSTANCE) return PATTERN;
        if (kind == OversizeInputInterfaceKind.INSTANCE
                || kind == OversizeOutputInterfaceKind.INSTANCE) return OVERSIZE;
        if (kind == ExtendedInputInterfaceKind.INSTANCE
                || kind == ExtendedStockingInputInterfaceKind.INSTANCE
                || kind == ExtendedOutputInterfaceKind.INSTANCE) return INTERFACE;
        return null;
    }
}

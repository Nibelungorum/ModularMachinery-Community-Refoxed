package cn.howxu.mmcr.compat.appliedenergistics2.loaded.menu;

import appeng.helpers.InterfaceLogicHost;
import appeng.helpers.patternprovider.PatternProviderLogicHost;
import appeng.menu.implementations.MenuTypeBuilder;
import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.compat.appliedenergistics2.InterfaceScreenTitles;
import cn.howxu.mmcr.compat.appliedenergistics2.loaded.kind.AsyncOutputInterfaceKind;
import cn.howxu.mmcr.compat.appliedenergistics2.loaded.kind.InputInterfaceKind;
import cn.howxu.mmcr.compat.appliedenergistics2.loaded.kind.OutputInterfaceKind;
import cn.howxu.mmcr.compat.appliedenergistics2.loaded.kind.PatternInterfaceKind;
import cn.howxu.mmcr.compat.appliedenergistics2.loaded.kind.StockingInterfaceKind;
import cn.howxu.mmcr.compat.extendedae.ExtendedAEContributorBootstrap;
import cn.howxu.mmcr.internal.port.IOPortKind;
import java.util.function.BiConsumer;
import java.util.function.Supplier;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.MenuType;

/**
 * Defines the AE2 interface types and routes every MMCR interface through its owning integration.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class AE2MenuTypes {
    public static final MenuType<AE2InterfaceMenu> INTERFACE = MenuTypeBuilder
            .create(AE2InterfaceMenu::new, InterfaceLogicHost.class)
            .withMenuTitle(host -> InterfaceScreenTitles.titleFor(host, Component.empty()))
            .buildUnregistered(MMCR.id("ae2_interface"));

    public static final MenuType<PatternInterfaceMenu> PATTERN = MenuTypeBuilder
            .create(PatternInterfaceMenu::new, PatternProviderLogicHost.class)
            .withMenuTitle(host -> InterfaceScreenTitles.titleFor(host, Component.empty()))
            .buildUnregistered(MMCR.id("ae2_pattern_interface"));

    private AE2MenuTypes() {
    }

    public static void register(BiConsumer<String, Supplier<? extends MenuType<?>>> registrar) {
        registrar.accept("ae2_interface", () -> INTERFACE);
        registrar.accept("ae2_pattern_interface", () -> PATTERN);
    }

    public static MenuType<?> typeFor(IOPortKind kind) {
        MenuType<?> extended = ExtendedAEContributorBootstrap.contributor().menuType(kind);
        if (extended != null) return extended;
        if (kind == PatternInterfaceKind.INSTANCE) return PATTERN;
        if (kind == InputInterfaceKind.INSTANCE || kind == StockingInterfaceKind.INSTANCE
                || kind == OutputInterfaceKind.INSTANCE || kind == AsyncOutputInterfaceKind.INSTANCE) {
            return INTERFACE;
        }
        throw new IllegalArgumentException("Unknown AE2 interface kind: " + kind.id());
    }
}

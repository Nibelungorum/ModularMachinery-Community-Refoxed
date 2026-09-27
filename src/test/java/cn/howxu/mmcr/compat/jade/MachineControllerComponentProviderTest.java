package cn.howxu.mmcr.compat.jade;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.internal.runtime.JadeTextState;
import cn.howxu.mmcr.test.TestBootstrap;
import net.minecraft.client.gui.layouts.LayoutElement;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.ITooltip;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies Jade only displays controller-owned information.
 *
 * @author howxu <dev@howxu.cn>
 */
class MachineControllerComponentProviderTest {

    @BeforeAll
    static void bootstrapMinecraft() throws Exception {
        TestBootstrap.bootstrap();
    }

    @Test
    void lineKeysDoNotIncludeMachineIdentifier() {
        CompoundTag tag = new CompoundTag();
        tag.putString("machine", "mmcr:machine");

        assertThat(MachineControllerComponentProvider.lineKeys(
                MachineControllerComponentProvider.Snapshot.from(tag)))
                .doesNotContain("machine");
    }

    @Test
    void lineKeysDoNotIncludeRecipe() {
        CompoundTag tag = new CompoundTag();
        tag.putBoolean("formed", true);
        tag.putString("activeRecipe", "mmcr:recipe");

        assertThat(MachineControllerComponentProvider.lineKeys(
                MachineControllerComponentProvider.Snapshot.from(tag)))
                .doesNotContain("recipe");
    }

    @Test
    void lineKeysOnlyIncludeStructureForTickMachine() {
        CompoundTag tag = new CompoundTag();
        tag.putBoolean("tickMachine", true);
        tag.putBoolean("formed", true);
        tag.putBoolean("active", true);
        tag.putString("activeRecipe", "mmcr:recipe");

        assertThat(MachineControllerComponentProvider.lineKeys(
                MachineControllerComponentProvider.Snapshot.from(tag)))
                .containsExactly("structure");
    }

    @Test
    void recipePoolLineFollowsStateForRecipeMachines() {
        CompoundTag tag = new CompoundTag();
        tag.putBoolean("formed", true);
        tag.putString("recipePool", "mmcr:jade_pool");

        MachineControllerComponentProvider.Snapshot snapshot =
                MachineControllerComponentProvider.Snapshot.from(tag);

        assertThat(snapshot.recipePoolId()).isEqualTo(MMCR.id("jade_pool"));
        assertThat(MachineControllerComponentProvider.lineKeys(snapshot))
                .containsExactly("structure", "state", "recipe_pool");
        assertThat(appendTooltip(tag).get(2).toString()).contains("mmcr:jade_pool");
    }

    @Test
    void appendTooltipAddsCustomLinesAfterTheStructureLineForTickMachines() {
        CompoundTag tag = new CompoundTag();
        tag.putBoolean("tickMachine", true);
        tag.putBoolean("formed", true);
        JadeTextState state = new JadeTextState();
        state.append(MMCR.id("custom"), Component.literal("custom value"));
        JadeTextCodec.write(tag, state.snapshot());

        List<Object> added = appendTooltip(tag);

        assertThat(MachineControllerComponentProvider.lineKeys(MachineControllerComponentProvider.Snapshot.from(tag)))
                .containsExactly("structure");
        assertThat(added).hasSize(2);
        assertThat(added.getLast()).isEqualTo(Component.literal("custom value"));
    }

    @Test
    void appendTooltipAddsCustomLinesAfterOrdinaryMachineLines() {
        CompoundTag tag = new CompoundTag();
        tag.putBoolean("formed", true);
        tag.putBoolean("active", true);
        tag.putString("activeRecipe", "mmcr:recipe");
        tag.putInt("tick", 2);
        tag.putInt("totalTick", 20);
        JadeTextState state = new JadeTextState();
        state.append(MMCR.id("custom"), Component.literal("custom value"));
        JadeTextCodec.write(tag, state.snapshot());

        List<Object> added = appendTooltip(tag);

        assertThat(MachineControllerComponentProvider.lineKeys(
                MachineControllerComponentProvider.Snapshot.from(tag)))
                .containsExactly("structure", "state", "progress");
        assertThat(added).hasSize(3);
        assertThat(added.getLast()).isEqualTo(Component.literal("custom value"));
    }

    private static List<Object> appendTooltip(CompoundTag serverData) {
        List<Object> added = new ArrayList<>();
        ITooltip tooltip = (ITooltip) Proxy.newProxyInstance(
                ITooltip.class.getClassLoader(), new Class<?>[]{ITooltip.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("add") && args != null && args.length >= 1
                            && (args[args.length - 1] instanceof Component
                                    || args[args.length - 1] instanceof LayoutElement)) {
                        added.add(args[args.length - 1]);
                        return null;
                    }
                    throw new UnsupportedOperationException(method.getName());
                });
        BlockAccessor accessor = (BlockAccessor) Proxy.newProxyInstance(
                BlockAccessor.class.getClassLoader(), new Class<?>[]{BlockAccessor.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("getServerData")) return serverData;
                    throw new UnsupportedOperationException(method.getName());
                });

        MachineControllerComponentProvider.INSTANCE.appendTooltip(tooltip, accessor, null);
        return added;
    }
}

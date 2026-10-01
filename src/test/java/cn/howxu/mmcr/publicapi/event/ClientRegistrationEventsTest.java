package cn.howxu.mmcr.publicapi.event;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.internal.api.facade.client.ClientRegistrationAdapters;
import cn.howxu.mmcr.publicapi.client.jei.RecipeInformation;
import cn.howxu.mmcr.publicapi.client.jei.Workstation;
import cn.howxu.mmcr.publicapi.client.render.ControllerRenderer;
import cn.howxu.mmcr.publicapi.registration.RegistrationException;
import cn.howxu.mmcr.test.TestBootstrap;
import java.util.Set;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Client public event contributions retain validation and defensive-copy semantics.
 * @author howxu <dev@howxu.cn>
 */
class ClientRegistrationEventsTest {
    @BeforeAll
    static void bootstrapMinecraft() throws Exception { TestBootstrap.bootstrap(); }

    @Test
    void renderer_registration_rejects_unknown_duplicate_and_late_contributions() {
        var id = MMCR.id("client_renderer_contract");
        var event = new RegisterControllerRenderersEvent(Set.of(id));
        ControllerRenderer renderer = (context, poses, buffers, light, overlay) -> { };
        assertThatThrownBy(() -> event.register(MMCR.id("unknown_renderer"), renderer))
                .isInstanceOf(RegistrationException.class).hasMessageContaining("unknown_renderer");
        event.register(id, renderer);
        assertThat(event.renderers().get(id)).isSameAs(renderer);
        assertThatThrownBy(() -> event.register(id, renderer)).isInstanceOf(RegistrationException.class);
        assertThatThrownBy(() -> event.renderers().clear()).isInstanceOf(UnsupportedOperationException.class);
        ClientRegistrationAdapters.freeze(event);
        assertThatThrownBy(() -> event.register(id, renderer)).isInstanceOf(RegistrationException.class);
    }

    @Test
    void workstation_stack_is_copied_across_registration_and_reads() {
        var pool = MMCR.id("client_workstation_pool");
        var event = new RegisterJeiWorkstationsEvent();
        var source = new ItemStack(Items.DIAMOND, 12);
        source.set(DataComponents.MAX_STACK_SIZE, 32);
        event.addRecipePoolWorkstation(pool, source);
        source.set(DataComponents.MAX_STACK_SIZE, 16);
        var entry = (Workstation.RecipePoolStack) event.entries().getFirst();
        var read = entry.workstation();
        read.set(DataComponents.MAX_STACK_SIZE, 8);
        assertThat(entry.workstation().get(DataComponents.MAX_STACK_SIZE)).isEqualTo(32);
        assertThat(entry.workstation().getCount()).isEqualTo(1);
        ClientRegistrationAdapters.freeze(event);
        assertThatThrownBy(() -> event.addMachineWorkstation(pool, pool)).isInstanceOf(RegistrationException.class);
        assertThatThrownBy(() -> event.entries().clear()).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void frozen_information_owns_nested_component_arguments_across_core_and_public_reads() {
        var pool = MMCR.id("client_component_information_pool");
        var source = informationArgument();
        var expectedArgument = informationArgument();
        var expected = Component.translatable("jei.mmcr.public_test", expectedArgument, "argument", 3);
        var event = new RegisterJeiRecipeInformationEvent();
        event.registerRecipePool(pool, "jei.mmcr.public_test", source, "argument", 3);
        ClientRegistrationAdapters.freeze(event);
        var core = ClientRegistrationAdapters.coreEntries(event).getFirst();
        var view = event.entries().getFirst();

        mutateInformationArgument(source);
        assertThat(core.component()).isEqualTo(expected);
        assertThat(view.component()).isEqualTo(expected);
        mutateInformationArgument((Component) view.arguments().getFirst());
        mutateInformationArgument((Component) core.arguments().getFirst());
        assertThat(core.arguments()).containsExactly(expectedArgument, "argument", 3);
        assertThat(view.arguments()).containsExactly(expectedArgument, "argument", 3);
        mutateInformationArgument((Component) ((TranslatableContents) view.component().getContents()).getArgs()[0]);
        mutateInformationArgument((Component) ((TranslatableContents) core.component().getContents()).getArgs()[0]);
        assertThat(ClientRegistrationAdapters.coreEntries(event).getFirst().component()).isEqualTo(expected);
        assertThat(event.entries().getFirst().component()).isEqualTo(expected);
        assertThatThrownBy(() -> event.registerRecipe(pool, "jei.mmcr.late_test", source))
                .isInstanceOf(RegistrationException.class);
    }

    private static MutableComponent informationArgument() {
        return Component.translatable("jei.mmcr.argument", Component.literal("nested"))
                .append(Component.literal("sibling"))
                .withStyle(style -> style.withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal("hover"))));
    }

    private static void mutateInformationArgument(Component argument) {
        ((MutableComponent) ((TranslatableContents) argument.getContents()).getArgs()[0]).append("changed nested");
        ((MutableComponent) argument.getSiblings().getFirst()).append("changed sibling");
        ((MutableComponent) argument.getStyle().getHoverEvent().getValue(HoverEvent.Action.SHOW_TEXT)).append("changed hover");
        ((MutableComponent) argument).append("changed root");
    }

    @Test
    void localized_information_preserves_target_and_arguments_and_freezes() {
        var pool = MMCR.id("client_information_pool");
        var event = new RegisterJeiRecipeInformationEvent();
        event.registerRecipePool(pool, "jei.mmcr.public_test", "argument", 3);
        var entry = event.entries().getFirst();
        assertThat(entry.target()).isEqualTo(RecipeInformation.Target.RECIPE_POOL);
        assertThat(entry.targetId()).isEqualTo(pool);
        assertThat(entry.translationKey()).isEqualTo("jei.mmcr.public_test");
        assertThat(entry.arguments()).containsExactly("argument", 3);
        assertThatThrownBy(() -> entry.arguments().clear()).isInstanceOf(UnsupportedOperationException.class);
        ClientRegistrationAdapters.freeze(event);
        assertThatThrownBy(() -> event.registerRecipe(pool, "jei.mmcr.late_test"))
                .isInstanceOf(RegistrationException.class);
        assertThatThrownBy(() -> new RegisterJeiRecipeInformationEvent().registerRecipe(pool, ""))
                .isInstanceOf(IllegalArgumentException.class);
    }
}

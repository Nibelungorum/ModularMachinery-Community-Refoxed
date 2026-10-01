package cn.howxu.mmcr.publicapi;

import cn.howxu.mmcr.internal.api.facade.registration.RegistrationAdapters;
import cn.howxu.mmcr.api.recipe.component.ComponentPredicate;
import cn.howxu.mmcr.internal.api.facade.recipe.ComponentAdapters;
import cn.howxu.mmcr.publicapi.event.RegisterMachineStructuresEvent;
import cn.howxu.mmcr.publicapi.recipe.component.ComponentConditions;
import cn.howxu.mmcr.publicapi.recipe.component.TextMatchMode;
import cn.howxu.mmcr.publicapi.structure.level.Levels;
import cn.howxu.mmcr.test.TestBootstrap;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.JsonOps;
import com.google.gson.JsonParser;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.network.chat.contents.SelectorContents;
import net.minecraft.network.chat.contents.NbtContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import java.util.Set;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

/** Public reads cannot mutate the core declaration through nested Component aliases.
 * @author howxu <dev@howxu.cn>
 */
class PublicComponentOwnershipTest {
    @BeforeAll
    static void bootstrap() throws Exception { TestBootstrap.bootstrap(); }

    private static MutableComponent name() {
        return Component.translatableWithFallback("test.ownership", "%s", Component.literal("argument").withStyle(ChatFormatting.GOLD))
                .append(Component.literal("sibling"))
                .withStyle(style -> style.withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal("hover"))));
    }

    private static void mutate(Component component) {
        ((MutableComponent) component.getSiblings().getFirst()).append("changed");
        var contents = (TranslatableContents) component.getContents();
        ((MutableComponent) contents.getArgs()[0]).append("changed");
        contents.getArgs()[0] = Component.literal("replaced argument");
        var hover = component.getStyle().getHoverEvent();
        ((MutableComponent) hover.getValue(HoverEvent.Action.SHOW_TEXT)).append("changed");
    }

    @Test
    void frozen_level_name_owns_source_and_every_read_including_siblings_arguments_and_hover() {
        var id = ResourceLocation.parse("test:owned_level");
        var source = name();
        var type = Levels.type(id, source);
        var event = new RegisterMachineStructuresEvent(Set.of());
        event.registerLevelType(type);
        RegistrationAdapters.freeze(event);
        var expected = name();
        mutate(source);
        for (int index = 0; index < 2; index++) {
            mutate(event.levelTypes().get(id).displayName());
            assertEquals(expected, event.levelTypes().get(id).displayName());
            assertEquals(expected, RegistrationAdapters.core(event).levelTypes().get(id).displayName());
        }
    }

    @Test
    void plain_and_full_conditions_own_source_and_each_read_without_facade_state() {
        for (var mode : TextMatchMode.values()) {
            var source = name();
            var condition = ComponentConditions.text(source, mode);
            var core = (ComponentPredicate.TextValue) ComponentAdapters.unwrap(condition);
            var candidate = new Dynamic<>(JsonOps.INSTANCE,
                    ComponentSerialization.CODEC.encodeStart(JsonOps.INSTANCE, name()).getOrThrow());
            assertTrue(condition.matches(candidate));
            mutate(source);
            assertEquals(name(), core.value());
            for (int index = 0; index < 2; index++) {
                var firstRead = condition.value();
                var secondRead = condition.value();
                assertNotSame(firstRead.getStyle().getHoverEvent(), secondRead.getStyle().getHoverEvent());
                mutate(firstRead);
                mutate(core.value());
                assertEquals(name(), secondRead);
                assertTrue(condition.matches(candidate));
                assertEquals(name(), condition.value());
            }
            var different = new Dynamic<>(JsonOps.INSTANCE,
                    ComponentSerialization.CODEC.encodeStart(JsonOps.INSTANCE, source).getOrThrow());
            assertFalse(condition.matches(different));
        }
    }

    @Test
    void selector_and_nbt_separators_are_owned_on_input_and_read() {
        for (String json : new String[] {
                "{\"selector\":\"@a\",\"separator\":{\"text\":\"separator\"}}",
                "{\"nbt\":\"name\",\"block\":\"~ ~ ~\",\"separator\":{\"text\":\"separator\"}}"}) {
            var source = ComponentSerialization.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(json)).getOrThrow();
            var mutableSource = MutableComponent.create(switch (source.getContents()) {
                case SelectorContents selector -> new SelectorContents(selector.getPattern(), Optional.of(Component.literal("separator")));
                case NbtContents nbt -> new NbtContents(nbt.getNbtPath(), nbt.isInterpreting(),
                        Optional.of(Component.literal("separator")), nbt.getDataSource());
                default -> throw new AssertionError("Expected separator contents");
            });
            for (var input : List.of(source, mutableSource)) {
                var type = Levels.type(ResourceLocation.parse("test:nested_separator"), input);
                var condition = ComponentConditions.text(input, TextMatchMode.FULL);
                // Independent baseline: never obtain the expected tree through the snapshot helper under test.
                var expected = ComponentSerialization.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(json)).getOrThrow();
                var candidate = new Dynamic<>(JsonOps.INSTANCE, JsonParser.parseString(json));
                assertTrue(condition.matches(candidate));
                // Codec-decoded MutableComponents can have immutable siblings, but their style is mutable.
                separator(input).withStyle(ChatFormatting.RED);
                if (input == mutableSource) separator(input).append("changed input");
                assertEquals(expected, type.displayName());
                assertEquals(expected, condition.value());
                for (int index = 0; index < 2; index++) {
                    var levelRead = type.displayName();
                    var conditionRead = condition.value();
                    assertNotSame(separator(input), separator(levelRead));
                    assertNotSame(separator(levelRead), separator(conditionRead));
                    separator(levelRead).append("changed level read").withStyle(ChatFormatting.BLUE);
                    separator(conditionRead).append("changed condition read").withStyle(ChatFormatting.GOLD);
                    assertEquals(expected, type.displayName());
                    assertEquals(expected, condition.value());
                    assertTrue(condition.matches(candidate));
                }
            }
        }
    }

    private static MutableComponent separator(Component value) {
        return (MutableComponent) switch (value.getContents()) {
            case SelectorContents selector -> selector.getSeparator().orElseThrow();
            case NbtContents nbt -> nbt.getSeparator().orElseThrow();
            default -> throw new AssertionError("Expected separator contents");
        };
    }

    @Test
    void equal_hover_entity_and_item_events_do_not_retain_source_or_getter_aliases() {
        for (boolean item : new boolean[] {false, true}) {
            var nested = Component.literal("nested hover");
            var stack = new ItemStack(Items.PAPER);
            stack.set(DataComponents.CUSTOM_NAME, nested);
            HoverEvent hover = item
                    ? new HoverEvent(HoverEvent.Action.SHOW_ITEM, new HoverEvent.ItemStackInfo(stack))
                    : new HoverEvent(HoverEvent.Action.SHOW_ENTITY, new HoverEvent.EntityTooltipInfo(EntityType.PIG, new UUID(0, 1), nested));
            var source = Component.literal("name").withStyle(style -> style.withBold(true).withHoverEvent(hover));
            var type = Levels.type(ResourceLocation.parse("test:hover_ownership"), source);
            var condition = ComponentConditions.text(source, TextMatchMode.FULL);
            var original = ComponentSerialization.CODEC.encodeStart(JsonOps.INSTANCE, source).getOrThrow();
            var candidate = new Dynamic<>(JsonOps.INSTANCE, original);
            nested.withStyle(ChatFormatting.RED);
            assertTrue(condition.matches(candidate));
            for (int index = 0; index < 2; index++) {
                var levelRead = type.displayName();
                var conditionRead = condition.value();
                assertNotSame(hover, levelRead.getStyle().getHoverEvent());
                assertNotSame(levelRead.getStyle().getHoverEvent(), conditionRead.getStyle().getHoverEvent());
                // A decoded item name can also have immutable siblings; editing style remains supported.
                hoverName(levelRead).withStyle(ChatFormatting.BLUE);
                hoverName(conditionRead).withStyle(ChatFormatting.GOLD);
                assertEquals(original, ComponentSerialization.CODEC.encodeStart(JsonOps.INSTANCE, type.displayName()).getOrThrow());
                assertEquals(original, ComponentSerialization.CODEC.encodeStart(JsonOps.INSTANCE, condition.value()).getOrThrow());
                assertTrue(condition.matches(candidate));
            }
        }
    }

    private static MutableComponent hoverName(Component component) {
        var hover = component.getStyle().getHoverEvent();
        if (hover.getAction() == HoverEvent.Action.SHOW_ENTITY) {
            return (MutableComponent) hover.getValue(HoverEvent.Action.SHOW_ENTITY).name.orElseThrow();
        }
        if (hover.getAction() == HoverEvent.Action.SHOW_ITEM) {
            return (MutableComponent) hover.getValue(HoverEvent.Action.SHOW_ITEM).getItemStack().get(DataComponents.CUSTOM_NAME);
        }
        throw new AssertionError("Expected entity or item hover");
    }
}

package org.nibelungorum.builtin;

import cn.howxu.mmcr.publicapi.Machines;
import cn.howxu.mmcr.publicapi.Structures;
import cn.howxu.mmcr.publicapi.data.DataKey;
import cn.howxu.mmcr.publicapi.event.RegisterControllerUiProtocolsEvent;
import cn.howxu.mmcr.publicapi.event.RegisterMachineDefinitionsEvent;
import cn.howxu.mmcr.publicapi.event.RegisterMachineStructuresEvent;
import cn.howxu.mmcr.publicapi.machine.MachineSpec;
import cn.howxu.mmcr.publicapi.presentation.TextScope;
import cn.howxu.mmcr.publicapi.structure.BlockConditions;
import cn.howxu.mmcr.publicapi.structure.StructureSpec;
import cn.howxu.mmcr.publicapi.ui.UiRequestType;
import cn.howxu.mmcr.publicapi.ui.UiResult;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

import static cn.howxu.mmcr.publicapi.ApiIds.id;
import static cn.howxu.mmcr.publicapi.structure.BlockConditions.block;

/**
 * @description: TODO
 * @author: HowXu
 * @date: 2026/8/23 11:23
 */
@EventBusSubscriber
public class MODERN_UI {

    public static final Identifier MACHINE_ID = id("modern_ui");
    public static final Identifier VALUE_LINE = id("modern_ui_value");
    public static final String VALUE_KEY = "value";
    public static final UiRequestType<SetValue, SetValue> SET_VALUE = UiRequestType.of(
            id("modern_ui_set_value"), 1, SetValue.CODEC, SetValue.CODEC);

    /** Pure request/response data, safe to encode from the Modern UI thread.
     * @author howxu <dev@howxu.cn>
     */
    public record SetValue(int value) {
        public static final StreamCodec<RegistryFriendlyByteBuf, SetValue> CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, SetValue::value, SetValue::new);
    }

    @SubscribeEvent
    public static void registerProtocols(RegisterControllerUiProtocolsEvent event) {
        event.registrar().request(MACHINE_ID, SET_VALUE, (context, request) -> {
            var storage = context.machine().dataStorage();
            if (storage == null) {
                return UiResult.reject(Component.translatable("gui.mmcr_test.modern_ui.unavailable"));
            }
            storage.set(VALUE_KEY, DataKey.of(request.value()));
            return UiResult.success(request);
        });
    }

    public static void registerDefinitions(RegisterMachineDefinitionsEvent event) {
        if (!event.definitions().containsKey(MACHINE_ID)) {
            MachineSpec machine = Machines
                    .machine(MACHINE_ID)
                    .recipePool(id("alloy_furnace"))
                    .displayNameKey("machine.mmcr_test.modern_ui")
                    .appearance(appearance -> appearance.machineBasicBlock(Identifier.parse("minecraft:bricks")))
                    .recipeBehavior(behavior -> behavior.postServerTick(context -> {
                        var storage = context.dataStorage();
                        if (storage == null) {
                            context.screenText().append(TextScope.CONTROLLER, VALUE_LINE,
                                    Component.translatable("gui.mmcr_test.modern_ui.unavailable"));
                            return;
                        }
                        if (!storage.contains(VALUE_KEY)) storage.set(VALUE_KEY, DataKey.of(0));
                        int value = storage.get(VALUE_KEY).flatMap(DataKey::asInt).orElse(0);
                        context.screenText().append(TextScope.CONTROLLER, VALUE_LINE,
                                Component.translatable("gui.mmcr_test.modern_ui.controller_value", value));
                    }))
                    .build();
            event.registerMachine(machine);
        }
    }

    @SubscribeEvent
    public static void registerStructures(RegisterMachineStructuresEvent event) {

        if (!event.structures().containsKey(MACHINE_ID)) {
            StructureSpec structure = Structures
                    .structure()
                    .fullStructure(s -> s
                            .pattern(p -> p
                                    .layer("XXX", "XIX", "XXX")
                                    .layer("XMX", "I I", "XMX")
                                    .layer("XXX", "XCX", "XXX")
                                    .where('X', block(Blocks.BRICKS))
                                    .where('I', BlockConditions.any(BlockConditions.ports(), BlockConditions.dataStorage()))
                                    .where('M', block(Blocks.BLAST_FURNACE))
                                    .controller('C')
                            )
                        )
                    .build(MACHINE_ID);
            event.registerStructure(structure);
        }
    }

}

package org.nibelungorum.builtin;

import cn.howxu.mmcr.publicapi.Machines;
import cn.howxu.mmcr.publicapi.Structures;
import cn.howxu.mmcr.publicapi.data.DataKey;
import cn.howxu.mmcr.publicapi.data.DataStore;
import cn.howxu.mmcr.publicapi.network.Networks;
import cn.howxu.mmcr.publicapi.network.NetworkPortView;
import cn.howxu.mmcr.publicapi.network.RequestPayload;
import cn.howxu.mmcr.publicapi.network.RequestDetails;
import cn.howxu.mmcr.publicapi.presentation.TextScope;
import cn.howxu.mmcr.publicapi.behavior.TickContext;
import cn.howxu.mmcr.publicapi.event.RegisterMachineDefinitionsEvent;
import cn.howxu.mmcr.publicapi.event.RegisterMachineStructuresEvent;
import cn.howxu.mmcr.publicapi.machine.MachineSpec;
import cn.howxu.mmcr.publicapi.structure.BlockConditions;
import cn.howxu.mmcr.publicapi.structure.StructureSpec;
import cn.howxu.mmcr.publicapi.recipe.IoDirection;
import cn.howxu.mmcr.publicapi.recipe.requirement.Requirements;
import cn.howxu.mmcr.publicapi.runtime.IoTransaction;
import java.util.ArrayList;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

import java.util.HashSet;
import java.util.Set;

import static cn.howxu.mmcr.publicapi.structure.BlockConditions.any;
import static cn.howxu.mmcr.publicapi.structure.BlockConditions.block;
import static cn.howxu.mmcr.publicapi.ApiIds.id;
import static org.nibelungorum.builtin.NETWORK_PRODUCER_MACHINE.NETWORK_CENTER_MACHINE;
import static org.nibelungorum.builtin.NETWORK_PRODUCER_MACHINE.REPORT_POWER;

/**
 * @author howxu <dev@howxu.cn>
 */
@EventBusSubscriber
public class NETWORK_CENTER_MACHINE {

    private static final ResourceLocation CENTER_POWER = id("center_power");
    private static final ResourceLocation CENTER_COUNT = id("center_count");
    private static final ResourceLocation CENTER_FE = id("center_fe");

    public static void registerDefinitions(RegisterMachineDefinitionsEvent event) {
        if (!event.definitions().containsKey(NETWORK_CENTER_MACHINE)) {
            MachineSpec machine = Machines
                    .machine(NETWORK_CENTER_MACHINE)
                    .recipePool(NETWORK_CENTER_MACHINE)
                    .displayNameKey("machine.mmcr.network_center_machine")
                    .appearance(a -> a.machineBasicBlock(ResourceLocation.parse("minecraft:black_wool")))
                    .networkInterface(1, 16)
                    .allowNetworkMachine(NETWORK_PRODUCER_MACHINE.NETWORK_PRODUCER_MACHINE)
                    .requestProcess(REPORT_POWER, (RequestPayload body, RequestDetails request,
                                                   DataStore senderStorage, DataStore receiverStorage) -> {
                        if (receiverStorage == null) return;
                        double reported = body.get("power").flatMap(value -> value.asDouble()).orElse(0.0);
                        long hash = request.peer().hash();
                        receiverStorage.set("power_" + hash, DataKey.of(reported));
                    })
                    .tickBehavior(behavior -> behavior.serverTick((TickContext context) -> {
                        DataStore storage = context.dataStorage();
                        if (storage == null) return;

                        IoTransaction energyPlan = context.ioPlan();
                        energyPlan.addInput(Requirements.energy(IoDirection.INPUT, 200));
                        var energySim = energyPlan.simulate();
                        boolean energyOk = energySim.energySatisfied() && energyPlan.commit().successful();

                        int liveCount = 0;
                        Set<String> connectedHashes = new HashSet<>();
                        var interfaces = Networks.interfaces(context);
                        NetworkPortView iface = interfaces != null && !interfaces.isEmpty() ? interfaces.get(0) : null;
                        if (iface != null) {
                            for (var target : iface.connections()) {
                                liveCount = liveCount + 1;
                                connectedHashes.add(String.valueOf(target.hash()));
                            }
                        }

                        var staleKeys = new ArrayList<String>();
                        if (iface != null) {
                            for (var entry : storage.values().entrySet()) {
                                String keyString = entry.getKey();
                                if (keyString.startsWith("power_")
                                        && !connectedHashes.contains(keyString.substring("power_".length()))) {
                                    staleKeys.add(keyString);
                                }
                            }
                        }
                        for (var key : staleKeys) storage.remove(key);

                        double total = 0;
                        for (var entry : storage.values().entrySet()) {
                            if (entry.getKey().startsWith("power_")) {
                                total = total + entry.getValue().asDouble().orElse(0.0);
                            }
                        }

                        int count = liveCount;

                        context.screenText().append(TextScope.OPERATION, CENTER_POWER,
                                Component.literal("Total Power: " + total + " tfps"));
                        context.screenText().append(TextScope.OPERATION, CENTER_COUNT,
                                Component.literal("Connected Devices: " + count));
                        context.screenText().append(TextScope.OPERATION, CENTER_FE,
                                Component.literal(energyOk ? "Energy: OK" : "Energy: LOW"));

                        context.jadeText().append(CENTER_POWER,
                                Component.literal("Total Power: " + total + " tfps"));
                        context.jadeText().append(CENTER_COUNT,
                                Component.literal("Connected Devices: " + count + " producers"));
                    }))
                    .build();
            event.registerMachine(machine);
        }
    }

    @SubscribeEvent
    public static void registerStructures(RegisterMachineStructuresEvent event) {
        if (!event.structures().containsKey(NETWORK_CENTER_MACHINE)) {
            StructureSpec structure = Structures
                    .structure()
                    .fullStructure(s -> s
                            .pattern(p -> p
                                    .layer("XXXX", "XAAX", "XXXX")
                                    .layer("XXXX", "A  A", "XXXX")
                                    .layer("XXXX", "A  A", "XXXX")
                                    .layer("XXXX", "XCAX", "XXXX")
                                    .where('X', block(Blocks.BLACK_WOOL))
                                    .where('A', any(
                                            BlockConditions.energyInput(),
                                            BlockConditions.networkInterface(),
                                            BlockConditions.dataStorage(),
                                            block(Blocks.RED_TERRACOTTA)
                                    ))
                                    .controller('C')
                            )
                    )
                    .build(NETWORK_CENTER_MACHINE);
            event.registerStructure(structure);
        }
    }
}

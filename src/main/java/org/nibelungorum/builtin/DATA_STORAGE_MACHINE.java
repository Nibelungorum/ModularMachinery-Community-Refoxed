package org.nibelungorum.builtin;

import cn.howxu.mmcr.publicapi.Machines;
import cn.howxu.mmcr.publicapi.Structures;
import cn.howxu.mmcr.publicapi.data.DataKey;
import cn.howxu.mmcr.publicapi.data.DataStore;
import cn.howxu.mmcr.publicapi.ReadableNumber;
import cn.howxu.mmcr.publicapi.presentation.TextScope;
import cn.howxu.mmcr.publicapi.behavior.TickContext;
import cn.howxu.mmcr.publicapi.event.RegisterMachineDefinitionsEvent;
import cn.howxu.mmcr.publicapi.event.RegisterMachineStructuresEvent;
import cn.howxu.mmcr.publicapi.machine.MachineSpec;
import cn.howxu.mmcr.publicapi.structure.BlockConditions;
import cn.howxu.mmcr.publicapi.structure.StructureSpec;
import cn.howxu.mmcr.publicapi.runtime.OutputMode;
import cn.howxu.mmcr.publicapi.runtime.IoTransaction;
import cn.howxu.mmcr.publicapi.recipe.IoDirection;
import cn.howxu.mmcr.publicapi.recipe.requirement.Requirements;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

import java.math.BigInteger;

import static cn.howxu.mmcr.publicapi.structure.BlockConditions.any;
import static cn.howxu.mmcr.publicapi.structure.BlockConditions.block;
import static cn.howxu.mmcr.publicapi.ApiIds.id;

/**
 * @author howxu <dev@howxu.cn>
 */
@EventBusSubscriber
public class DATA_STORAGE_MACHINE {

    private static final ResourceLocation DATA_STORAGE_MACHINE = id("data_storage_machine");
    private static final ResourceLocation FE_STATUS = id("fe_storage_status");

    public static void registerDefinitions(RegisterMachineDefinitionsEvent event) {
        if (!event.definitions().containsKey(DATA_STORAGE_MACHINE)) {
            MachineSpec machine = Machines
                    .machine(DATA_STORAGE_MACHINE)
                    .recipePool(DATA_STORAGE_MACHINE)
                    .displayNameKey("machine.mmcr.data_storage_machine")
                    .appearance(a -> a.machineBasicBlock(ResourceLocation.parse("minecraft:crying_obsidian")))
                    .tickBehavior(behavior -> behavior.serverTick((TickContext context) -> {
                        DataStore storage = context.dataStorage();
                        if (storage == null) return;

                        BigInteger stored = BigInteger.ZERO;
                        var saved = storage.get("energy");
                        if (saved.isPresent()) {
                            stored = saved.get().asBigInteger().orElse(BigInteger.ZERO);
                        }

                        if (context.isDue(5)) {
                            int available = (int) Math.min(context.ioView().energyInput(), Integer.MAX_VALUE);
                            int low = 0;
                            int high = available;

                            while (low < high) {
                                int candidate = low + (int) Math.ceil((high - low) / 2.0);

                                IoTransaction probe = context.ioPlan();
                                probe.addInput(Requirements.energy(IoDirection.INPUT, candidate));

                                if (probe.simulate().energySatisfied()) {
                                    low = candidate;
                                } else {
                                    high = candidate - 1;
                                }
                            }

                            if (low > 0) {
                                IoTransaction inputPlan = context.ioPlan();
                                inputPlan.addInput(Requirements.energy(IoDirection.INPUT, low));

                                BigInteger next = stored.add(BigInteger.valueOf(low));
                                var inputSimulation = inputPlan.simulate();

                                if (inputSimulation.energySatisfied() && inputPlan.commitData(transaction ->
                                        storage.set("energy", DataKey.of(next), transaction)).successful()) {
                                    stored = next;
                                }
                            }
                        }

                        if (context.isDue(5)) {
                            long outputCapacity = context.ioView().energyOutputCapacity();

                            if (outputCapacity > 0 && stored.signum() > 0) {
                                BigInteger requestedBig = stored.min(
                                        BigInteger.valueOf(Math.min(outputCapacity, Integer.MAX_VALUE)));
                                int requested = requestedBig.intValue();

                                if (requested > 0) {
                                    IoTransaction outputPlan = context.ioPlan();
                                    outputPlan.addOutput(
                                            Requirements.energy(IoDirection.OUTPUT, requested),
                                            OutputMode.ALLOW_PARTIAL);

                                    var simulation = outputPlan.simulate();
                                    var outputs = simulation.outputs();

                                    if (!outputs.isEmpty()) {
                                        long accepted = outputs.get(0).accepted();

                                        if (accepted > 0) {
                                            BigInteger finalStored = stored.subtract(BigInteger.valueOf(accepted));

                                        if (outputPlan.commitData(transaction ->
                                                storage.set("energy", DataKey.of(finalStored), transaction)).successful()) {
                                                stored = finalStored;
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        if (stored.signum() == 0) {
                            context.screenText().append(
                                    TextScope.OPERATION,
                                    FE_STATUS,
                                    Component.literal("No FE stored."));
                            return;
                        }
                        context.screenText().append(
                                TextScope.OPERATION,
                                FE_STATUS,
                                Component.literal("FE stored: " + ReadableNumber.formatCompact(stored)));
                    }))
                    .build();
            event.registerMachine(machine);
        }
    }

    @SubscribeEvent
    public static void registerStructures(RegisterMachineStructuresEvent event) {
        if (!event.structures().containsKey(DATA_STORAGE_MACHINE)) {
            StructureSpec structure = Structures
                    .structure()
                    .fullStructure(s -> s
                            .pattern(p -> p
                                    .layer("         ", "         ", "         ", "   AAA   ", "   ABA   ", "   AAA   ", "         ", "         ", "         ")
                                    .layer("         ", "         ", "  AAAAA  ", "  AXXXA  ", "  AXXXA  ", "  AXXXA  ", "  AAAAA  ", "         ", "         ")
                                    .layer("         ", "  AAAAA  ", " AXXXXXA ", " AXXXXXA ", " AXXXXXA ", " AXXXXXA ", " AXXXXXA ", "  AAAAA  ", "         ")
                                    .layer("   AAA   ", "  AXXXA  ", " AXXXXXA ", "AXXXXXXXA", "AXXXXXXXA", "AXXXXXXXA", " AXXXXXA ", "  AXXXA  ", "   AAA   ")
                                    .layer("   ABA   ", "  AXXXA  ", " AXXXXXA ", "AXXXXXXXA", "BXXXDXXXB", "AXXXXXXXA", " AXXXXXA ", "  AXXXA  ", "   ABA   ")
                                    .layer("   AAA   ", "  AXXXA  ", " AXXXXXA ", "AXXXXXXXA", "AXXXXXXXA", "AXXXXXXXA", " AXXXXXA ", "  AXXXA  ", "   AAA   ")
                                    .layer("         ", "  AAAAA  ", " AXXXXXA ", " AXXXXXA ", " AXXXXXA ", " AXXXXXA ", " AXXXXXA ", "  AAAAA  ", "         ")
                                    .layer("         ", "         ", "  AAAAA  ", "  AXXXA  ", "  AXXXA  ", "  AXXXA  ", "  AAAAA  ", "         ", "         ")
                                    .layer("         ", "         ", "         ", "   AAA   ", "   ACA   ", "   AAA   ", "         ", "         ", "         ")
                                    .where('X', block(Blocks.REDSTONE_BLOCK))
                                    .where('A', block(Blocks.CRYING_OBSIDIAN))
                                    .where('B', any(
                                            BlockConditions.energyInput(),
                                            BlockConditions.energyOutput()
                                    ))
                                    .where('D', BlockConditions.dataStorage())
                                    .controller('C')
                            )
                    )
                    .build(DATA_STORAGE_MACHINE);
            event.registerStructure(structure);
        }
    }
}

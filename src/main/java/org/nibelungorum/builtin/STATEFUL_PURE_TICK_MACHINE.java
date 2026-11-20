package org.nibelungorum.builtin;

import cn.howxu.mmcr.publicapi.Machines;
import cn.howxu.mmcr.publicapi.Structures;
import cn.howxu.mmcr.publicapi.behavior.TickContext;
import cn.howxu.mmcr.publicapi.event.RegisterMachineDefinitionsEvent;
import cn.howxu.mmcr.publicapi.event.RegisterMachineStructuresEvent;
import cn.howxu.mmcr.publicapi.presentation.ControllerTexts;
import cn.howxu.mmcr.publicapi.presentation.TextScope;
import cn.howxu.mmcr.publicapi.recipe.IoDirection;
import cn.howxu.mmcr.publicapi.recipe.IoValues;
import cn.howxu.mmcr.publicapi.recipe.requirement.Requirements;
import cn.howxu.mmcr.publicapi.runtime.IoTransaction;
import cn.howxu.mmcr.publicapi.structure.BlockConditions;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

import java.util.function.Consumer;

import static cn.howxu.mmcr.publicapi.ApiIds.id;
import static cn.howxu.mmcr.publicapi.structure.BlockConditions.any;
import static cn.howxu.mmcr.publicapi.structure.BlockConditions.block;

/** Factory counterpart to {@link PURE_TICK_MACHINE}: every controller gets its own tick handler.
 * @author howxu <dev@howxu.cn>
 */
@EventBusSubscriber
public final class STATEFUL_PURE_TICK_MACHINE {
    private static final ResourceLocation MACHINE_ID = id("stateful_pure_tick_machine");
    private static final ResourceLocation FE_STATUS = id("fe_status");
    private static final ResourceLocation ITEM_STATUS = id("pure_tick_status");
    private static final ResourceLocation COMPLETED_BATCHES = id("completed_batches");

    public static void registerDefinitions(RegisterMachineDefinitionsEvent event) {
        if (event.definitions().containsKey(MACHINE_ID)) return;
        ControllerTexts.register(MACHINE_ID, context -> {
            context.screenText().append(TextScope.CONTROLLER, FE_STATUS,
                    Component.translatable("gui.mmcr_test.stateful_pure_tick.needs_energy"));
            context.screenText().append(TextScope.CONTROLLER, ITEM_STATUS,
                    Component.translatable("gui.mmcr_test.stateful_pure_tick.needs_iron"));
        });
        event.registerMachine(Machines.machine(MACHINE_ID)
                .recipePool(MACHINE_ID)
                .displayNameKey("machine.mmcr.stateful_pure_tick_machine")
                .appearance(a -> a.machineBasicBlock(ResourceLocation.parse("minecraft:green_terracotta")))
                .allowMultithreading()
                .maxParallelism(Integer.MAX_VALUE)
                .tickBehavior(hooks -> hooks.serverTick(CountingTick::new))
                .build());
    }

    /** The counter lives until the controller unloads. Use {@link TickContext#dataStorage()} for persistence.
     * @author howxu <dev@howxu.cn>
     */
    private static final class CountingTick implements Consumer<TickContext> {
        private long completedBatches;

        @Override
        public void accept(TickContext context) {
            if (!context.isDue(40)) return;
            if (consumeEnergy(context)) {
                strikeNearbyPlayers(context);
                if (convertIngot(context)) {
                    completedBatches++;
                    context.screenText().replace(ITEM_STATUS,
                            Component.translatable("gui.mmcr_test.stateful_pure_tick.converted"));
                }
            }
            context.screenText().append(TextScope.CONTROLLER, COMPLETED_BATCHES,
                    Component.translatable("gui.mmcr_test.stateful_pure_tick.completed_batches", completedBatches));
        }

        private boolean consumeEnergy(TickContext context) {
            IoTransaction energy = context.ioPlan().addInput(Requirements.energy(IoDirection.INPUT, 10));
            if (!energy.simulate().energySatisfied()) {
                context.screenText().replace(FE_STATUS, Component.translatable("gui.mmcr_test.stateful_pure_tick.needs_energy"));
                return false;
            }
            if (!energy.commit().successful()) {
                context.screenText().replace(FE_STATUS, Component.translatable("gui.mmcr_test.stateful_pure_tick.energy_error"));
                return false;
            }
            context.screenText().replace(FE_STATUS, Component.translatable("gui.mmcr_test.stateful_pure_tick.powered"));
            return true;
        }

        private void strikeNearbyPlayers(TickContext context) {
            var level = context.level();
            var pos = context.controllerPos();
            var area = new AABB(pos.getX() - 1, level.getMinBuildHeight(), pos.getZ() - 1,
                    pos.getX() + 2, level.getMaxBuildHeight(), pos.getZ() + 2);
            for (Player player : level.getEntitiesOfClass(Player.class, area)) {
                LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(level);
                if (bolt != null) {
                    bolt.setPos(player.getX(), player.getY(), player.getZ());
                    bolt.setVisualOnly(false);
                    level.addFreshEntity(bolt);
                }
            }
        }

        private boolean convertIngot(TickContext context) {
            IoTransaction plan = context.ioPlan()
                    .addInput(Requirements.itemInput(IoValues.itemInput(Ingredient.of(Items.IRON_INGOT), 1)))
                    .add(Requirements.itemOutput(IoValues.itemOutput(new ItemStack(Items.GOLD_NUGGET))));
            var simulation = plan.simulate();
            if (!simulation.inputsSatisfied()) return false;
            for (var output : simulation.outputs()) {
                if (output.accepted() < output.requested()) return false;
            }
            return plan.commit().successful();
        }
    }

    @SubscribeEvent
    public static void registerStructures(RegisterMachineStructuresEvent event) {
        if (event.structures().containsKey(MACHINE_ID)) return;
        event.registerStructure(Structures.structure()
                .fullStructure(s -> s.pattern(p -> p
                        .layer("XXX", "AAA", "XXX")
                        .layer("XXX", "A A", "X X")
                        .layer("XXX", "ACA", "XXX")
                        .where('X', block(Blocks.GREEN_TERRACOTTA))
                        .where('A', any(BlockConditions.itemInput(), BlockConditions.itemOutput(),
                                BlockConditions.energyInput(), BlockConditions.parallelControllers(),
                                BlockConditions.factoryController(), block(Blocks.GREEN_WOOL)))
                        .controller('C')))
                .build(MACHINE_ID));
    }
}

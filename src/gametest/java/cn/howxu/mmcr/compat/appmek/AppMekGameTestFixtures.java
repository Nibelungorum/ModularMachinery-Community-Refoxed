package cn.howxu.mmcr.compat.appmek;

import appeng.helpers.externalstorage.GenericStackInv;
import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.MEStorage;
import appeng.api.networking.GridHelper;
import appeng.api.networking.IGridConnection;
import appeng.me.helpers.IGridConnectedBlockEntity;
import appeng.blockentity.storage.MEChestBlockEntity;
import appeng.blockentity.networking.CreativeEnergyCellBlockEntity;
import appeng.core.definitions.AEBlocks;
import me.ramidzkh.mekae2.AMItems;
import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.machine.BlockArray;
import cn.howxu.mmcr.api.machine.BlockPredicate;
import cn.howxu.mmcr.api.machine.DynamicMachine;
import cn.howxu.mmcr.api.machine.MachineRegistry;
import cn.howxu.mmcr.api.recipe.MachineOutput;
import cn.howxu.mmcr.api.recipe.MachineRecipe;
import cn.howxu.mmcr.api.recipe.RecipeRegistry;
import cn.howxu.mmcr.api.recipe.requirement.MachineRequirement;
import cn.howxu.mmcr.api.compat.mekanism.ChemicalIngredient;
import cn.howxu.mmcr.compat.mekanism.loaded.LoadedChemicalRequirement;
import cn.howxu.mmcr.internal.block.MachineControllerBlock;
import cn.howxu.mmcr.internal.tile.IOPortBlockEntity;
import cn.howxu.mmcr.internal.tile.ItemBusBlockEntity;
import cn.howxu.mmcr.internal.tile.MachineControllerBlockEntity;
import cn.howxu.mmcr.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import me.ramidzkh.mekae2.ae2.MekanismKeyType;
import mekanism.api.MekanismAPI;
import mekanism.api.chemical.ChemicalStack;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.chat.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.Set;

/**
 * Runtime-backed chemical fixtures for ME integration tests.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class AppMekGameTestFixtures {
    private AppMekGameTestFixtures() {}

    public static ChemicalStack chemical(String path, long amount) {
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath("mekanism", path);
        var holder = MekanismAPI.CHEMICAL_REGISTRY.getHolder(
                ResourceKey.create(MekanismAPI.CHEMICAL_REGISTRY_NAME, id)).orElseThrow();
        return new ChemicalStack(holder, amount);
    }

    public static GenericStackInv inventory(int slots, long capacity) {
        GenericStackInv inventory = new GenericStackInv(null, GenericStackInv.Mode.STORAGE, slots);
        inventory.setCapacity(MekanismKeyType.TYPE, capacity);
        return inventory;
    }

    public static ChemicalMachine createChemicalMachine(GameTestHelper helper, String name, String inputId, long amount) {
        BlockPos controllerPos = new BlockPos(1, 1, 1);
        BlockPos inputPos = controllerPos.above();
        BlockPos outputPos = controllerPos.below();
        helper.setBlock(inputPos, ModBlocks.BLOCKS.get(inputId).get().defaultBlockState());
        helper.setBlock(outputPos, ModBlocks.BLOCKS.get("item_output_bus").get().defaultBlockState());
        helper.setBlock(controllerPos, ModBlocks.controllerFor(MMCR.id("test_cube")).get().defaultBlockState()
                .setValue(MachineControllerBlock.FACING, Direction.SOUTH));
        ResourceLocation id = MMCR.id(name);
        DynamicMachine machine = new DynamicMachine(id, "Chemical integration test", new BlockArray(Map.of(
                new BlockPos(0, 1, 0), new BlockPredicate.OfBlock(ModBlocks.BLOCKS.get(inputId).get()),
                new BlockPos(0, -1, 0), new BlockPredicate.OfBlock(ModBlocks.BLOCKS.get("item_output_bus").get()))));
        if (!MachineRegistry.containsStatic(id)) MachineRegistry.register(machine);
        RecipeRegistry.registerStatic(MachineRecipe.fromCanonical(MMCR.id(name + "_recipe"), id, 2,
                List.of(LoadedChemicalRequirement.input(ChemicalIngredient.chemical(
                        ResourceLocation.fromNamespaceAndPath("mekanism", "oxygen"), amount)),
                        MachineRequirement.itemOutput(new ItemStack(Items.DIAMOND))),
                List.of(new MachineOutput.ItemOutput(new ItemStack(Items.DIAMOND), 1F)), List.of(), 0, 1,
                false, false, false, Set.of()));
        MachineControllerBlockEntity controller = helper.getBlockEntity(controllerPos);
        controller.setMachine(machine);
        controller.setStructureCheckIntervalForTesting(1);
        controller.requestImmediateStructureCheck();
        return new ChemicalMachine(controller, helper.getBlockEntity(inputPos), helper.getBlockEntity(outputPos));
    }

    /** @author howxu <dev@howxu.cn> */
    public record ChemicalMachine(MachineControllerBlockEntity controller, IOPortBlockEntity input, ItemBusBlockEntity output) {}

    public static ChemicalNetwork createChemicalNetwork(GameTestHelper helper, BlockPos portPos) {
        BlockPos chestPos = new BlockPos(4, 1, 1);
        BlockPos energyPos = new BlockPos(4, 1, 3);
        helper.setBlock(chestPos, AEBlocks.ME_CHEST.block().defaultBlockState());
        helper.setBlock(energyPos, AEBlocks.CREATIVE_ENERGY_CELL.block().defaultBlockState());
        MEChestBlockEntity chest = helper.getBlockEntity(chestPos);
        chest.setCell(new ItemStack(AMItems.CHEMICAL_CELL_1K.get()));
        return new ChemicalNetwork(helper.getBlockEntity(portPos), chest, helper.getBlockEntity(energyPos));
    }

    /** State-driven real chemical cell network fixture. @author howxu <dev@howxu.cn> */
    public static final class ChemicalNetwork {
        private final IGridConnectedBlockEntity port;
        private final MEChestBlockEntity chest;
        private final CreativeEnergyCellBlockEntity energy;
        private IGridConnection chestConnection;
        private IGridConnection energyConnection;

        private ChemicalNetwork(IGridConnectedBlockEntity port, MEChestBlockEntity chest, CreativeEnergyCellBlockEntity energy) {
            this.port = port;
            this.chest = chest;
            this.energy = energy;
        }

        public void connectWhenReady(GameTestHelper helper) {
            helper.assertTrue(port.getMainNode().getNode() != null && chest.getMainNode().getNode() != null
                    && energy.getMainNode().getNode() != null, "Chemical network nodes initialize before connection");
            if (chestConnection == null) chestConnection = GridHelper.createConnection(port.getMainNode().getNode(), chest.getMainNode().getNode());
            if (energyConnection == null) energyConnection = GridHelper.createConnection(port.getMainNode().getNode(), energy.getMainNode().getNode());
            helper.assertTrue(port.getMainNode().isActive(), "Chemical interface becomes active on the real ME cell network");
        }

        public MEStorage storage() { return chest.getInventory(); }

        public void disconnect() {
            if (chestConnection != null) chestConnection.destroy();
            if (energyConnection != null) energyConnection.destroy();
            chestConnection = null;
            energyConnection = null;
        }
    }

    /** @author howxu <dev@howxu.cn> */
    public static final class LimitedStorage implements MEStorage {
        private final long limit;
        private final Map<AEKey, Long> stacks = new HashMap<>();

        public LimitedStorage(long limit) { this.limit = limit; }

        public long amount(AEKey key) { return stacks.getOrDefault(key, 0L); }

        @Override public long insert(AEKey key, long amount, Actionable mode, IActionSource source) {
            MEStorage.checkPreconditions(key, amount, mode, source);
            long stored = stacks.values().stream().mapToLong(Long::longValue).sum();
            long accepted = Math.min(amount, limit - stored);
            if (mode == Actionable.MODULATE && accepted > 0L) stacks.merge(key, accepted, Long::sum);
            return accepted;
        }

        @Override public long extract(AEKey key, long amount, Actionable mode, IActionSource source) {
            MEStorage.checkPreconditions(key, amount, mode, source);
            long stored = amount(key);
            long extracted = Math.min(amount, stored);
            if (mode == Actionable.MODULATE && extracted > 0L) {
                if (stored == extracted) stacks.remove(key);
                else stacks.put(key, stored - extracted);
            }
            return extracted;
        }

        @Override public void getAvailableStacks(KeyCounter out) { stacks.forEach(out::add); }
        @Override public Component getDescription() { return Component.empty(); }
    }
}

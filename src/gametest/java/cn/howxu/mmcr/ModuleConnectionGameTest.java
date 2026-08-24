package cn.howxu.mmcr;

import cn.howxu.mmcr.api.machine.BlockArray;
import cn.howxu.mmcr.api.machine.BlockPredicate;
import cn.howxu.mmcr.api.machine.DynamicMachine;
import cn.howxu.mmcr.api.machine.MachineRole;
import cn.howxu.mmcr.api.machine.MachineRegistry;
import cn.howxu.mmcr.internal.block.MachineControllerBlock;
import cn.howxu.mmcr.internal.multiblock.ModuleConnectionCoordinator;
import cn.howxu.mmcr.internal.tile.MachineControllerBlockEntity;
import cn.howxu.mmcr.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Blocks;

import net.minecraft.server.level.ServerLevel;

import java.util.Map;
import java.util.Set;

/**
 * End-to-end host and module connection regressions.
 *
 * @author howxu <dev@howxu.cn>
 */
public class ModuleConnectionGameTest {
    private static final Identifier HOST_ID = MMCR.id("gametest_module_host");
    private static final Identifier MODULE_ID = MMCR.id("gametest_module");

    public void hostFormsWithNoInstalledModules(GameTestHelper helper) {
        Fixture fixture = placeFixture(helper, false, true, false);

        helper.runAtTickTime(4, () -> {
            fixture.host().serverTick();
            helper.assertTrue(fixture.host().isFormed(), "host forms with its module slot empty");
            helper.assertTrue(ModuleConnectionCoordinator.installedModuleCount(fixture.host()) == 0,
                    "empty host has no installed modules");
            helper.succeed();
        });
    }

    public void moduleFormsIndependentlyButCannotRunWithoutHost(GameTestHelper helper) {
        Fixture fixture = placeFixture(helper, false, false, true);

        helper.runAtTickTime(4, () -> {
            fixture.module().serverTick();
            helper.assertTrue(fixture.module().isFormed(), "module forms independently");
            helper.assertTrue(!fixture.module().moduleConnectionStatus().canRunRecipe(Set.of(HOST_ID)),
                    "unconnected module cannot run a host-gated recipe");
            helper.succeed();
        });
    }

    public void sharedCouplerConnectsModuleAndEnablesHostGatedRecipes(GameTestHelper helper) {
        Fixture fixture = placeFixture(helper, false, true, true);

        helper.runAtTickTime(4, () -> {
            formAndRefresh(fixture);
            helper.assertTrue(fixture.host().isFormed(), "host forms before establishing a module connection");
            helper.assertTrue(fixture.module().isFormed(), "module forms before establishing a module connection");
            helper.assertTrue(ModuleConnectionCoordinator.installedModuleCount(fixture.host()) == 1,
                    "shared coupler installs the formed module in the host");
            helper.assertTrue(fixture.module().moduleConnectionStatus().canRunRecipe(Set.of(HOST_ID)),
                    "connected module can run a matching host-gated recipe");
            helper.succeed();
        });
    }

    public void sharedInterfaceInvalidatesHost(GameTestHelper helper) {
        Fixture fixture = placeFixture(helper, true, true, true);

        helper.runAtTickTime(4, () -> {
            formAndRefresh(fixture);
            helper.assertTrue(!fixture.host().isFormed(), "shared interface invalidates the host");
            helper.assertTrue(fixture.module().isFormed(), "interface conflict leaves module formed");
            helper.succeed();
        });
    }

    private static Fixture placeFixture(GameTestHelper helper, boolean sharedInterface, boolean placeHost, boolean placeModule) {
        BlockPos hostPos = new BlockPos(2, 2, 2);
        BlockPos modulePos = new BlockPos(6, 2, 2);
        BlockPos coupler = new BlockPos(4, 2, 2);
        BlockPos hostNormal = new BlockPos(3, 2, 2);
        BlockPos moduleNormal = new BlockPos(5, 2, 2);
        BlockPos moduleInterface = new BlockPos(6, 2, 3);
        BlockPos hostInterface = sharedInterface ? moduleInterface : new BlockPos(2, 2, 3);
        Identifier hostId = sharedInterface ? MMCR.id("gametest_module_host_interface_conflict") : HOST_ID;
        Identifier moduleId = sharedInterface ? MMCR.id("gametest_module_interface_conflict") : MODULE_ID;
        DynamicMachine hostMachine = machine(hostId, MachineRole.HOST, Set.of(moduleId), hostPos, coupler, hostNormal, hostInterface);
        DynamicMachine moduleMachine = machine(moduleId, MachineRole.MODULE, Set.of(), modulePos, coupler, moduleNormal, moduleInterface);
        registerForGameTest(hostMachine);
        registerForGameTest(moduleMachine);

        if (placeHost) {
            helper.setBlock(hostPos, ModBlocks.controllerFor(MMCR.id("test_cube")).get().defaultBlockState()
                    .setValue(MachineControllerBlock.FACING, Direction.SOUTH));
        }
        if (placeModule) {
            helper.setBlock(modulePos, ModBlocks.controllerFor(MMCR.id("test_cube")).get().defaultBlockState()
                    .setValue(MachineControllerBlock.FACING, Direction.SOUTH));
        }
        helper.setBlock(coupler, ModBlocks.MODULE_BRIDGE.get().defaultBlockState());
        if (placeHost) {
            helper.setBlock(hostNormal, Blocks.STONE.defaultBlockState());
            helper.setBlock(hostInterface, ModBlocks.SMART_INTERFACE.get().defaultBlockState());
        }
        if (placeModule) {
            helper.setBlock(moduleNormal, Blocks.STONE.defaultBlockState());
            helper.setBlock(moduleInterface, ModBlocks.SMART_INTERFACE.get().defaultBlockState());
        }

        MachineControllerBlockEntity host = placeHost
                ? helper.getBlockEntity(hostPos, MachineControllerBlockEntity.class) : null;
        MachineControllerBlockEntity module = placeModule
                ? helper.getBlockEntity(modulePos, MachineControllerBlockEntity.class) : null;
        if (host != null) host.setMachine(hostMachine);
        if (module != null) module.setMachine(moduleMachine);
        return new Fixture(host, module, helper.absolutePos(coupler), hostInterface);
    }

    private static void registerForGameTest(DynamicMachine machine) {
        if (MachineRegistry.getMachine(machine.registryName()) == null) {
            MachineRegistry.register(machine);
        }
    }

    private static DynamicMachine machine(Identifier id, MachineRole role, Set<Identifier> acceptedModules,
                                          BlockPos controller, BlockPos coupler, BlockPos normal, BlockPos smartInterface) {
        return new DynamicMachine(id, id.toString(), new BlockArray(Map.of(
                coupler.subtract(controller), BlockPredicate.machineCoupler(),
                normal.subtract(controller), new BlockPredicate.OfBlock(Blocks.STONE),
                smartInterface.subtract(controller), new BlockPredicate.OfBlock(ModBlocks.SMART_INTERFACE.get()))))
                .withRole(role, acceptedModules);
    }

    private static void formAndRefresh(Fixture fixture) {
        fixture.host().serverTick();
        fixture.module().serverTick();
        ModuleConnectionCoordinator.refresh((ServerLevel) fixture.host().getLevel(), fixture.coupler());
    }

    private record Fixture(MachineControllerBlockEntity host, MachineControllerBlockEntity module,
                           BlockPos coupler, BlockPos hostInterface) {
    }
}

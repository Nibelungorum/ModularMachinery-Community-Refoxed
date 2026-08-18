package cn.howxu.mmcr.internal.command;

import cn.howxu.mmcr.api.machine.BlockArray;
import cn.howxu.mmcr.api.machine.MachineRegistration;
import cn.howxu.mmcr.api.machine.MachineStructureDefinition;
import cn.howxu.mmcr.api.machine.MachineStructureRequirements;
import cn.howxu.mmcr.api.machine.MachineStructureRegistry;
import cn.howxu.mmcr.api.machine.PortRequirementSpec;
import cn.howxu.mmcr.api.machine.MachineDefinitions;
import cn.howxu.mmcr.api.machine.MachineRegistry;
import cn.howxu.mmcr.api.recipe.RecipeRegistry;
import cn.howxu.mmcr.internal.network.RuntimeContentSync;
import cn.howxu.mmcr.internal.reload.DynamicContentReloadService;
import cn.howxu.mmcr.test.TestBootstrap;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSource;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.permissions.PermissionSet;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class ReloadCommandTest {

    @BeforeAll
    static void bootstrapMinecraft() throws Exception {
        TestBootstrap.bootstrap();
    }

    @AfterEach
    void cleanup() {
        RuntimeContentSync.resetSenderForTesting();
        MachineDefinitions.clearForTesting();
        MachineRegistry.clearForTesting();
        MachineStructureRegistry.clearForTesting();
        RecipeRegistry.clearForTesting();
    }

    @Test
    void reloadCommandClearsDynamicStructures() throws Exception {
        Identifier removed = Identifier.parse("mmcr:removed");
        MachineDefinitions.register(MachineRegistration.builder(removed).localizedName("Removed").build());
        DynamicContentReloadService.reload(candidate -> candidate.registerStructure(structure(removed)));
        CommandDispatcher<CommandSourceStack> dispatcher = new CommandDispatcher<>();
        ReloadCommand.register(dispatcher);
        AtomicBoolean syncCalled = new AtomicBoolean();
        AtomicReference<MinecraftServer> syncedServer = new AtomicReference<>();
        RuntimeContentSync.setSenderForTesting(server -> {
            syncCalled.set(true);
            syncedServer.set(server);
        });

        int result = dispatcher.execute("mmcr reload", source());

        assertThat(result).isEqualTo(1);
        assertThat(syncCalled).isTrue();
        assertThat(syncedServer).hasValue(null);
    }

    private CommandSourceStack source() {
        CommandSource source = new CommandSource() {
            @Override
            public void sendSystemMessage(net.minecraft.network.chat.Component message) {
            }

            @Override
            public boolean acceptsSuccess() {
                return true;
            }

            @Override
            public boolean acceptsFailure() {
                return true;
            }

            @Override
            public boolean shouldInformAdmins() {
                return false;
            }
        };
        return new CommandSourceStack(source, Vec3.ZERO, Vec2.ZERO, null, PermissionSet.ALL_PERMISSIONS,
                "test", net.minecraft.network.chat.Component.literal("test"), null, null);
    }

    private static MachineStructureDefinition structure(Identifier id) {
        return new MachineStructureDefinition(id, new BlockArray(Map.of()), PortRequirementSpec.none(), List.of(),
                MachineStructureRequirements.EMPTY);
    }

}

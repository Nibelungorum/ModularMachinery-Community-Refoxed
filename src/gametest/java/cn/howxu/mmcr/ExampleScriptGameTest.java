package cn.howxu.mmcr;

import cn.howxu.mmcr.api.recipe.requirement.ItemRequirement;
import cn.howxu.mmcr.api.recipe.requirement.MachineRequirement;
import cn.howxu.mmcr.compat.kubejs.KubeJSApi;
import cn.howxu.mmcr.compat.botania.BotaniaBridge;
import cn.howxu.mmcr.compat.botania.BotaniaManaExampleGameTest;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import dev.latvian.mods.rhino.ContextFactory;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.RegistryOps;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;

/**
 * Validates example declarations against the live 1.21.1 registries and codecs.
 *
 * @author howxu <dev@howxu.cn>
 */
public class ExampleScriptGameTest {
    public void botaniaManaExamplesExecute(GameTestHelper helper) {
        if (!BotaniaBridge.get().available()) {
            helper.succeed();
            return;
        }
        new BotaniaManaExampleGameTest().examplesExecute(helper);
    }

    public void nativeRecipeRequirementsDecode(GameTestHelper helper) {
        var context = new ContextFactory().enter();
        var scope = context.initStandardObjects();
        RegistryOps<JsonElement> ops = RegistryOps.create(JsonOps.INSTANCE, helper.getLevel().registryAccess());
        int decoded = 0;
        try (var paths = Files.walk(examples().resolve("recipe"))) {
            for (Path path : paths.filter(file -> file.toString().endsWith(".js")).sorted().toList()) {
                if (path.getParent().endsWith("mekanism") || path.getParent().endsWith("takeover")) continue;
                context.evaluateString(scope, """
                        var captured = [];
                        var MMCR = { getValues: function() { return { INT_MAX: 2147483647 }; } };
                        var ServerEvents = { recipes: function(callback) {
                            callback({ custom: function(json) {
                                captured.push(json);
                                return { id: function() {} };
                            } });
                        } };
                        """, "example-recipe-capture", 1, null);
                context.evaluateString(scope, Files.readString(path), path.toString(), 1, null);
                String json = context.evaluateString(scope, "JSON.stringify(captured)", path.toString(), 1, null).toString();
                for (JsonElement recipe : JsonParser.parseString(json).getAsJsonArray()) {
                    for (JsonElement requirement : recipe.getAsJsonObject().getAsJsonArray("requirements")) {
                        String type = requirement.getAsJsonObject().get("type").getAsString();
                        if (!type.equals("minecraft:item") && !type.equals("minecraft:fluid")) continue;
                        var parsed = MachineRequirement.CODEC.parse(ops, requirement)
                                .getOrThrow(error -> new IllegalArgumentException(path + ": " + error));
                        if (path.getFileName().toString().equals("A_Modifier_Machine.js")
                                && parsed instanceof ItemRequirement item && item.io().isInput()) {
                            var sharpness = helper.getLevel().registryAccess().registryOrThrow(Registries.ENCHANTMENT)
                                    .getHolderOrThrow(Enchantments.SHARPNESS);
                            ItemStack matching = new ItemStack(Items.IRON_SWORD);
                            matching.enchant(sharpness, 2);
                            ItemStack wrongLevel = new ItemStack(Items.IRON_SWORD);
                            wrongLevel.enchant(sharpness, 1);
                            helper.assertTrue(item.components().matches(matching, ops), "Example accepts Sharpness II");
                            helper.assertTrue(!item.components().matches(wrongLevel, ops), "Example rejects Sharpness I");
                        }
                        decoded++;
                    }
                }
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to load example recipes", exception);
        }
        helper.assertTrue(decoded > 0, "Example recipes were executed and decoded");
        helper.succeed();
    }

    public void structureBlocksResolve(GameTestHelper helper) {
        var api = new KubeJSApi();
        var declarations = Pattern.compile("api\\.(block|state)\\(([\"'])(minecraft:[^\"']+)\\2\\)");
        int resolved = 0;
        try (var paths = Files.walk(examples().resolve("structure"))) {
            for (Path path : paths.filter(file -> file.toString().endsWith(".js")).sorted().toList()) {
                var matcher = declarations.matcher(Files.readString(path));
                while (matcher.find()) {
                    try {
                        if (matcher.group(1).equals("block")) api.block(matcher.group(3));
                        else api.state(matcher.group(3));
                    } catch (RuntimeException exception) {
                        throw new IllegalArgumentException(path + ": " + matcher.group(3), exception);
                    }
                    resolved++;
                }
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to load example structures", exception);
        }
        helper.assertTrue(resolved > 0, "Example block and state declarations resolved against game registries");
        helper.succeed();
    }

    private static Path examples() {
        for (Path root = Path.of("").toAbsolutePath(); root != null; root = root.getParent()) {
            Path examples = root.resolve("example/server_scripts");
            if (Files.isDirectory(examples)) return examples;
        }
        throw new IllegalStateException("Cannot locate example/server_scripts from the GameTest working directory");
    }
}

package cn.howxu.mmcr.compat.botania;

import com.google.gson.JsonParser;
import cn.howxu.mmcr.api.capability.plan.PlanningContext;
import cn.howxu.mmcr.api.recipe.requirement.MachineRequirement;
import cn.howxu.mmcr.api.recipe.requirement.RequirementType;
import cn.howxu.mmcr.test.TestBootstrap;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.MapCodec;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Exercises neutral declarations with native and client linkage actively forbidden. @author howxu <dev@howxu.cn> */
class BotaniaOptionalLoadingTest {
    @BeforeAll
    static void bootstrapMinecraft() throws Exception { TestBootstrap.bootstrap(); }

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void absentNativeClassesStillAllowBridgeCodecsAndDeclarations() throws Exception {
        FilteringLoader loader = new FilteringLoader(getClass().getClassLoader());
        Class<?> bootstrap = loader.loadClass("cn.howxu.mmcr.compat.botania.BotaniaBridgeBootstrap");
        Class<?> bridgeType = loader.loadClass("cn.howxu.mmcr.compat.botania.BotaniaBridge");
        Object bridge = bootstrap.getMethod("selectForTesting", boolean.class).invoke(null, false);
        bootstrap.getMethod("installForTesting", bridgeType).invoke(null, bridge);
        try {
            assertFalse((boolean) bridgeType.getMethod("available").invoke(bridge));
            assertEquals(List.of(), bridgeType.getMethod("portKinds").invoke(bridge));
            assertSame(bridge, bridgeType.getMethod("get").invoke(null));
            Class<?> requirement = loader.loadClass("cn.howxu.mmcr.compat.botania.ManaRequirement");
            assertNotNull(requirement.getField("TYPE").get(null));
            assertNotNull(requirement.getField("SYNC_CODEC").get(null));
            MapCodec<?> codec = (MapCodec<?>) requirement.getField("CODEC").get(null);
            Object parsed = codec.codec().parse(JsonOps.INSTANCE,
                            JsonParser.parseString("{\"type\":\"botania:mana\",\"io\":\"input\",\"amount\":37}"))
                    .getOrThrow();
            assertEquals(37L, requirement.getMethod("amount").invoke(parsed));
            Object input = requirement.getMethod("input", long.class).invoke(null, 37L);
            assertEquals(37L, requirement.getMethod("amount").invoke(input));
            Object output = requirement.getMethod("output", long.class).invoke(null, 41L);
            assertEquals(41L, requirement.getMethod("amount").invoke(output));
            RequirementType type = (RequirementType) requirement.getField("TYPE").get(null);
            var plan = type.handler().plan((MachineRequirement) input, List.of(), new PlanningContext(1, 0));
            assertFalse(plan.successful());
            assertEquals("mmcr:botania_unavailable", plan.failure().reason().id().toString());
            Class<?> declarations = loader.loadClass("cn.howxu.mmcr.compat.botania.ManaRecipeDeclarations");
            assertNotNull(declarations.getMethod("inputPayload", long.class).invoke(null, 37L));
            assertNotNull(declarations.getMethod("outputPayload", long.class).invoke(null, 41L));
            Class<?> publicIo = loader.loadClass("cn.howxu.mmcr.publicapi.recipe.BotaniaIo");
            assertNotNull(publicIo.getMethod("manaInput", long.class).invoke(null, 37L));
            assertNotNull(publicIo.getMethod("manaOutput", long.class).invoke(null, 41L));
            assertTrue(loader.forbiddenLoads.isEmpty(), "Absent bridge and neutral codecs never attempt native/client linkage");
        } finally {
            bootstrap.getMethod("resetForTesting").invoke(null);
        }
    }

    /** Child-first integration loader retaining the shared Minecraft/serialization runtime. @author howxu <dev@howxu.cn> */
    private static final class FilteringLoader extends ClassLoader {
        private final List<String> forbiddenLoads = new ArrayList<>();

        private FilteringLoader(ClassLoader parent) { super(parent); }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            synchronized (getClassLoadingLock(name)) {
                if (name.startsWith("vazkii.botania.") || name.startsWith("cn.howxu.mmcr.compat.botania.loaded.")
                        || name.startsWith("cn.howxu.mmcr.compat.botania.client.")) {
                    forbiddenLoads.add(name);
                    throw new ClassNotFoundException("Filtered optional native class: " + name);
                }
                if (!name.startsWith("cn.howxu.mmcr.compat.botania.")
                        && !name.equals("cn.howxu.mmcr.publicapi.recipe.BotaniaIo")
                        && !name.equals("cn.howxu.mmcr.internal.api.facade.recipe.BotaniaIoAdapters")) {
                    return super.loadClass(name, resolve);
                }
                Class<?> loaded = findLoadedClass(name);
                if (loaded == null) {
                    try (InputStream source = getParent().getResourceAsStream(name.replace('.', '/') + ".class")) {
                        if (source == null) throw new ClassNotFoundException(name);
                        byte[] bytes = source.readAllBytes();
                        loaded = defineClass(name, bytes, 0, bytes.length);
                    } catch (IOException exception) {
                        throw new ClassNotFoundException(name, exception);
                    }
                }
                if (resolve) resolveClass(loaded);
                return loaded;
            }
        }
    }
}

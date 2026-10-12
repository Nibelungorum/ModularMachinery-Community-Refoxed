package cn.howxu.mmcr.compat.emi;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.Logger;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Property;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.spongepowered.asm.mixin.transformer.meta.MixinMerged;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Exercises release EMI diagnostics without starting Minecraft or a reload worker.
 * @author howxu <dev@howxu.cn>
 */
class EmiLoggerPreloadTest {
    private static final String DIAGNOSTICS = "dev.emi.emi.mixinsupport.EmiMixinTransformation";
    private static final String LOG = "dev.emi.emi.runtime.EmiLog";
    private static final String CLIENT = "cn.howxu.mmcr.Client";
    private static final String EVENTS = "cn.howxu.mmcr.compat.emi.EmiClientEvents";
    private static final String TARGET = "dev.emi.emi.screen.RecipeScreen";
    private static final String TAINTER = "cn.howxu.mmcr.mixin.compat.emi.EmiRecipeScreenMixin";

    @Test
    void coldLoggerIsRejectedWhenReleaseDiagnosticsRunInsideAnOuterTransformation() throws Exception {
        ColdEmiLoader loader = new ColdEmiLoader();
        Class<?> diagnostics = enableDiagnostics(loader);

        InvocationTargetException failure = assertThrows(InvocationTargetException.class,
                () -> exposeSinners(loader, diagnostics, taintedScreen()));

        assertInstanceOf(NestedLoggerTransformation.class, failure.getCause());
        assertEquals(List.of(LOG), loader.rejectedLoads);
        assertNull(loader.loaded(LOG));
        assertTrue(loader.loggerDefinitions.isEmpty());
    }

    @Test
    void clientPreloadAvoidsNestedLoggerTransformationAndPreservesTheWarning() throws Exception {
        ColdEmiLoader loader = new ColdEmiLoader();
        Class<?> diagnostics = enableDiagnostics(loader);

        // Execute the compiled Client try-block prefix, not a second handwritten Class.forName.
        // A same-name, minimal test class keeps Client.class.getClassLoader() meaningful without
        // linking the actual constructor's Minecraft/NeoForge startup dependencies.
        Class<?> client = loader.defineClient(clientPreloadBytecode());
        loader.preloading = true;
        try {
            client.getMethod("preload").invoke(null);
        } finally {
            loader.preloading = false;
        }
        assertEquals(List.of(false), loader.loggerDefinitions);
        assertEquals(List.of(false), loader.loggerFactoryLoads,
                "The release logger must initialize before the transformation window");
        assertNotNull(loader.loaded(LOG));
        assertSame(loader, loader.loaded(LOG).getClassLoader());
        assertNotNull(loader.loaded(LOG).getField("LOG").get(null));
        assertTrue(loader.unexpectedLoads.isEmpty(), "Preloading must not start EMI reload/UI");

        Logger logger = (Logger) LogManager.getLogger("EMI");
        RecordingAppender appender = new RecordingAppender();
        appender.start();
        logger.addAppender(appender);
        try {
            assertTrue(logger.isWarnEnabled(), "The existing logging backend must retain EMI warnings");
            ClassNode screen = taintedScreen();
            assertDoesNotThrow(() -> exposeSinners(loader, diagnostics, screen));
            assertTrue(appender.events.stream().anyMatch(event -> event.getLevel() == Level.WARN
                    && event.getLoggerName().equals("EMI")
                    && event.getMessage().getFormattedMessage().contains(TARGET + " is tainted by")
                    && event.getMessage().getFormattedMessage().contains(TAINTER)));
            assertTrue(diagnostics.getField("speakTheGoodWord").getBoolean(null));
            assertNotNull(screen.sourceFile);
            assertTrue(screen.sourceFile.contains(TAINTER));
            assertEquals(screen.sourceFile, screen.sourceDebug);
            assertTrue(loader.rejectedLoads.isEmpty());
            assertEquals(List.of(false), loader.loggerDefinitions);
            assertTrue(loader.unexpectedLoads.isEmpty());
        } finally {
            logger.removeAppender(appender);
            appender.stop();
        }
    }

    private static Class<?> enableDiagnostics(ColdEmiLoader loader) throws Exception {
        Class<?> diagnostics = Class.forName(DIAGNOSTICS, true, loader);
        assertSame(loader, diagnostics.getClassLoader());
        diagnostics.getMethod("preach").invoke(null);
        assertTrue(diagnostics.getField("speakTheGoodWord").getBoolean(null));
        assertNull(loader.loaded(LOG), "preach with an empty taint cache does not warm EmiLog");
        assertTrue(loader.loggerFactoryLoads.isEmpty());
        return diagnostics;
    }

    private static void exposeSinners(ColdEmiLoader loader, Class<?> diagnostics, ClassNode screen)
            throws ReflectiveOperationException {
        loader.transformationActive = true;
        try {
            diagnostics.getMethod("exposeSinners", String.class, ClassNode.class, String.class)
                    .invoke(null, TARGET, screen, "dev.emi.emi.mixin.GlobalMixin");
        } finally {
            loader.transformationActive = false;
        }
    }

    private static ClassNode taintedScreen() {
        ClassNode screen = new ClassNode();
        screen.name = TARGET.replace('.', '/');
        MethodNode method = new MethodNode(Opcodes.ACC_PUBLIC, "mmcrInjected", "()V", null, null);
        AnnotationNode merged = new AnnotationNode(Type.getDescriptor(MixinMerged.class));
        merged.values = List.of("mixin", TAINTER);
        method.visibleAnnotations = List.of(merged);
        screen.methods.add(method);
        return screen;
    }

    private static byte[] clientPreloadBytecode() throws IOException {
        ClassNode client = new ClassNode();
        new ClassReader(classBytes(CLIENT)).accept(client, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        MethodNode constructor = client.methods.stream().filter(method -> method.name.equals("<init>"))
                .findFirst().orElseThrow();
        AbstractInsnNode events = null;
        for (AbstractInsnNode instruction : constructor.instructions) {
            if (instruction instanceof LdcInsnNode constant && EVENTS.equals(constant.cst)) events = instruction;
        }
        assertNotNull(events, "The optional EMI registration branch must exist");
        int eventsIndex = constructor.instructions.indexOf(events);
        var registration = constructor.tryCatchBlocks.stream()
                .filter(block -> "java/lang/ReflectiveOperationException".equals(block.type)
                        && constructor.instructions.indexOf(block.start) <= eventsIndex
                        && eventsIndex < constructor.instructions.indexOf(block.end))
                .findFirst().orElseThrow();

        List<AbstractInsnNode> prefix = new ArrayList<>();
        for (AbstractInsnNode instruction = registration.start; instruction != events;
             instruction = instruction.getNext()) {
            if (instruction.getOpcode() >= 0) prefix.add(instruction);
        }
        // Check initialization and defining-loader operands in executable bytecode. In particular,
        // initialize=false would define the logger but leave its static initialization until later.
        int call = -1;
        for (int i = 0; i < prefix.size(); i++) {
            if (prefix.get(i) instanceof MethodInsnNode method
                    && method.owner.equals("java/lang/Class") && method.name.equals("forName")) {
                assertEquals("(Ljava/lang/String;ZLjava/lang/ClassLoader;)Ljava/lang/Class;", method.desc);
                call = i;
            }
        }
        assertTrue(call >= 4, "Client must preload EmiLog before loading EmiClientEvents");
        assertEquals(LOG, assertInstanceOf(LdcInsnNode.class, prefix.get(call - 4)).cst);
        assertEquals(Opcodes.ICONST_1, prefix.get(call - 3).getOpcode());
        assertEquals(Type.getObjectType(client.name),
                assertInstanceOf(LdcInsnNode.class, prefix.get(call - 2)).cst);
        MethodInsnNode definingLoader = assertInstanceOf(MethodInsnNode.class, prefix.get(call - 1));
        assertEquals("java/lang/Class", definingLoader.owner);
        assertEquals("getClassLoader", definingLoader.name);

        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        writer.visit(client.version, Opcodes.ACC_PUBLIC, client.name, null, "java/lang/Object", null);
        var preload = writer.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "preload", "()V", null, null);
        preload.visitCode();
        for (AbstractInsnNode instruction : prefix) instruction.accept(preload);
        preload.visitInsn(Opcodes.RETURN);
        preload.visitMaxs(0, 0);
        preload.visitEnd();
        writer.visitEnd();
        return writer.toByteArray();
    }

    private static byte[] classBytes(String name) throws IOException {
        try (InputStream stream = EmiLoggerPreloadTest.class.getClassLoader()
                .getResourceAsStream(name.replace('.', '/') + ".class")) {
            assertNotNull(stream, "Missing real classpath resource: " + name);
            return stream.readAllBytes();
        }
    }

    /** Rejects only a fresh logger definition inside the simulated Mixin transformation window.
     * @author howxu <dev@howxu.cn>
     */
    private static final class ColdEmiLoader extends ClassLoader {
        private final List<String> rejectedLoads = new ArrayList<>();
        private final List<String> unexpectedLoads = new ArrayList<>();
        private final List<Boolean> loggerDefinitions = new ArrayList<>();
        private final List<Boolean> loggerFactoryLoads = new ArrayList<>();
        private boolean transformationActive;
        private boolean preloading;

        private ColdEmiLoader() {
            super(EmiLoggerPreloadTest.class.getClassLoader());
        }

        private Class<?> defineClient(byte[] bytes) {
            return defineClass(CLIENT, bytes, 0, bytes.length);
        }

        private Class<?> loaded(String name) {
            return findLoadedClass(name);
        }

        @Override
        protected synchronized Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            Class<?> type = findLoadedClass(name);
            if (type == null) {
                if (name.equals(LOG) || name.equals(DIAGNOSTICS)) {
                    if (name.equals(LOG) && transformationActive) {
                        rejectedLoads.add(name);
                        throw new NestedLoggerTransformation();
                    }
                    if (preloading && !name.equals(LOG)) {
                        unexpectedLoads.add(name);
                        throw new AssertionError("Unexpected EMI preload: " + name);
                    }
                    try {
                        byte[] bytes = classBytes(name);
                        type = defineClass(name, bytes, 0, bytes.length);
                        if (name.equals(LOG)) loggerDefinitions.add(transformationActive);
                    } catch (IOException exception) {
                        throw new ClassNotFoundException(name, exception);
                    }
                } else if (name.startsWith("dev.emi.emi.") || name.startsWith("cn.howxu.mmcr.compat.emi.")) {
                    unexpectedLoads.add(name);
                    throw new AssertionError("Unexpected EMI runtime/UI class load: " + name);
                } else {
                    if (name.equals("org.slf4j.LoggerFactory")) loggerFactoryLoads.add(transformationActive);
                    type = super.loadClass(name, false);
                }
            }
            if (resolve) resolveClass(type);
            return type;
        }
    }

    /** Models Mixin's rejection of a nested transformation, not a logger method failure.
     * @author howxu <dev@howxu.cn>
     */
    private static final class NestedLoggerTransformation extends LinkageError {}

    /** Captures the existing backend without changing logger levels or configuration.
     * @author howxu <dev@howxu.cn>
     */
    private static final class RecordingAppender extends AbstractAppender {
        private final List<LogEvent> events = new ArrayList<>();

        private RecordingAppender() {
            super("emi-logger-preload-test", null, null, true, Property.EMPTY_ARRAY);
        }

        @Override
        public void append(LogEvent event) {
            events.add(event.toImmutable());
        }
    }
}

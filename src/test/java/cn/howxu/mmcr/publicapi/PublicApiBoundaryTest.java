package cn.howxu.mmcr.publicapi;

import java.lang.classfile.AnnotationValue;
import java.lang.classfile.AttributedElement;
import java.lang.classfile.ClassFile;
import java.lang.classfile.attribute.AnnotationDefaultAttribute;
import java.lang.classfile.attribute.RecordAttribute;
import java.lang.classfile.attribute.RuntimeInvisibleAnnotationsAttribute;
import java.lang.classfile.attribute.RuntimeInvisibleParameterAnnotationsAttribute;
import java.lang.classfile.attribute.RuntimeInvisibleTypeAnnotationsAttribute;
import java.lang.classfile.attribute.RuntimeVisibleAnnotationsAttribute;
import java.lang.classfile.attribute.RuntimeVisibleParameterAnnotationsAttribute;
import java.lang.classfile.attribute.RuntimeVisibleTypeAnnotationsAttribute;
import java.lang.constant.ClassDesc;
import java.lang.constant.MethodTypeDesc;
import java.lang.reflect.AnnotatedArrayType;
import java.lang.reflect.AnnotatedParameterizedType;
import java.lang.reflect.AnnotatedType;
import java.lang.reflect.AnnotatedTypeVariable;
import java.lang.reflect.AnnotatedWildcardType;
import java.lang.reflect.Executable;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.lang.reflect.WildcardType;
import java.net.URLClassLoader;
import java.util.HashSet;
import java.util.Set;
import java.util.jar.JarFile;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Recursive binary export audit, including CLASS-retained annotations, without inspecting method bodies.
 * @author howxu <dev@howxu.cn>
 */
class PublicApiBoundaryTest {
    private static final String PUBLIC = "cn.howxu.mmcr.publicapi.";
    private static final String CORE_LANE_OWNER = PUBLIC + "ui.ControllerUiSnapshot$Lane";
    private static final Set<String> CLIENT_EVENTS = Set.of("RegisterControllerRenderersEvent",
            "RegisterJeiWorkstationsEvent", "RegisterJeiRecipeInformationEvent", "RegisterControllerUisEvent");
    private final Set<Type> visited = new HashSet<>();
    private boolean common;

    @Test
    void every_exported_signature_is_public_only_and_common_contracts_are_server_safe() throws Exception {
        try (URLClassLoader loader = PublicApiArtifactTest.isolatedLoader();
             JarFile jar = new JarFile(PublicApiArtifactTest.apiJar().toFile())) {
            for (String name : PublicApiArtifactTest.classNames()) {
                Class<?> type = Class.forName(name, false, loader);
                if (!Modifier.isPublic(type.getModifiers()) && !Modifier.isProtected(type.getModifiers())) continue;
                common = !name.startsWith(PUBLIC + "client.") && !clientEvent(name);
                visited.clear();
                inspect(type);
                try (var input = jar.getInputStream(jar.getJarEntry(name.replace('.', '/') + ".class"))) {
                    var model = ClassFile.of().parse(input.readAllBytes());
                    inspectAnnotations(model);
                    model.fields().stream().filter(field -> exported(field.flags().flagsMask())).forEach(this::inspectAnnotations);
                    model.methods().stream().filter(method -> exported(method.flags().flagsMask())).forEach(this::inspectAnnotations);
                }
            }
            for (var entry : jar.stream().filter(e -> e.getName().endsWith("/package-info.class")).toList()) {
                common = !entry.getName().startsWith("cn/howxu/mmcr/publicapi/client/");
                try (var input = jar.getInputStream(entry)) {
                    inspectAnnotations(ClassFile.of().parse(input.readAllBytes()));
                }
            }
        }
    }

    private static boolean clientEvent(String name) {
        return CLIENT_EVENTS.stream().anyMatch(event -> name.equals(PUBLIC + "event." + event)
                || name.startsWith(PUBLIC + "event." + event + "$"));
    }

    private static boolean exported(int modifiers) {
        return Modifier.isPublic(modifiers) || Modifier.isProtected(modifiers);
    }

    @Test
    void core_name_exception_allows_only_the_instance_boolean_lane_property() throws Exception {
        try (URLClassLoader loader = PublicApiArtifactTest.isolatedLoader()) {
            Method actual = Class.forName(CORE_LANE_OWNER, false, loader).getDeclaredMethod("core");
            assertDoesNotThrow(() -> inspectMethodName(actual));
        }
        assertDoesNotThrow(() -> inspectMethodName(laneMethod(boolean.class, false)));
        assertThrows(AssertionError.class, () -> inspectMethodName(laneMethod(Object.class, false)));
        assertThrows(AssertionError.class, () -> inspectMethodName(laneMethod(Boolean.class, false)));
        assertThrows(AssertionError.class, () -> inspectMethodName(laneMethod(boolean.class, false, boolean.class)));
        assertThrows(AssertionError.class, () -> inspectMethodName(laneMethod(boolean.class, true)));
        for (Method method : ForbiddenHandles.class.getDeclaredMethods()) {
            assertThrows(AssertionError.class, () -> inspectMethodName(method), method.toString());
        }
    }

    private static void inspectMethodName(Method method) {
        String name = method.getName();
        boolean coreLaneProperty = method.getDeclaringClass().getName().equals(CORE_LANE_OWNER)
                && name.equals("core") && method.getReturnType() == boolean.class && method.getParameterCount() == 0
                && !Modifier.isStatic(method.getModifiers());
        assertFalse(!coreLaneProperty && (name.startsWith("core") || name.startsWith("unwrap") || name.contains("bridge")
                || Set.of("toCore", "fromCore").contains(name)), method.toString());
    }

    private static Method laneMethod(Class<?> result, boolean isStatic, Class<?>... parameters) throws Exception {
        // Real reflection signatures with the exact owner, including invalid alternatives to the API property.
        ClassDesc[] args = new ClassDesc[parameters.length];
        for (int i = 0; i < parameters.length; i++) args[i] = ClassDesc.ofDescriptor(parameters[i].descriptorString());
        MethodTypeDesc descriptor = MethodTypeDesc.of(ClassDesc.ofDescriptor(result.descriptorString()), args);
        byte[] bytes = ClassFile.of().build(ClassDesc.of(CORE_LANE_OWNER), builder -> builder
                .withFlags(ClassFile.ACC_PUBLIC | ClassFile.ACC_ABSTRACT)
                .withMethod("core", descriptor, ClassFile.ACC_PUBLIC | (isStatic ? ClassFile.ACC_STATIC : ClassFile.ACC_ABSTRACT),
                        method -> {
                            if (isStatic) method.withCode(code -> code.aconst_null().athrow());
                        }));
        return new ClassLoader(PublicApiBoundaryTest.class.getClassLoader()) {
            Class<?> define() { return defineClass(CORE_LANE_OWNER, bytes, 0, bytes.length); }
        }.define().getDeclaredMethod("core", parameters);
    }

    /** Negative audit signatures; a boolean core on any other owner remains forbidden.
     * @author howxu <dev@howxu.cn>
     */
    private interface ForbiddenHandles {
        boolean core();
        Object coreHandle();
        boolean coreDelegate();
        Object unwrap();
        Object unwrapValue();
        Object bridge();
        Object bridgeValue();
        Object toCore();
        Object fromCore();
    }

    private void inspect(Class<?> type) {
        checkName(type.getName());
        check(type.getGenericSuperclass());
        for (Type parent : type.getGenericInterfaces()) check(parent);
        for (TypeVariable<?> variable : type.getTypeParameters()) check(variable);
        annotated(type.getAnnotatedSuperclass());
        for (AnnotatedType parent : type.getAnnotatedInterfaces()) annotated(parent);
        for (var field : type.getDeclaredFields()) {
            if (!exported(field.getModifiers())) continue;
            noObject(field.getGenericType(), field.toString());
            check(field.getGenericType());
            annotated(field.getAnnotatedType());
        }
        for (var constructor : type.getDeclaredConstructors()) {
            if (exported(constructor.getModifiers())) executable(constructor, false);
        }
        for (Method method : type.getDeclaredMethods()) {
            if (!exported(method.getModifiers())) continue;
            String name = method.getName();
            inspectMethodName(method);
            boolean equality = name.equals("equals") && method.getReturnType() == boolean.class
                    && method.getParameterCount() == 1 && method.getParameterTypes()[0] == Object.class
                    && !Modifier.isStatic(method.getModifiers());
            boolean objectValue = equality || translationArguments(method);
            if (!objectValue) noObject(method.getGenericReturnType(), method.toString());
            check(method.getGenericReturnType());
            annotated(method.getAnnotatedReturnType());
            executable(method, objectValue);
        }
        for (Class<?> nested : type.getDeclaredClasses()) {
            if (exported(nested.getModifiers()) && visited.add(nested)) inspect(nested);
        }
    }

    // Translation arguments are platform values, not implementation handles. This is a narrow allowlist.
    private boolean translationArguments(Method method) {
        String owner = method.getDeclaringClass().getName();
        return (owner.equals(PUBLIC + "client.jei.RecipeInformation") && method.getName().equals("arguments"))
                || ((owner.equals(PUBLIC + "client.jei.RecipeInformationRegistrar")
                || owner.equals(PUBLIC + "event.RegisterJeiRecipeInformationEvent"))
                && Set.of("registerRecipe", "registerRecipePool").contains(method.getName()) && method.isVarArgs());
    }

    private void executable(Executable executable, boolean objectValue) {
        for (Type parameter : executable.getGenericParameterTypes()) {
            if (!objectValue) noObject(parameter, executable.toString());
            check(parameter);
        }
        for (Type exception : executable.getGenericExceptionTypes()) check(exception);
        for (TypeVariable<?> variable : executable.getTypeParameters()) check(variable);
        for (AnnotatedType parameter : executable.getAnnotatedParameterTypes()) annotated(parameter);
        for (AnnotatedType exception : executable.getAnnotatedExceptionTypes()) annotated(exception);
        annotated(executable.getAnnotatedReceiverType());
    }

    private void noObject(Type type, String location) {
        if (type instanceof Class<?> value) {
            assertNotEquals(Object.class, value, location + " exposes an untyped Object");
            if (value.isArray()) noObject(value.getComponentType(), location);
        } else if (type instanceof ParameterizedType value) {
            for (Type argument : value.getActualTypeArguments()) noObject(argument, location);
        } else if (type instanceof GenericArrayType value) {
            noObject(value.getGenericComponentType(), location);
        }
    }

    private void check(Type type) {
        if (type == null || !visited.add(type)) return;
        if (type instanceof Class<?> value) {
            if (value.isArray()) check(value.getComponentType());
            else {
                checkName(value.getName());
                if (value.getName().startsWith(PUBLIC)) inspect(value);
            }
        } else if (type instanceof ParameterizedType value) {
            check(value.getRawType()); check(value.getOwnerType());
            for (Type argument : value.getActualTypeArguments()) check(argument);
        } else if (type instanceof TypeVariable<?> value) {
            for (Type bound : value.getBounds()) check(bound);
            for (AnnotatedType bound : value.getAnnotatedBounds()) annotated(bound);
        } else if (type instanceof WildcardType value) {
            for (Type bound : value.getUpperBounds()) check(bound);
            for (Type bound : value.getLowerBounds()) check(bound);
        } else if (type instanceof GenericArrayType value) {
            check(value.getGenericComponentType());
        } else fail("Unhandled signature type: " + type);
    }

    private void annotated(AnnotatedType type) {
        if (type == null) return;
        // CLASS-retained annotations and their values are additionally visited from the actual classfile.
        for (var annotation : type.getAnnotations()) checkName(annotation.annotationType().getName());
        if (type instanceof AnnotatedParameterizedType value) {
            for (AnnotatedType argument : value.getAnnotatedActualTypeArguments()) annotated(argument);
        } else if (type instanceof AnnotatedArrayType value) annotated(value.getAnnotatedGenericComponentType());
        else if (type instanceof AnnotatedWildcardType value) {
            for (AnnotatedType bound : value.getAnnotatedLowerBounds()) annotated(bound);
            for (AnnotatedType bound : value.getAnnotatedUpperBounds()) annotated(bound);
        }
        // Recursive T extends Comparable<T> bounds are handled by check(Type)'s visited set.
        else if (type instanceof AnnotatedTypeVariable value) check(value.getType());
    }

    private void inspectAnnotations(AttributedElement element) {
        for (var attribute : element.attributes()) {
            switch (attribute) {
                case RuntimeVisibleAnnotationsAttribute value -> value.annotations().forEach(this::annotation);
                case RuntimeInvisibleAnnotationsAttribute value -> value.annotations().forEach(this::annotation);
                case RuntimeVisibleTypeAnnotationsAttribute value -> value.annotations().forEach(a -> annotation(a.annotation()));
                case RuntimeInvisibleTypeAnnotationsAttribute value -> value.annotations().forEach(a -> annotation(a.annotation()));
                case RuntimeVisibleParameterAnnotationsAttribute value -> value.parameterAnnotations().forEach(as -> as.forEach(this::annotation));
                case RuntimeInvisibleParameterAnnotationsAttribute value -> value.parameterAnnotations().forEach(as -> as.forEach(this::annotation));
                case AnnotationDefaultAttribute value -> annotationValue(value.defaultValue());
                case RecordAttribute value -> value.components().forEach(this::inspectAnnotations);
                default -> { }
            }
        }
    }

    private void annotation(java.lang.classfile.Annotation annotation) {
        descriptor(annotation.classSymbol());
        annotation.elements().forEach(element -> annotationValue(element.value()));
    }

    private void annotationValue(AnnotationValue value) {
        switch (value) {
            case AnnotationValue.OfClass type -> descriptor(type.classSymbol());
            case AnnotationValue.OfEnum type -> descriptor(type.classSymbol());
            case AnnotationValue.OfAnnotation nested -> annotation(nested.annotation());
            case AnnotationValue.OfArray array -> array.values().forEach(this::annotationValue);
            default -> { }
        }
    }

    private void descriptor(ClassDesc descriptor) {
        while (descriptor.isArray()) descriptor = descriptor.componentType();
        if (!descriptor.isPrimitive()) checkName(descriptor.packageName() + "." + descriptor.displayName());
    }

    private void checkName(String name) {
        if (name.startsWith("cn.howxu.mmcr.")) assertTrue(name.startsWith(PUBLIC), "Non-Public MMCR type: " + name);
        if (common) {
            assertFalse(name.startsWith("net.minecraft.client.") || name.startsWith("com.mojang.blaze3d.")
                    || name.startsWith(PUBLIC + "client."), "Client type in common contract: " + name);
        }
    }
}

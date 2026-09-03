package cn.howxu.mmcr.api.machine;

import cn.howxu.mmcr.api.publicapi.ApiRegistrationException;
import cn.howxu.mmcr.api.publicapi.machine.MachineBehavior;
import cn.howxu.mmcr.api.publicapi.machine.RecipeBehavior;
import cn.howxu.mmcr.api.network.RequestFailed;
import cn.howxu.mmcr.api.network.RequestProcess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.ArrayList;
import java.util.Objects;
import java.util.Set;

/**
 * Startup-time machine declaration. A registration must exist before controller
 * blocks are registered, and it intentionally contains no reloadable structure.
 *
 * @author howxu <dev@howxu.cn>
 */
public record MachineRegistration(
        Identifier id,
        String displayNameKey,
        MachineControllerSpec controllerSpec,
        MachineAppearanceSpec appearance,
        Identifier recipeFamilyId,
        boolean allowModifiers,
        boolean allowMultithreading,
        boolean allowParallelism,
        long maxParallelAmount,
        boolean expandableStructure,
        Map<String, SmartInterfaceType> smartInterfaceTypes,
        boolean shareSmartInterfaces,
        List<SmartInterfaceModifier> smartInterfaceModifiers,
        @Nullable Identifier runningSoundId,
        @Nullable Identifier finishSoundId,
        MachineRole role,
        Set<Identifier> acceptedModuleIds,
        NetworkInterfaceSpec networkInterface,
        BlockArray pattern,
        MachineBehavior behavior,
        Map<Identifier, RequestProcess> requestProcessors,
        Map<Identifier, RequestFailed> requestFailures
) {
    public MachineRegistration(Identifier id, String displayNameKey, MachineControllerSpec controllerSpec,
            MachineAppearanceSpec appearance, Identifier recipeFamilyId, boolean allowModifiers,
            boolean allowMultithreading, boolean allowParallelism, long maxParallelAmount,
            boolean expandableStructure, Map<String, SmartInterfaceType> smartInterfaceTypes,
            boolean shareSmartInterfaces, List<SmartInterfaceModifier> smartInterfaceModifiers,
            @Nullable Identifier runningSoundId, @Nullable Identifier finishSoundId, MachineRole role,
            Set<Identifier> acceptedModuleIds, NetworkInterfaceSpec networkInterface, BlockArray pattern,
            MachineBehavior behavior) {
        this(id, displayNameKey, controllerSpec, appearance, recipeFamilyId, allowModifiers, allowMultithreading,
                allowParallelism, maxParallelAmount, expandableStructure, smartInterfaceTypes, shareSmartInterfaces,
                smartInterfaceModifiers, runningSoundId, finishSoundId, role, acceptedModuleIds, networkInterface,
                pattern, behavior, Map.of(), Map.of());
    }
    public MachineRegistration {
        if (id == null) throw new IllegalArgumentException("id null");
        displayNameKey = defaultDisplayNameKey(id, displayNameKey);
        controllerSpec = controllerSpec == null ? MachineControllerSpec.defaultsFor(id) : controllerSpec;
        appearance = appearance == null ? MachineAppearanceSpec.defaults() : appearance;
        recipeFamilyId = recipeFamilyId == null ? id : recipeFamilyId;
        maxParallelAmount = Math.max(1, maxParallelAmount);
        smartInterfaceTypes = Collections.unmodifiableMap(new LinkedHashMap<>(smartInterfaceTypes));
        smartInterfaceModifiers = smartInterfaceModifiers == null ? List.of() : List.copyOf(smartInterfaceModifiers);
        if (runningSoundId != null) validateSound(runningSoundId);
        if (finishSoundId != null) validateSound(finishSoundId);
        role = role == null ? MachineRole.NORMAL : role;
        acceptedModuleIds = copyAcceptedModuleIds(acceptedModuleIds);
        networkInterface = networkInterface == null ? NetworkInterfaceSpec.disabled() : networkInterface;
        if (role != MachineRole.HOST && !acceptedModuleIds.isEmpty()) {
            throw new IllegalArgumentException("Only HOST machines may accept modules");
        }
        pattern = pattern == null ? new BlockArray(Map.of()) : pattern;
        behavior = Objects.requireNonNull(behavior, "behavior");
        requestProcessors = Collections.unmodifiableMap(new LinkedHashMap<>(requestProcessors == null ? Map.of() : requestProcessors));
        requestFailures = Collections.unmodifiableMap(new LinkedHashMap<>(requestFailures == null ? Map.of() : requestFailures));
    }

    public MachineRegistration(Identifier id, String displayNameKey, MachineControllerSpec controllerSpec,
            MachineAppearanceSpec appearance, Identifier recipeFamilyId, boolean allowModifiers,
            boolean allowMultithreading, boolean allowParallelism, long maxParallelAmount,
            boolean expandableStructure, Map<String, SmartInterfaceType> smartInterfaceTypes,
            boolean shareSmartInterfaces, List<SmartInterfaceModifier> smartInterfaceModifiers,
            @Nullable Identifier runningSoundId, @Nullable Identifier finishSoundId, MachineRole role,
            Set<Identifier> acceptedModuleIds, BlockArray pattern) {
        this(id, displayNameKey, controllerSpec, appearance, recipeFamilyId, allowModifiers,
                allowMultithreading, allowParallelism, maxParallelAmount, expandableStructure,
                smartInterfaceTypes, shareSmartInterfaces, smartInterfaceModifiers, runningSoundId,
                finishSoundId, role, acceptedModuleIds, pattern, RecipeBehavior.defaults());
    }

    public MachineRegistration(Identifier id, String displayNameKey, MachineControllerSpec controllerSpec,
            MachineAppearanceSpec appearance, Identifier recipeFamilyId, boolean allowModifiers,
            boolean allowMultithreading, boolean allowParallelism, long maxParallelAmount,
            boolean expandableStructure, Map<String, SmartInterfaceType> smartInterfaceTypes,
            boolean shareSmartInterfaces, List<SmartInterfaceModifier> smartInterfaceModifiers,
            @Nullable Identifier runningSoundId, @Nullable Identifier finishSoundId, MachineRole role,
            Set<Identifier> acceptedModuleIds, BlockArray pattern, MachineBehavior behavior) {
        this(id, displayNameKey, controllerSpec, appearance, recipeFamilyId, allowModifiers,
                allowMultithreading, allowParallelism, maxParallelAmount, expandableStructure,
                smartInterfaceTypes, shareSmartInterfaces, smartInterfaceModifiers, runningSoundId,
                finishSoundId, role, acceptedModuleIds, NetworkInterfaceSpec.disabled(), pattern, behavior);
    }

    public static Builder builder(Identifier id) {
        return new Builder(id);
    }

    public Component displayName() {
        return Component.translatable(displayNameKey);
    }

    @Deprecated(forRemoval = true)
    public String localizedName() {
        return displayNameKey;
    }

    public boolean isHost() {
        return role == MachineRole.HOST;
    }

    public boolean isModule() {
        return role == MachineRole.MODULE;
    }

    public MachineRegistration withPattern(BlockArray pattern) {
        return new MachineRegistration(id, displayNameKey, controllerSpec, appearance, recipeFamilyId, allowModifiers,
                allowMultithreading, allowParallelism, maxParallelAmount, expandableStructure, smartInterfaceTypes, shareSmartInterfaces,
                 smartInterfaceModifiers, runningSoundId, finishSoundId, role, acceptedModuleIds, networkInterface, pattern, behavior,
                requestProcessors, requestFailures);
    }

    public static String defaultDisplayNameKey(Identifier id) {
        return defaultDisplayNameKey(id, null);
    }

    public static String defaultDisplayNameKey(Identifier id, String explicitKey) {
        if (id == null) throw new IllegalArgumentException("id null");
        if (explicitKey != null && !explicitKey.isBlank()) return explicitKey;
        return "machine." + id.getNamespace() + "." + id.getPath();
    }

    private static Set<Identifier> copyAcceptedModuleIds(Set<Identifier> acceptedModuleIds) {
        if (acceptedModuleIds == null || acceptedModuleIds.isEmpty()) return Set.of();
        LinkedHashSet<Identifier> copy = new LinkedHashSet<>();
        for (Identifier acceptedModuleId : acceptedModuleIds) {
            if (acceptedModuleId == null) throw new IllegalArgumentException("accepted module id null");
            copy.add(acceptedModuleId);
        }
        return Collections.unmodifiableSet(copy);
    }

    public static final class Builder {
        private final Identifier id;
        private String displayNameKey;
        private MachineControllerSpec controllerSpec;
        private MachineAppearanceSpec appearance;
        private Identifier recipeFamilyId;
        private boolean allowModifiers;
        private boolean allowMultithreading;
        private boolean allowParallelism;
        private long maxParallelAmount = 1L;
        private boolean expandableStructure;
        private final Map<String, SmartInterfaceType> smartInterfaceTypes = new LinkedHashMap<>();
        private boolean shareSmartInterfaces;
        private final List<SmartInterfaceModifier> smartInterfaceModifiers = new ArrayList<>();
        private @Nullable Identifier runningSoundId;
        private @Nullable Identifier finishSoundId;
        private boolean host;
        private boolean module;
        private final Set<Identifier> acceptedModuleIds = new LinkedHashSet<>();
        private NetworkInterfaceSpec networkInterface = NetworkInterfaceSpec.disabled();
        private BlockArray pattern;
        private MachineBehavior behavior = RecipeBehavior.defaults();
        private final Map<Identifier, RequestProcess> requestProcessors = new LinkedHashMap<>();
        private final Map<Identifier, RequestFailed> requestFailures = new LinkedHashMap<>();

        private Builder(Identifier id) {
            this.id = id;
        }

        public Builder displayNameKey(String displayNameKey) {
            this.displayNameKey = displayNameKey;
            return this;
        }

        @Deprecated(forRemoval = true)
        public Builder localizedName(String localizedName) {
            return displayNameKey(localizedName);
        }

        public Builder controllerSpec(MachineControllerSpec controllerSpec) {
            this.controllerSpec = controllerSpec;
            return this;
        }

        public Builder appearance(MachineAppearanceSpec appearance) {
            this.appearance = appearance;
            return this;
        }

        public Builder recipeFamilyId(Identifier recipeFamilyId) {
            this.recipeFamilyId = recipeFamilyId;
            return this;
        }

        public Builder allowModifiers(boolean allowModifiers) {
            this.allowModifiers = allowModifiers;
            return this;
        }

        public Builder allowMultithreading(boolean allowMultithreading) {
            this.allowMultithreading = allowMultithreading;
            return this;
        }

        public Builder allowParallelism(boolean allowParallelism) {
            this.allowParallelism = allowParallelism;
            return this;
        }

        public Builder maxParallelAmount(long maxParallelAmount) {
            this.maxParallelAmount = maxParallelAmount;
            return this;
        }

        public Builder expandableStructure() {
            this.expandableStructure = true;
            return this;
        }

        public Builder smartInterfaceType(SmartInterfaceType smartInterfaceType) {
            if (smartInterfaceTypes.putIfAbsent(smartInterfaceType.type(), smartInterfaceType) != null) {
                throw new IllegalArgumentException("Duplicate smart interface type: " + smartInterfaceType.type());
            }
            return this;
        }

        public Builder shareSmartInterfaces(boolean shareSmartInterfaces) {
            this.shareSmartInterfaces = shareSmartInterfaces;
            return this;
        }

        public Builder smartInterfaceModifier(SmartInterfaceModifier modifier) {
            smartInterfaceModifiers.add(Objects.requireNonNull(modifier));
            return this;
        }

        public Builder runningSound(Identifier id) {
            if (id != null) MachineRegistration.validateSound(id);
            this.runningSoundId = id;
            return this;
        }

        public Builder runningSound(SoundEvent sound) {
            return runningSound(soundId(sound));
        }

        public Builder finishSound(Identifier id) {
            if (id != null) MachineRegistration.validateSound(id);
            this.finishSoundId = id;
            return this;
        }

        public Builder finishSound(SoundEvent sound) {
            return finishSound(soundId(sound));
        }

        public Builder host(Identifier acceptedModuleId) {
            this.host = true;
            if (acceptedModuleId == null) throw new IllegalArgumentException("accepted module id null");
            this.acceptedModuleIds.add(acceptedModuleId);
            return this;
        }

        public Builder host(String acceptedModuleId) {
            return host(Identifier.parse(acceptedModuleId));
        }

        public Builder networkInterface(NetworkInterfaceSpec networkInterface) {
            this.networkInterface = Objects.requireNonNull(networkInterface, "networkInterface");
            return this;
        }

        public Builder module() {
            this.module = true;
            return this;
        }

        public Builder pattern(BlockArray pattern) {
            this.pattern = pattern;
            return this;
        }

        public Builder behavior(MachineBehavior behavior) {
            this.behavior = Objects.requireNonNull(behavior, "behavior");
            return this;
        }

        public Builder requestProcess(Identifier requestId, RequestProcess process) {
            if (requestProcessors.putIfAbsent(Objects.requireNonNull(requestId, "requestId"),
                    Objects.requireNonNull(process, "process")) != null) {
                throw new IllegalArgumentException("Duplicate request processor: " + requestId);
            }
            return this;
        }

        public Builder requestFailed(Identifier requestId, RequestFailed failure) {
            if (requestFailures.putIfAbsent(Objects.requireNonNull(requestId, "requestId"),
                    Objects.requireNonNull(failure, "failure")) != null) {
                throw new IllegalArgumentException("Duplicate request failure handler: " + requestId);
            }
            return this;
        }

        public MachineRegistration build() {
            if (host && module) {
                throw new IllegalArgumentException("Machine roles are mutually exclusive");
            }
            MachineRole role = host ? MachineRole.HOST : module ? MachineRole.MODULE : MachineRole.NORMAL;
            return new MachineRegistration(id, displayNameKey, controllerSpec, appearance, recipeFamilyId, allowModifiers,
                    allowMultithreading, allowParallelism, maxParallelAmount, expandableStructure, smartInterfaceTypes, shareSmartInterfaces,
                     smartInterfaceModifiers, runningSoundId, finishSoundId, role, acceptedModuleIds, networkInterface, pattern, behavior,
                    requestProcessors, requestFailures);
        }

        private static Identifier soundId(SoundEvent sound) {
            Identifier id = BuiltInRegistries.SOUND_EVENT.getKey(sound);
            if (id == null) throw new ApiRegistrationException("Unregistered sound event " + sound);
            return id;
        }

    }

    public static void validateSound(Identifier id) {
        if (id == null || !BuiltInRegistries.SOUND_EVENT.containsKey(id)) {
            throw new ApiRegistrationException("Unknown sound event " + id);
        }
    }
}

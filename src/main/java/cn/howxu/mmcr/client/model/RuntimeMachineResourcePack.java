package cn.howxu.mmcr.client.model;

import cn.howxu.mmcr.MMCR;
import net.minecraft.SharedConstants;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackSelectionConfig;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.metadata.MetadataSectionSerializer;
import net.minecraft.server.packs.metadata.pack.PackMetadataSection;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.server.packs.repository.RepositorySource;
import net.minecraft.server.packs.resources.IoSupplier;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.LinkedHashMap;

/**
 * Client-only resource pack that supplies dynamic machine blockstate and item model definitions.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class RuntimeMachineResourcePack implements PackResources {
    private static final String PACK_ID = "mmcr/runtime_machine_models";
    private static final PackLocationInfo LOCATION = new PackLocationInfo(
            PACK_ID, Component.translatable("pack.mmc.dynamical_resources"), PackSource.BUILT_IN, Optional.empty());

    private final PackLocationInfo location;

    RuntimeMachineResourcePack(PackLocationInfo location) {
        this.location = location;
    }

    public static RepositorySource source() {
        return output -> {
            Pack pack = Pack.readMetaAndCreate(
                    LOCATION,
                    new Supplier(),
                    PackType.CLIENT_RESOURCES,
                    new PackSelectionConfig(true, Pack.Position.TOP, true));
            if (pack != null) {
                output.accept(pack);
            }
        };
    }

    static Map<ResourceLocation, String> resources() {
        Map<ResourceLocation, String> resources = new LinkedHashMap<>();
        resources.put(MMCR.id("models/block/" + DynamicOverlayModelLoader.CONTROLLER_ID.getPath() + ".json"),
                geometryModelJson(DynamicOverlayModelLoader.CONTROLLER_ID));
        resources.put(MMCR.id("models/block/" + DynamicOverlayModelLoader.PORT_ID.getPath() + ".json"),
                geometryModelJson(DynamicOverlayModelLoader.PORT_ID));
        RuntimeMachineModelRegistry.definitions().forEach(definition -> {
            String name = definition.blockName();
            resources.put(MMCR.id("blockstates/" + name + ".json"),
                    RuntimeMachineModelRegistry.blockStateJson(definition.blockStateDefinition()));
            resources.put(MMCR.id("models/item/" + name + ".json"),
                    RuntimeMachineModelRegistry.itemModelJson(definition));
        });
        return Map.copyOf(resources);
    }

    @Override
    public IoSupplier<InputStream> getRootResource(String... path) {
        if (path.length == 1 && PackResources.PACK_META.equals(path[0])) {
            int packVersion = SharedConstants.getCurrentVersion().getPackVersion(PackType.CLIENT_RESOURCES);
            return bytes("{\"pack\":{\"description\":\"MMCR Runtime Dynamical Resources\",\"pack_format\":"
                    + packVersion + "}}\n");
        }
        return null;
    }

    @Override
    public IoSupplier<InputStream> getResource(PackType type, ResourceLocation id) {
        if (type != PackType.CLIENT_RESOURCES || !MMCR.MODID.equals(id.getNamespace())) {
            return null;
        }
        String content = resources().get(id);
        return content == null ? null : bytes(content);
    }

    @Override
    public void listResources(PackType type, String namespace, String path, ResourceOutput output) {
        if (type != PackType.CLIENT_RESOURCES || !MMCR.MODID.equals(namespace)) {
            return;
        }
        resources().forEach((id, content) -> {
            if (id.getPath().startsWith(path)) {
                output.accept(id, bytes(content));
            }
        });
    }

    @Override
    public Set<String> getNamespaces(PackType type) {
        return type == PackType.CLIENT_RESOURCES ? Set.of(MMCR.MODID) : Set.of();
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T getMetadataSection(MetadataSectionSerializer<T> type) throws IOException {
        if (type == PackMetadataSection.TYPE) {
            return (T) new PackMetadataSection(
                    Component.literal("Runtime BuildIn"),
                    SharedConstants.getCurrentVersion().getPackVersion(PackType.CLIENT_RESOURCES));
        }
        return null;
    }

    @Override
    public PackLocationInfo location() {
        return location;
    }

    @Override
    public void close() {
    }

    private static IoSupplier<InputStream> bytes(String content) {
        byte[] data = content.getBytes(StandardCharsets.UTF_8);
        return () -> new ByteArrayInputStream(data);
    }

    private static String geometryModelJson(ResourceLocation loader) {
        return "{\n  \"loader\": \"" + loader + "\"\n}\n";
    }

    private static final class Supplier implements Pack.ResourcesSupplier {
        @Override
        public PackResources openPrimary(PackLocationInfo location) {
            return new RuntimeMachineResourcePack(location);
        }

        @Override
        public PackResources openFull(PackLocationInfo location, Pack.Metadata metadata) {
            return new RuntimeMachineResourcePack(location);
        }
    }
}

package cn.howxu.mmcr.client.model;

import cn.howxu.mmcr.MMCR;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackSelectionConfig;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.metadata.MetadataSectionType;
import net.minecraft.server.packs.metadata.pack.PackFormat;
import net.minecraft.server.packs.metadata.pack.PackMetadataSection;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.server.packs.repository.RepositorySource;
import net.minecraft.server.packs.resources.IoSupplier;
import net.minecraft.util.InclusiveRange;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Client-only resource pack that supplies dynamic machine blockstate and item model definitions.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class RuntimeMachineResourcePack implements PackResources {
    private static final String PACK_ID = "mmcr/runtime_machine_models";
    private static final PackLocationInfo LOCATION = new PackLocationInfo(
            PACK_ID, Component.literal("MMCR Runtime Machine Models"), PackSource.BUILT_IN, Optional.empty());

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
                    new PackSelectionConfig(true, Pack.Position.BOTTOM, true));
            if (pack != null) {
                output.accept(pack);
            }
        };
    }

    static Map<Identifier, String> resources() {
        Map<Identifier, String> resources = new java.util.LinkedHashMap<>();
        RuntimeMachineModelRegistry.definitions().forEach(definition -> {
            String name = definition.blockName();
            resources.put(MMCR.id("blockstates/" + name + ".json"),
                    RuntimeMachineModelRegistry.blockStateJson(definition.blockStateDefinition()));
            resources.put(MMCR.id("items/" + name + ".json"), RuntimeMachineModelRegistry.itemDefinitionJson());
        });
        return Map.copyOf(resources);
    }

    @Override
    public IoSupplier<InputStream> getRootResource(String... path) {
        if (path.length == 1 && PackResources.PACK_META.equals(path[0])) {
            return bytes("{\"pack\":{\"description\":\"MMCR Runtime Machine Models\",\"pack_format\":1}}\n");
        }
        return null;
    }

    @Override
    public IoSupplier<InputStream> getResource(PackType type, Identifier id) {
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
    public <T> T getMetadataSection(MetadataSectionType<T> type) throws IOException {
        if (type == PackMetadataSection.CLIENT_TYPE || type == PackMetadataSection.FALLBACK_TYPE) {
            return (T) new PackMetadataSection(
                    Component.literal("MMCR Runtime Machine Models"),
                    new InclusiveRange<>(PackFormat.of(1), PackFormat.of(Integer.MAX_VALUE)));
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

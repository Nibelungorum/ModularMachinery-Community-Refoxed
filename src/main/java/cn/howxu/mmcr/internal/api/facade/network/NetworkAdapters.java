package cn.howxu.mmcr.internal.api.facade.network;

import cn.howxu.mmcr.api.machine.NetworkInterfaceSpec;
import cn.howxu.mmcr.api.network.view.MachineReference;
import cn.howxu.mmcr.api.network.view.NetworkApi;
import cn.howxu.mmcr.api.network.view.NetworkInterfaceReference;
import cn.howxu.mmcr.api.network.view.RequestBody;
import cn.howxu.mmcr.api.network.view.RequestInfo;
import cn.howxu.mmcr.api.network.view.RequestProcess;
import cn.howxu.mmcr.api.network.view.RequestFailed;
import cn.howxu.mmcr.api.network.view.RequestFailureReason;
import cn.howxu.mmcr.api.data.view.DataValue;
import cn.howxu.mmcr.api.data.DataStorage;
import cn.howxu.mmcr.internal.api.facade.behavior.BehaviorAdapters;
import cn.howxu.mmcr.internal.api.facade.data.StorageAdapters;
import cn.howxu.mmcr.publicapi.behavior.MachineContext;
import cn.howxu.mmcr.publicapi.data.DataKey;
import cn.howxu.mmcr.publicapi.data.DataStore;
import cn.howxu.mmcr.publicapi.network.NodeView;
import cn.howxu.mmcr.publicapi.network.NetworkPortView;
import cn.howxu.mmcr.publicapi.network.RequestPayload;
import cn.howxu.mmcr.publicapi.network.RequestDetails;
import cn.howxu.mmcr.publicapi.network.RequestHandler;
import cn.howxu.mmcr.publicapi.network.FailureHandler;
import cn.howxu.mmcr.publicapi.network.RequestFailure;
import cn.howxu.mmcr.publicapi.network.NetworkSettings;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** Java callback boundary, without duplicating dispatcher or queue behavior.
 * @author howxu <dev@howxu.cn>
 */
public final class NetworkAdapters {
    private NetworkAdapters() {}
    public static NodeView wrap(MachineReference value) { return new NodeAdapter(value); }
    public static MachineReference unwrap(NodeView value) { return ((NodeAdapter) value).delegate; }
    public static NetworkPortView wrap(NetworkInterfaceReference value) { return new PortAdapter(value); }
    public static NetworkInterfaceReference unwrap(NetworkPortView value) { return ((PortAdapter) value).delegate; }
    public static RequestPayload wrap(RequestBody value) { return new PayloadAdapter(value); }
    public static RequestBody unwrap(RequestPayload value) { return ((PayloadAdapter) value).delegate; }
    public static RequestDetails wrap(RequestInfo value) { return new DetailsAdapter(value); }
    public static NodeView node(ResourceLocation type, long hash) { return wrap(new MachineReference(type, hash)); }
    public static RequestPayload payload(Map<String, DataKey> values) {
        Map<String, DataValue> converted = new LinkedHashMap<>();
        values.forEach((key, value) -> converted.put(key, StorageAdapters.unwrap(value)));
        return wrap(RequestBody.of(converted));
    }
    public static NetworkSettings settings(NetworkInterfaceSpec value) { return new SettingsAdapter(value); }
    public static NetworkSettings settings(int count, int connections, Set<ResourceLocation> allowed) {
        return settings(new NetworkInterfaceSpec(count, connections, allowed));
    }
    public static NetworkInterfaceSpec toCore(NetworkSettings value) { return ((SettingsAdapter) value).delegate; }
    public static List<NetworkPortView> interfaces(MachineContext context) {
        return NetworkApi.interfaces(BehaviorAdapters.unwrap(context)).stream().map(NetworkAdapters::wrap).toList();
    }
    public static void sendRequest(NetworkPortView source, NodeView target, ResourceLocation id, RequestPayload payload) {
        NetworkApi.sendRequest(unwrap(source), unwrap(target), id, unwrap(payload));
    }
    public static RequestProcess toCore(RequestHandler handler) {
        Objects.requireNonNull(handler, "handler");
        return (body, request, sender, receiver) -> handler.process(wrap(body), wrap(request),
                sender == null ? null : StorageAdapters.wrap(sender), receiver == null ? null : StorageAdapters.wrap(receiver));
    }
    public static RequestFailed toCore(FailureHandler handler) {
        Objects.requireNonNull(handler, "handler");
        return (body, request, sender, reason) -> handler.fail(wrap(body), wrap(request),
                sender == null ? null : StorageAdapters.wrap(sender), wrap(reason));
    }
    public static RequestHandler toPublic(cn.howxu.mmcr.api.network.RequestProcess handler) {
        Objects.requireNonNull(handler, "handler");
        return (body, request, sender, receiver) -> handler.process(nativeBody(body), nativeRequest(request),
                nativeStorage(sender), nativeStorage(receiver));
    }
    public static FailureHandler toPublic(RequestFailed handler) {
        Objects.requireNonNull(handler, "handler");
        return (body, request, sender, reason) -> handler.fail(unwrap(body),
                new RequestInfo(request.requestId(), unwrap(request.peer())),
                sender == null ? null : StorageAdapters.unwrap(sender), unwrap(reason));
    }
    private static cn.howxu.mmcr.api.network.RequestBody nativeBody(RequestPayload value) {
        return unwrap(value).toInternal();
    }
    private static cn.howxu.mmcr.api.network.RequestInfo nativeRequest(RequestDetails value) {
        return new cn.howxu.mmcr.api.network.RequestInfo(value.requestId(),
                unwrap(value.peer()).toInternal());
    }
    private static @Nullable DataStorage nativeStorage(@Nullable DataStore value) {
        return value == null ? null : StorageAdapters.unwrap(value).toInternal();
    }
    private static RequestFailureReason unwrap(RequestFailure value) {
        return switch (value) {
            case SOURCE_INTERFACE_MISSING -> RequestFailureReason.SOURCE_INTERFACE_MISSING;
            case TARGET_INTERFACE_MISSING -> RequestFailureReason.TARGET_INTERFACE_MISSING;
            case TARGET_CHUNK_UNLOADED -> RequestFailureReason.TARGET_CHUNK_UNLOADED;
            case CONNECTION_MISSING -> RequestFailureReason.CONNECTION_MISSING;
            case SOURCE_STRUCTURE_INVALID -> RequestFailureReason.SOURCE_STRUCTURE_INVALID;
            case TARGET_STRUCTURE_INVALID -> RequestFailureReason.TARGET_STRUCTURE_INVALID;
            case HASH_MISMATCH -> RequestFailureReason.HASH_MISMATCH;
            case ALLOWLIST_REJECTED -> RequestFailureReason.ALLOWLIST_REJECTED;
            case TARGET_HANDLER_MISSING -> RequestFailureReason.TARGET_HANDLER_MISSING;
            case UNREACHABLE -> RequestFailureReason.UNREACHABLE;
        };
    }
    public static RequestFailure wrap(RequestFailureReason value) {
        return switch (value) {
            case SOURCE_INTERFACE_MISSING -> RequestFailure.SOURCE_INTERFACE_MISSING;
            case TARGET_INTERFACE_MISSING -> RequestFailure.TARGET_INTERFACE_MISSING;
            case TARGET_CHUNK_UNLOADED -> RequestFailure.TARGET_CHUNK_UNLOADED;
            case CONNECTION_MISSING -> RequestFailure.CONNECTION_MISSING;
            case SOURCE_STRUCTURE_INVALID -> RequestFailure.SOURCE_STRUCTURE_INVALID;
            case TARGET_STRUCTURE_INVALID -> RequestFailure.TARGET_STRUCTURE_INVALID;
            case HASH_MISMATCH -> RequestFailure.HASH_MISMATCH;
            case ALLOWLIST_REJECTED -> RequestFailure.ALLOWLIST_REJECTED;
            case TARGET_HANDLER_MISSING -> RequestFailure.TARGET_HANDLER_MISSING;
            case UNREACHABLE -> RequestFailure.UNREACHABLE;
        };
    }
    private record NodeAdapter(MachineReference delegate) implements NodeView {
        private NodeAdapter { Objects.requireNonNull(delegate); }
        public ResourceLocation type() { return delegate.type(); }
        public long hash() { return delegate.hash(); }
    }
    private record PortAdapter(NetworkInterfaceReference delegate) implements NetworkPortView {
        private PortAdapter { Objects.requireNonNull(delegate); }
        public BlockPos position() { return delegate.position(); }
        public List<NodeView> connections() { return delegate.connections().stream().map(NetworkAdapters::wrap).toList(); }
    }
    private record PayloadAdapter(RequestBody delegate) implements RequestPayload {
        private PayloadAdapter { Objects.requireNonNull(delegate); }
        public Map<String, DataKey> values() { return StorageAdapters.values(delegate.values()); }
        public Optional<DataKey> get(String key) { return delegate.get(key).map(StorageAdapters::wrap); }
    }
    private record DetailsAdapter(RequestInfo delegate) implements RequestDetails {
        public ResourceLocation requestId() { return delegate.requestId(); }
        public NodeView peer() { return wrap(delegate.peer()); }
    }
    private record SettingsAdapter(NetworkInterfaceSpec delegate) implements NetworkSettings {
        public int maxCount() { return delegate.maxCount(); }
        public int maxConnections() { return delegate.maxConnections(); }
        public Set<ResourceLocation> allowedMachineIds() { return delegate.allowedMachineIds(); }
        public NetworkSettings withAllowedMachine(ResourceLocation id) { return settings(delegate.withAllowedMachine(id)); }
    }
}

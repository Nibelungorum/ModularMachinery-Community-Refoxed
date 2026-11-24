package cn.howxu.mmcr.compat.appliedenergistics2.loaded.storage;

import cn.howxu.mmcr.MMCR;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Server-wide directory of bound mirrors with unpaid contents. Never owns an AE key or an amount.
 * @author HowXu {@literal <dev@howxu.cn>}
 */
public final class PatternRefundIndex extends SavedData {
    /** The instance, not just its coordinates, is entitled to receive the request.
     * @author HowXu {@literal <dev@howxu.cn>}
     */
    public record Member(GlobalPos mirror, UUID instance, GlobalPos master, boolean requested) {
        private static final Codec<Member> CODEC = RecordCodecBuilder.create(i -> i.group(
                GlobalPos.CODEC.fieldOf("mirror").forGetter(Member::mirror), UUIDUtil.CODEC.fieldOf("instance").forGetter(Member::instance),
                GlobalPos.CODEC.fieldOf("master").forGetter(Member::master), Codec.BOOL.fieldOf("requested").forGetter(Member::requested)
        ).apply(i, Member::new));
    }

    private static final Codec<PatternRefundIndex> CODEC = Member.CODEC.listOf().fieldOf("members").codec().xmap(
            entries -> { var result = new PatternRefundIndex(); entries.forEach(result::put); return result; },
            index -> List.copyOf(index.members.values()));
    private static final String ID = MMCR.MODID + "_pattern_refund_index";
    private static final Factory<PatternRefundIndex> FACTORY = new Factory<>(PatternRefundIndex::new, PatternRefundIndex::load);
    private final Map<GlobalPos, Member> members = new HashMap<>();
    private final Map<GlobalPos, Set<GlobalPos>> byMaster = new HashMap<>();

    public static @Nullable PatternRefundIndex get(ServerLevel level) {
        return level.getServer().overworld().getDataStorage().get(FACTORY, ID);
    }

    public static PatternRefundIndex getOrCreate(ServerLevel level) {
        return level.getServer().overworld().getDataStorage().computeIfAbsent(FACTORY, ID);
    }

    static PatternRefundIndex load(CompoundTag tag, HolderLookup.Provider registries) {
        return CODEC.parse(registries.createSerializationContext(NbtOps.INSTANCE), tag).getOrThrow();
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        return (CompoundTag) CODEC.encode(this, registries.createSerializationContext(NbtOps.INSTANCE), tag).getOrThrow();
    }

    private void put(Member next) {
        var previous = members.put(next.mirror, next);
        if (previous != null && !previous.master.equals(next.master)) unlink(previous);
        byMaster.computeIfAbsent(next.master, ignored -> new HashSet<>()).add(next.mirror);
    }

    private void unlink(Member member) {
        var group = byMaster.get(member.master);
        group.remove(member.mirror);
        if (group.isEmpty()) byMaster.remove(member.master);
    }

    public @Nullable Member member(GlobalPos mirror) { return members.get(mirror); }
    public boolean hasMembers(GlobalPos master) { return byMaster.containsKey(master); }

    /** Called by the actual loaded instance, after replay, to publish its current binding and nonempty state. */
    public void observe(GlobalPos mirror, UUID instance, @Nullable GlobalPos master, boolean nonEmpty) {
        var old = member(mirror);
        if (master == null || !nonEmpty) {
            if (old != null) forget(mirror, old.instance);
        } else if (old == null || !old.master.equals(master) || !old.instance.equals(instance)) {
            put(new Member(mirror, instance, master, false));
            setDirty();
        }
    }

    public List<Member> request(GlobalPos master) {
        var selected = new ArrayList<Member>();
        for (var pos : byMaster.getOrDefault(master, Set.of())) {
            var member = members.get(pos);
            if (!member.requested) {
                member = new Member(pos, member.instance, master, true);
                members.put(pos, member);
                setDirty();
            }
            selected.add(member);
        }
        return List.copyOf(selected);
    }

    public void acknowledge(GlobalPos mirror, UUID instance) {
        var old = member(mirror);
        if (old != null && old.instance.equals(instance) && old.requested) {
            members.put(mirror, new Member(mirror, instance, old.master, false));
            setDirty();
        }
    }

    public void forget(GlobalPos mirror, UUID instance) {
        var old = member(mirror);
        if (old != null && old.instance.equals(instance)) {
            members.remove(mirror);
            unlink(old);
            setDirty();
        }
    }
}

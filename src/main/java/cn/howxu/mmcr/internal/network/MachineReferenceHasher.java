package cn.howxu.mmcr.internal.network;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;

import java.util.Objects;

/**
 * Computes allocation-free XXH64 identities for formed machine controllers.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class MachineReferenceHasher {
    private static final long SEED = 0x4D4D43525F4E4554L;
    private static final long PRIME1 = 0x9E3779B185EBCA87L;
    private static final long PRIME2 = 0xC2B2AE3D27D4EB4FL;
    private static final long PRIME3 = 0x165667B19E3779F9L;
    private static final long PRIME4 = 0x85EBCA77C2B2AE63L;
    private static final long PRIME5 = 0x27D4EB2F165667C5L;

    private MachineReferenceHasher() {
    }

    static long hash(Identifier dimension, Identifier machineType, BlockPos controllerPos) {
        Objects.requireNonNull(dimension, "dimension");
        Objects.requireNonNull(machineType, "machineType");
        Objects.requireNonNull(controllerPos, "controllerPos");

        String dimensionNamespace = dimension.getNamespace();
        String dimensionPath = dimension.getPath();
        String machineNamespace = machineType.getNamespace();
        String machinePath = machineType.getPath();
        int dimensionNamespaceLength = utf8Length(dimensionNamespace);
        int dimensionPathLength = utf8Length(dimensionPath);
        int machineNamespaceLength = utf8Length(machineNamespace);
        int machinePathLength = utf8Length(machinePath);
        int length = 33 + dimensionNamespaceLength + dimensionPathLength
                + machineNamespaceLength + machinePathLength;

        long hash;
        int offset = 0;
        if (length >= 32) {
            long v1 = SEED + PRIME1 + PRIME2;
            long v2 = SEED + PRIME2;
            long v3 = SEED;
            long v4 = SEED - PRIME1;
            while (offset <= length - 32) {
                v1 = round(v1, readLong(offset, dimension, machineType, controllerPos,
                        dimensionNamespaceLength, dimensionPathLength, machineNamespaceLength, machinePathLength));
                v2 = round(v2, readLong(offset + 8, dimension, machineType, controllerPos,
                        dimensionNamespaceLength, dimensionPathLength, machineNamespaceLength, machinePathLength));
                v3 = round(v3, readLong(offset + 16, dimension, machineType, controllerPos,
                        dimensionNamespaceLength, dimensionPathLength, machineNamespaceLength, machinePathLength));
                v4 = round(v4, readLong(offset + 24, dimension, machineType, controllerPos,
                        dimensionNamespaceLength, dimensionPathLength, machineNamespaceLength, machinePathLength));
                offset += 32;
            }
            hash = Long.rotateLeft(v1, 1) + Long.rotateLeft(v2, 7)
                    + Long.rotateLeft(v3, 12) + Long.rotateLeft(v4, 18);
            hash = mergeRound(hash, v1);
            hash = mergeRound(hash, v2);
            hash = mergeRound(hash, v3);
            hash = mergeRound(hash, v4);
        } else {
            hash = SEED + PRIME5;
        }

        hash += length;
        while (offset <= length - 8) {
            hash ^= round(0L, readLong(offset, dimension, machineType, controllerPos,
                    dimensionNamespaceLength, dimensionPathLength, machineNamespaceLength, machinePathLength));
            hash = Long.rotateLeft(hash, 27) * PRIME1 + PRIME4;
            offset += 8;
        }
        if (offset <= length - 4) {
            hash ^= (readInt(offset, dimension, machineType, controllerPos,
                    dimensionNamespaceLength, dimensionPathLength, machineNamespaceLength, machinePathLength)
                    & 0xFFFFFFFFL) * PRIME1;
            hash = Long.rotateLeft(hash, 23) * PRIME2 + PRIME3;
            offset += 4;
        }
        while (offset < length) {
            hash ^= (long) byteAt(offset, dimension, machineType, controllerPos,
                    dimensionNamespaceLength, dimensionPathLength, machineNamespaceLength, machinePathLength)
                    * PRIME5;
            hash = Long.rotateLeft(hash, 11) * PRIME1;
            offset++;
        }
        return avalanche(hash);
    }

    /**
     * Bridges the package-local hash implementation to the internal controller package.
     */
    public static long hashForController(Identifier dimension, Identifier machineType, BlockPos controllerPos) {
        return hash(dimension, machineType, controllerPos);
    }

    private static long round(long accumulator, long input) {
        accumulator += input * PRIME2;
        return Long.rotateLeft(accumulator, 31) * PRIME1;
    }

    private static long mergeRound(long accumulator, long value) {
        accumulator ^= round(0L, value);
        return accumulator * PRIME1 + PRIME4;
    }

    private static long avalanche(long hash) {
        hash ^= hash >>> 33;
        hash *= PRIME2;
        hash ^= hash >>> 29;
        hash *= PRIME3;
        return hash ^ (hash >>> 32);
    }

    private static int readInt(int offset, Identifier dimension, Identifier machineType, BlockPos controllerPos,
                               int dimensionNamespaceLength, int dimensionPathLength,
                               int machineNamespaceLength, int machinePathLength) {
        return byteAt(offset, dimension, machineType, controllerPos,
                dimensionNamespaceLength, dimensionPathLength, machineNamespaceLength, machinePathLength)
                | byteAt(offset + 1, dimension, machineType, controllerPos,
                dimensionNamespaceLength, dimensionPathLength, machineNamespaceLength, machinePathLength) << 8
                | byteAt(offset + 2, dimension, machineType, controllerPos,
                dimensionNamespaceLength, dimensionPathLength, machineNamespaceLength, machinePathLength) << 16
                | byteAt(offset + 3, dimension, machineType, controllerPos,
                dimensionNamespaceLength, dimensionPathLength, machineNamespaceLength, machinePathLength) << 24;
    }

    private static long readLong(int offset, Identifier dimension, Identifier machineType, BlockPos controllerPos,
                                int dimensionNamespaceLength, int dimensionPathLength,
                                int machineNamespaceLength, int machinePathLength) {
        return (long) byteAt(offset, dimension, machineType, controllerPos,
                dimensionNamespaceLength, dimensionPathLength, machineNamespaceLength, machinePathLength)
                | (long) byteAt(offset + 1, dimension, machineType, controllerPos,
                dimensionNamespaceLength, dimensionPathLength, machineNamespaceLength, machinePathLength) << 8
                | (long) byteAt(offset + 2, dimension, machineType, controllerPos,
                dimensionNamespaceLength, dimensionPathLength, machineNamespaceLength, machinePathLength) << 16
                | (long) byteAt(offset + 3, dimension, machineType, controllerPos,
                dimensionNamespaceLength, dimensionPathLength, machineNamespaceLength, machinePathLength) << 24
                | (long) byteAt(offset + 4, dimension, machineType, controllerPos,
                dimensionNamespaceLength, dimensionPathLength, machineNamespaceLength, machinePathLength) << 32
                | (long) byteAt(offset + 5, dimension, machineType, controllerPos,
                dimensionNamespaceLength, dimensionPathLength, machineNamespaceLength, machinePathLength) << 40
                | (long) byteAt(offset + 6, dimension, machineType, controllerPos,
                dimensionNamespaceLength, dimensionPathLength, machineNamespaceLength, machinePathLength) << 48
                | (long) byteAt(offset + 7, dimension, machineType, controllerPos,
                dimensionNamespaceLength, dimensionPathLength, machineNamespaceLength, machinePathLength) << 56;
    }

    private static int byteAt(int offset, Identifier dimension, Identifier machineType, BlockPos controllerPos,
                              int dimensionNamespaceLength, int dimensionPathLength,
                              int machineNamespaceLength, int machinePathLength) {
        if (offset == 0) return 0x01;
        offset--;
        if (offset < 4) return littleEndianByte(dimensionNamespaceLength, offset);
        offset -= 4;
        if (offset < dimensionNamespaceLength) return utf8ByteAt(dimension.getNamespace(), offset);
        offset -= dimensionNamespaceLength;
        if (offset < 4) return littleEndianByte(dimensionPathLength, offset);
        offset -= 4;
        if (offset < dimensionPathLength) return utf8ByteAt(dimension.getPath(), offset);
        offset -= dimensionPathLength;
        if (offset == 0) return 0x02;
        offset--;
        if (offset < 4) return littleEndianByte(controllerPos.getX(), offset);
        offset -= 4;
        if (offset == 0) return 0x03;
        offset--;
        if (offset < 4) return littleEndianByte(controllerPos.getY(), offset);
        offset -= 4;
        if (offset == 0) return 0x04;
        offset--;
        if (offset < 4) return littleEndianByte(controllerPos.getZ(), offset);
        offset -= 4;
        if (offset == 0) return 0x05;
        offset--;
        if (offset < 4) return littleEndianByte(machineNamespaceLength, offset);
        offset -= 4;
        if (offset < machineNamespaceLength) return utf8ByteAt(machineType.getNamespace(), offset);
        offset -= machineNamespaceLength;
        if (offset < 4) return littleEndianByte(machinePathLength, offset);
        offset -= 4;
        return utf8ByteAt(machineType.getPath(), offset);
    }

    private static int littleEndianByte(int value, int offset) {
        return value >>> (offset * 8) & 0xFF;
    }

    private static int utf8Length(String value) {
        int length = 0;
        for (int offset = 0; offset < value.length();) {
            int codePoint = value.codePointAt(offset);
            length += utf8Width(codePoint);
            offset += Character.charCount(codePoint);
        }
        return length;
    }

    private static int utf8ByteAt(String value, int byteIndex) {
        for (int offset = 0; offset < value.length();) {
            int codePoint = value.codePointAt(offset);
            int width = utf8Width(codePoint);
            if (byteIndex < width) {
                return switch (width) {
                    case 1 -> codePoint;
                    case 2 -> byteIndex == 0 ? 0xC0 | codePoint >>> 6 : 0x80 | codePoint & 0x3F;
                    case 3 -> switch (byteIndex) {
                        case 0 -> 0xE0 | codePoint >>> 12;
                        case 1 -> 0x80 | codePoint >>> 6 & 0x3F;
                        default -> 0x80 | codePoint & 0x3F;
                    };
                    default -> switch (byteIndex) {
                        case 0 -> 0xF0 | codePoint >>> 18;
                        case 1 -> 0x80 | codePoint >>> 12 & 0x3F;
                        case 2 -> 0x80 | codePoint >>> 6 & 0x3F;
                        default -> 0x80 | codePoint & 0x3F;
                    };
                };
            }
            byteIndex -= width;
            offset += Character.charCount(codePoint);
        }
        throw new IllegalArgumentException("UTF-8 byte index out of bounds");
    }

    private static int utf8Width(int codePoint) {
        if (codePoint <= 0x7F) return 1;
        if (codePoint <= 0x7FF) return 2;
        if (codePoint <= 0xFFFF) return 3;
        return 4;
    }
}

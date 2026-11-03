package cn.howxu.mmcr.internal.runtime;

import org.jetbrains.annotations.Nullable;

import java.util.AbstractList;
import java.util.List;
import java.util.Objects;
import java.util.RandomAccess;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;
import java.util.Iterator;
import java.util.ListIterator;

/**
 * Immutable lane-indexed snapshots; updates share untouched branches and omit empty slots.
 *
 * @author howxu <dev@howxu.cn>
 */
final class FactorySnapshotList<E> extends AbstractList<E> implements RandomAccess {
    private final @Nullable Node<E> root;

    FactorySnapshotList() {
        this(null);
    }

    private FactorySnapshotList(@Nullable Node<E> root) {
        this.root = root;
    }

    static <E> List<E> immutableCopy(@Nullable List<E> values) {
        return values instanceof FactorySnapshotList<?> ? values : List.copyOf(values == null ? List.of() : values);
    }

    FactorySnapshotList<E> with(int laneIndex, @Nullable E value) {
        Objects.checkIndex(laneIndex, FactoryRuntime.MAX_LANES);
        Node<E> next = update(root, 0, FactoryRuntime.MAX_LANES, laneIndex, value);
        return next == root ? this : new FactorySnapshotList<>(next);
    }

    @Override
    public int size() {
        return size(root);
    }

    @Override
    public E get(int index) {
        Objects.checkIndex(index, size());
        Node<E> node = root;
        while (node.value == null) {
            int leftSize = size(node.left);
            if (index < leftSize) node = node.left;
            else {
                index -= leftSize;
                node = node.right;
            }
        }
        return node.value;
    }

    private static int size(@Nullable Node<?> node) {
        return node == null ? 0 : node.size;
    }

    @Override public Iterator<E> iterator() { return listIterator(0); }
    @Override public List<E> subList(int fromIndex, int toIndex) {
        return Collections.unmodifiableList(super.subList(fromIndex, toIndex));
    }
    @Override public ListIterator<E> listIterator(int index) { return immutableIterator(super.listIterator(index)); }
    @Override public boolean addAll(Collection<? extends E> values) { throw new UnsupportedOperationException(); }
    @Override public boolean addAll(int index, Collection<? extends E> values) { throw new UnsupportedOperationException(); }
    @Override public boolean remove(Object value) { throw new UnsupportedOperationException(); }
    @Override public boolean removeAll(Collection<?> values) { throw new UnsupportedOperationException(); }
    @Override public boolean retainAll(Collection<?> values) { throw new UnsupportedOperationException(); }
    @Override public boolean removeIf(Predicate<? super E> filter) { throw new UnsupportedOperationException(); }
    @Override public void replaceAll(UnaryOperator<E> operator) { throw new UnsupportedOperationException(); }
    @Override public void sort(Comparator<? super E> comparator) { throw new UnsupportedOperationException(); }
    @Override public void clear() { throw new UnsupportedOperationException(); }

    private static <E> ListIterator<E> immutableIterator(ListIterator<E> iterator) {
        return new ListIterator<>() {
            @Override public boolean hasNext() { return iterator.hasNext(); }
            @Override public E next() { return iterator.next(); }
            @Override public boolean hasPrevious() { return iterator.hasPrevious(); }
            @Override public E previous() { return iterator.previous(); }
            @Override public int nextIndex() { return iterator.nextIndex(); }
            @Override public int previousIndex() { return iterator.previousIndex(); }
            @Override public void remove() { throw new UnsupportedOperationException(); }
            @Override public void set(E value) { throw new UnsupportedOperationException(); }
            @Override public void add(E value) { throw new UnsupportedOperationException(); }
        };
    }

    private static <E> @Nullable Node<E> update(@Nullable Node<E> node, int from, int to,
                                               int index, @Nullable E value) {
        if (to - from == 1) {
            if (value == null) return null;
            return node != null && node.value == value ? node : new Node<>(null, null, value, 1);
        }
        int middle = (from + to) >>> 1;
        Node<E> left = node == null ? null : node.left;
        Node<E> right = node == null ? null : node.right;
        Node<E> nextLeft = index < middle ? update(left, from, middle, index, value) : left;
        Node<E> nextRight = index < middle ? right : update(right, middle, to, index, value);
        if (nextLeft == left && nextRight == right) return node;
        int count = size(nextLeft) + size(nextRight);
        return count == 0 ? null : new Node<>(nextLeft, nextRight, null, count);
    }

    /** @author howxu <dev@howxu.cn> */
    private record Node<E>(@Nullable Node<E> left, @Nullable Node<E> right, @Nullable E value, int size) {
    }
}

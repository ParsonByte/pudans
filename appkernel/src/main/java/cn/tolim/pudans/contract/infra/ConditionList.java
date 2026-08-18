package cn.tolim.pudans.contract.infra;

import java.util.AbstractCollection;
import java.util.Deque;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Objects;
import java.util.function.Consumer;
import org.jspecify.annotations.Nullable;


public class ConditionList extends AbstractCollection<Condition> {
    private static final int EVICT_THRESHOLD = 20;
    private final Deque<Condition> conditions = new LinkedList<>();

    @Override
    public boolean add(Condition condition) {
        if (isSame(conditions.peekFirst(), condition)) {
            return false;
        }
        return conditions.add(condition);
    }

    public boolean addFirst(Condition condition) {
        if (isSame(conditions.peekFirst(), condition)) {
            return false;
        }
        conditions.addFirst(condition);
        return true;
    }


    public boolean addAndEvictFIFO(Condition condition) {
        return addAndEvictFIFO(condition, EVICT_THRESHOLD);
    }


    public boolean addAndEvictFIFO(Condition condition, int evictThreshold) {
        var current = getCondition(condition.getType());
        if (current != null) {

            if (Objects.equals(condition.getStatus(), current.getStatus())) {
                condition.setLastTransitionTime(current.getLastTransitionTime());
            }
        }

        conditions.remove(current);
        conditions.addFirst(condition);

        while (conditions.size() > evictThreshold) {
            removeLast();
        }
        return true;
    }

    private @Nullable Condition getCondition(String type) {
        for (Condition condition : conditions) {
            if (condition.getType().equals(type)) {
                return condition;
            }
        }
        return null;
    }

    public void remove(Condition condition) {
        conditions.remove(condition);
    }


    public @Nullable Condition peek() {
        return peekFirst();
    }

    public @Nullable Condition peekFirst() {
        return conditions.peekFirst();
    }

    public Condition removeLast() {
        return conditions.removeLast();
    }

    @Override
    public void clear() {
        conditions.clear();
    }

    public int size() {
        return conditions.size();
    }

    private boolean isSame(@Nullable Condition a, @Nullable Condition b) {
        if (a == null || b == null) {
            return false;
        }
        return Objects.equals(a.getType(), b.getType())
                && Objects.equals(a.getStatus(), b.getStatus())
                && Objects.equals(a.getReason(), b.getReason())
                && Objects.equals(a.getMessage(), b.getMessage());
    }

    @Override
    public Iterator<Condition> iterator() {
        return conditions.iterator();
    }

    @Override
    public void forEach(Consumer<? super Condition> action) {
        conditions.forEach(action);
    }

    @Override
    public boolean equals(@Nullable Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        ConditionList that = (ConditionList) o;
        return Objects.equals(conditions, that.conditions);
    }

    @Override
    public int hashCode() {
        return Objects.hash(conditions);
    }
}

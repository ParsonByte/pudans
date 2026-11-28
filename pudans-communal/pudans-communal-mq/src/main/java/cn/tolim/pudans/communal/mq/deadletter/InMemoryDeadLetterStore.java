package cn.tolim.pudans.communal.mq.deadletter;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

// Bounded in-memory dead letter store; oldest entries are evicted when capacity is reached.
public class InMemoryDeadLetterStore implements DeadLetterStore {

    private final int capacity;
    private final Map<String, DeadLetterRecord> records = new LinkedHashMap<>();

    public InMemoryDeadLetterStore(int capacity) {
        if (capacity < 1) {
            throw new IllegalArgumentException("capacity must be greater than or equal to 1");
        }
        this.capacity = capacity;
    }

    @Override
    public synchronized void store(DeadLetterRecord record) {
        if (record == null) {
            throw new IllegalArgumentException("record must not be null");
        }
        while (records.size() >= capacity) {
            String eldest = records.keySet().iterator().next();
            records.remove(eldest);
        }
        records.put(record.id(), record);
    }

    @Override
    public synchronized List<DeadLetterRecord> findAll() {
        return List.copyOf(records.values());
    }

    @Override
    public synchronized Optional<DeadLetterRecord> findById(String id) {
        return Optional.ofNullable(records.get(id));
    }

    @Override
    public synchronized boolean remove(String id) {
        return records.remove(id) != null;
    }

    @Override
    public synchronized int size() {
        return records.size();
    }

    @Override
    public synchronized void clear() {
        records.clear();
    }

    public int capacity() {
        return capacity;
    }
}
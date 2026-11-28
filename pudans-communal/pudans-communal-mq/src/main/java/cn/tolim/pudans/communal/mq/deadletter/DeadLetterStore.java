package cn.tolim.pudans.communal.mq.deadletter;

import java.util.List;
import java.util.Optional;

public interface DeadLetterStore {

    void store(DeadLetterRecord record);

    List<DeadLetterRecord> findAll();

    Optional<DeadLetterRecord> findById(String id);

    boolean remove(String id);

    int size();

    void clear();
}
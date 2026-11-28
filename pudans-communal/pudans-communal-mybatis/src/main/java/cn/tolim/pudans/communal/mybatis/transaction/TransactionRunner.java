package cn.tolim.pudans.communal.mybatis.transaction;

import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.function.Supplier;

// Programmatic transaction helper for explicit read-only and propagation control.
public class TransactionRunner {

    private final PlatformTransactionManager transactionManager;

    public TransactionRunner(PlatformTransactionManager transactionManager) {
        if (transactionManager == null) {
            throw new IllegalArgumentException("transactionManager must not be null");
        }
        this.transactionManager = transactionManager;
    }

    public <T> T execute(Supplier<T> action) {
        return execute(TransactionDefinition.PROPAGATION_REQUIRED, false, action);
    }

    public void execute(Runnable action) {
        execute(TransactionDefinition.PROPAGATION_REQUIRED, false, () -> {
            action.run();
            return null;
        });
    }

    public <T> T readOnly(Supplier<T> action) {
        return execute(TransactionDefinition.PROPAGATION_REQUIRED, true, action);
    }

    public <T> T execute(int propagationBehavior, boolean readOnly, Supplier<T> action) {
        if (action == null) {
            throw new IllegalArgumentException("action must not be null");
        }
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        template.setPropagationBehavior(propagationBehavior);
        template.setReadOnly(readOnly);
        return template.execute(status -> action.get());
    }
}
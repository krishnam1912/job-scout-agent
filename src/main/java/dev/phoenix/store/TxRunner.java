package dev.phoenix.store;

import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.function.Supplier;

/** Runs a unit of work in one database transaction; a RuntimeException rolls everything back. */
public final class TxRunner {

    private final TransactionTemplate template;

    public TxRunner(PlatformTransactionManager transactionManager) {
        this.template = new TransactionTemplate(transactionManager);
    }

    public <T> T inTx(Supplier<T> work) {
        return template.execute(status -> work.get());
    }

    public void inTx(Runnable work) {
        template.executeWithoutResult(status -> work.run());
    }
}

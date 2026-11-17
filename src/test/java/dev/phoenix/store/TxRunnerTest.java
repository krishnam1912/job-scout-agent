package dev.phoenix.store;

import dev.phoenix.domain.JobState;
import dev.phoenix.testsupport.TestStore;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TxRunnerTest {

    private TestStore store;

    @BeforeEach
    void setUp(@TempDir Path dir) {
        store = TestStore.create(dir);
    }

    @AfterEach
    void tearDown() {
        store.close();
    }

    @Test
    void everyWriteInsideASuccessfulTransactionIsKept() {
        store.tx.inTx(() -> {
            store.insertJob("a", "acme", "1", "T1", JobState.CANDIDATE);
            store.insertJob("a", "acme", "2", "T2", JobState.CANDIDATE);
        });

        assertThat(store.jobs.findAll()).hasSize(2);
    }

    @Test
    void aFailureRollsBackEveryWriteOfTheTransaction() {
        assertThatThrownBy(() -> store.tx.inTx(() -> {
            store.insertJob("a", "acme", "1", "T1", JobState.CANDIDATE);
            store.insertJob("a", "acme", "2", "T2", JobState.CANDIDATE);
            throw new IllegalStateException("boom");
        })).isInstanceOf(IllegalStateException.class).hasMessage("boom");

        assertThat(store.jobs.findAll()).isEmpty();
    }

    @Test
    void theSupplierFormReturnsTheValue() {
        int value = store.tx.inTx(() -> 7);

        assertThat(value).isEqualTo(7);
    }
}

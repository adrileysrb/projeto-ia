package br.com.projetoia;

import br.com.projetoia.domain.TransactionType;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

@QuarkusTest
class SeedDataTest {

    @Inject
    EntityManager em;

    @Test
    @Transactional
    void loadsThreeUsers() {
        Long users = em.createQuery("select count(u) from User u", Long.class).getSingleResult();
        assertEquals(3L, users);
    }

    @Test
    @Transactional
    void loadsFifteenTransactionsForEachOfTheThreeTypes() {
        List<Object[]> perType = em
                .createQuery("select t.type, count(t) from Transaction t group by t.type", Object[].class)
                .getResultList();

        assertEquals(TransactionType.values().length, perType.size());
        for (Object[] row : perType) {
            assertEquals(15L, row[1], "transacoes do tipo " + row[0]);
        }
    }

    @Test
    @Transactional
    void everyTransactionHasADifferentAmount() {
        Long total = em.createQuery("select count(t) from Transaction t", Long.class).getSingleResult();
        Long distinctAmounts = em
                .createQuery("select count(distinct t.amount) from Transaction t", Long.class)
                .getSingleResult();

        assertEquals(45L, total);
        assertEquals(total, distinctAmounts);
    }

    @Test
    @Transactional
    void everyUserHasFiveTransactionsOfEachType() {
        List<Object[]> perUserAndType = em
                .createQuery("select t.user.id, t.type, count(t) from Transaction t group by t.user.id, t.type",
                        Object[].class)
                .getResultList();

        assertEquals(9, perUserAndType.size());
        for (Object[] row : perUserAndType) {
            assertEquals(5L, row[2], "usuario " + row[0] + " tipo " + row[1]);
        }
    }
}

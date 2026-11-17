package br.com.projetoia.chart;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import jakarta.transaction.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Busca as transacoes no banco e monta o que foi pedido: grafico, numero ou tabela.
 *
 * <p>As transacoes sao abertas aqui porque as tools rodam numa thread do cliente HTTP do
 * modelo, fora de qualquer requisicao web.
 */
@ApplicationScoped
public class ChartDataService {

    @Inject
    EntityManager em;

    @Transactional
    public ChartSpec buildChart(ChartRequest request) {
        return ChartBuilder.build(request, findRows(request.filter()), findUserNames());
    }

    @Transactional
    public StatSpec buildStat(StatRequest request) {
        TransactionFilter filter = request.filter();
        StatBuilder.PreviousPeriod previous = filter.previousPeriod()
                .map(before -> new StatBuilder.PreviousPeriod(before.periodLabel().orElseThrow(), findRows(before)))
                .orElse(null);
        return StatBuilder.build(request, findRows(filter), previous);
    }

    @Transactional
    public TableSpec buildTable(TableRequest request) {
        return TableBuilder.build(request, findRows(request.filter()));
    }

    private List<TransactionRow> findRows(TransactionFilter filter) {
        // O Hibernate monta o TransactionRow a partir da classe passada ao createQuery. Um
        // "select new pacote.Classe(...)" procuraria a classe pelo nome no class loader da
        // thread, que nao e o da aplicacao quando a tool roda na thread do cliente HTTP.
        StringBuilder jpql = new StringBuilder("""
                select t.type, u.name, t.category, t.status, t.amount, t.createdAt
                from Transaction t join t.user u
                where 1 = 1
                """);
        Map<String, Object> parameters = new HashMap<>();
        if (filter.type() != null) {
            jpql.append(" and t.type = :type");
            parameters.put("type", filter.type());
        }
        if (filter.userName() != null) {
            jpql.append(" and lower(u.name) like :userName");
            parameters.put("userName", "%" + filter.userName().toLowerCase(Locale.ROOT) + "%");
        }
        if (filter.category() != null) {
            jpql.append(" and t.category = :category");
            parameters.put("category", filter.category());
        }
        if (filter.status() != null) {
            jpql.append(" and t.status = :status");
            parameters.put("status", filter.status());
        }
        if (filter.startDate() != null) {
            jpql.append(" and t.createdAt >= :start");
            parameters.put("start", filter.startDate().atStartOfDay());
        }
        if (filter.endDate() != null) {
            // A data final e inclusiva: vale ate o ultimo instante do dia.
            jpql.append(" and t.createdAt < :endExclusive");
            parameters.put("endExclusive", filter.endDate().plusDays(1).atStartOfDay());
        }
        jpql.append(" order by t.createdAt");

        TypedQuery<TransactionRow> query = em.createQuery(jpql.toString(), TransactionRow.class);
        parameters.forEach(query::setParameter);
        return query.getResultList();
    }

    private List<String> findUserNames() {
        return em.createQuery("select u.name from User u order by u.name", String.class).getResultList();
    }
}

package br.com.projetoia.chart;

import br.com.projetoia.domain.TransactionCategory;
import br.com.projetoia.domain.TransactionStatus;
import br.com.projetoia.domain.TransactionType;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Projecao de uma transacao com so o que os graficos, numeros e tabelas precisam. */
public record TransactionRow(
        TransactionType type,
        String userName,
        TransactionCategory category,
        TransactionStatus status,
        BigDecimal amount,
        LocalDateTime createdAt) {
}

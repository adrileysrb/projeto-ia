package br.com.projetoia.domain;

/** Categoria de gasto da transacao. */
public enum TransactionCategory {
    ALIMENTACAO("Alimentação"),
    TRANSPORTE("Transporte"),
    MORADIA("Moradia"),
    LAZER("Lazer"),
    SAUDE("Saúde"),
    EDUCACAO("Educação");

    private final String label;

    TransactionCategory(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}

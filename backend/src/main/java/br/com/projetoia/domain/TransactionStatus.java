package br.com.projetoia.domain;

/** Situacao da transacao. Uma estornada chegou a ser aprovada; uma recusada, nao. */
public enum TransactionStatus {
    CONCLUIDA("Concluída"),
    RECUSADA("Recusada"),
    ESTORNADA("Estornada");

    private final String label;

    TransactionStatus(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}

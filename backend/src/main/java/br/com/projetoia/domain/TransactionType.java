package br.com.projetoia.domain;

public enum TransactionType {
    PIX("Pix"),
    BOLETO("Boleto"),
    CARTAO("Cartão");

    private final String label;

    TransactionType(String label) {
        this.label = label;
    }

    /** Nome de exibicao, usado nos rotulos dos graficos. */
    public String label() {
        return label;
    }
}

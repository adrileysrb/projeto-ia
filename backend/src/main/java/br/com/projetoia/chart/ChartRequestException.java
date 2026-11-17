package br.com.projetoia.chart;

/**
 * Pedido de grafico que nao da para atender: combinacao invalida de parametros ou filtro
 * sem dados. A mensagem e escrita para quem fez o pedido (o modelo) poder corrigi-lo,
 * ao contrario de erros internos, que nao devem sair do servidor.
 */
public class ChartRequestException extends RuntimeException {

    public ChartRequestException(String message) {
        super(message);
    }
}

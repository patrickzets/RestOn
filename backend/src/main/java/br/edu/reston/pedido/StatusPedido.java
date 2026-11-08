package br.edu.reston.pedido;

public enum StatusPedido {
    RECEBIDO,
    EM_PREPARO,
    PRONTO,
    ENTREGUE,
    CANCELADO;

    // O único passo que o pedido pode dar a partir deste status (null = não anda mais)
    public StatusPedido proximo() {
        return switch (this) {
            case RECEBIDO -> EM_PREPARO;
            case EM_PREPARO -> PRONTO;
            case PRONTO -> ENTREGUE;
            default -> null;
        };
    }
}

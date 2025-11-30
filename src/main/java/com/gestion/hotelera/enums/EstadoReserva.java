package com.gestion.hotelera.enums;

/**
 * Estados posibles de una reserva en el sistema.
 */
public enum EstadoReserva {
    PENDIENTE("PENDIENTE"),
    ACTIVA("ACTIVA"),
    FINALIZADA("FINALIZADA"),
    CANCELADA("CANCELADA");

    private final String valor;

    EstadoReserva(String valor) {
        this.valor = valor;
    }

    public String getValor() {
        return valor;
    }

    public static EstadoReserva fromString(String texto) {
        if (texto == null || texto.trim().isEmpty()) {
            return null;
        }
        for (EstadoReserva estado : EstadoReserva.values()) {
            if (estado.valor.equalsIgnoreCase(texto.trim())) {
                return estado;
            }
        }
        throw new IllegalArgumentException("Estado de reserva no válido: " + texto);
    }

    public boolean esActiva() {
        return this == ACTIVA || this == PENDIENTE;
    }

    public boolean esFinalizada() {
        return this == FINALIZADA;
    }

    public boolean esCancelada() {
        return this == CANCELADA;
    }
}

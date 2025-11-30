package com.gestion.hotelera.enums;

/**
 * Estados posibles de una habitación en el sistema.
 */
public enum EstadoHabitacion {
    DISPONIBLE("DISPONIBLE"),
    OCUPADA("OCUPADA"),
    MANTENIMIENTO("MANTENIMIENTO");

    private final String valor;

    EstadoHabitacion(String valor) {
        this.valor = valor;
    }

    public String getValor() {
        return valor;
    }

    public static EstadoHabitacion fromString(String texto) {
        if (texto == null || texto.trim().isEmpty()) {
            return DISPONIBLE; // Default
        }
        for (EstadoHabitacion estado : EstadoHabitacion.values()) {
            if (estado.valor.equalsIgnoreCase(texto.trim())) {
                return estado;
            }
        }
        throw new IllegalArgumentException("Estado de habitación no válido: " + texto);
    }

    public boolean esDisponible() {
        return this == DISPONIBLE;
    }

    public boolean esOcupada() {
        return this == OCUPADA;
    }

    public boolean esMantenimiento() {
        return this == MANTENIMIENTO;
    }
}

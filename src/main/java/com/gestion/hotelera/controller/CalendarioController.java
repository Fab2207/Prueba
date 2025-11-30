package com.gestion.hotelera.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/calendario")
@PreAuthorize("hasAnyRole('ADMIN', 'RECEPCIONISTA')")
public class CalendarioController {

    private final com.gestion.hotelera.service.HabitacionService habitacionService;
    private final com.gestion.hotelera.service.ReservaService reservaService;

    public CalendarioController(com.gestion.hotelera.service.HabitacionService habitacionService,
            com.gestion.hotelera.service.ReservaService reservaService) {
        this.habitacionService = habitacionService;
        this.reservaService = reservaService;
    }

    @GetMapping
    public String mostrarCalendario(Model model) {
        java.time.LocalDate now = java.time.LocalDate.now();
        java.time.YearMonth yearMonth = java.time.YearMonth.from(now);
        java.time.LocalDate start = yearMonth.atDay(1);
        java.time.LocalDate end = yearMonth.atEndOfMonth();

        // 1. Días del mes
        java.util.List<java.util.Map<String, Object>> diasMes = new java.util.ArrayList<>();
        java.time.format.DateTimeFormatter dayFormatter = java.time.format.DateTimeFormatter.ofPattern("EEE dd",
                java.util.Locale.forLanguageTag("es-ES"));

        for (java.time.LocalDate date = start; !date.isAfter(end); date = date.plusDays(1)) {
            java.util.Map<String, Object> diaInfo = new java.util.HashMap<>();
            diaInfo.put("fecha", date.toString());
            diaInfo.put("nombre", date.format(dayFormatter));
            diaInfo.put("esHoy", date.equals(now));
            diasMes.add(diaInfo);
        }

        // 2. Habitaciones
        java.util.List<com.gestion.hotelera.model.Habitacion> habitaciones = habitacionService
                .obtenerTodasLasHabitaciones();

        // 3. Mapa de disponibilidad: Map<HabitacionID, Map<Fecha, Estado>>
        // Estado puede ser: "LIBRE", "OCUPADA", "MANTENIMIENTO", "LIMPIEZA"
        java.util.Map<Long, java.util.Map<String, String>> disponibilidad = new java.util.HashMap<>();

        java.util.List<com.gestion.hotelera.model.Reserva> reservasMes = reservaService.obtenerReservasPorPeriodo(start,
                end);

        for (com.gestion.hotelera.model.Habitacion hab : habitaciones) {
            java.util.Map<String, String> diasEstado = new java.util.HashMap<>();

            // Inicializar como libre
            for (java.time.LocalDate date = start; !date.isAfter(end); date = date.plusDays(1)) {
                diasEstado.put(date.toString(), "LIBRE");
            }

            // Marcar ocupadas por reservas
            for (com.gestion.hotelera.model.Reserva res : reservasMes) {
                if (res.getHabitacion().getId().equals(hab.getId()) &&
                        (res.getEstadoReserva().equals("ACTIVA") || res.getEstadoReserva().equals("PENDIENTE"))) {

                    java.time.LocalDate resStart = res.getFechaInicio();
                    java.time.LocalDate resEnd = res.getFechaFin();

                    for (java.time.LocalDate date = start; !date.isAfter(end); date = date.plusDays(1)) {
                        if (!date.isBefore(resStart) && !date.isAfter(resEnd)) {
                            diasEstado.put(date.toString(), "OCUPADA");
                        }
                    }
                }
            }

            // Override con estado actual de la habitación si es relevante (ej.
            // Mantenimiento)
            // Esto es simplificado, idealmente tendríamos un historial de estados de
            // habitación
            if ("MANTENIMIENTO".equals(hab.getEstado())) {
                // Para el demo, marcamos hoy como mantenimiento si la habitación está en
                // mantenimiento
                diasEstado.put(now.toString(), "MANTENIMIENTO");
            }

            disponibilidad.put(hab.getId(), diasEstado);
        }

        model.addAttribute("diasMes", diasMes);
        model.addAttribute("habitaciones", habitaciones);
        model.addAttribute("disponibilidad", disponibilidad);
        model.addAttribute("mesAnio", yearMonth
                .format(java.time.format.DateTimeFormatter.ofPattern("MMMM yyyy",
                        java.util.Locale.forLanguageTag("es-ES"))));

        return "calendario";
    }
}

package com.gestion.hotelera.controller;

import com.gestion.hotelera.model.Reserva;
import com.gestion.hotelera.service.HabitacionService;
import com.gestion.hotelera.service.ReservaService;
import com.gestion.hotelera.service.AuditoriaService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.data.domain.PageRequest;

import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.stream.Collectors;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Locale;

@Controller
@RequestMapping("/recepcion")
@PreAuthorize("hasAnyRole('ADMIN', 'RECEPCIONISTA')")
public class RecepcionController {

    private final ReservaService reservaService;
    private final HabitacionService habitacionService;
    private final AuditoriaService auditoriaService;

    public RecepcionController(ReservaService reservaService, HabitacionService habitacionService,
            AuditoriaService auditoriaService) {
        this.reservaService = reservaService;
        this.habitacionService = habitacionService;
        this.auditoriaService = auditoriaService;
    }

    @GetMapping
    public String mostrarPanelRecepcion(Model model) {
        // Listas para tablas
        List<Reserva> llegadas = reservaService.obtenerLlegadasHoy();
        List<Reserva> salidas = reservaService.obtenerSalidasHoy();

        // Estadísticas
        long totalLlegadas = llegadas.size();
        long totalSalidas = salidas.size();
        long habitacionesOcupadas = habitacionService.contarOcupadas();
        long habitacionesDisponibles = habitacionService.contarDisponibles();
        long totalHabitaciones = habitacionService.contarHabitaciones();

        int porcentajeOcupacion = totalHabitaciones > 0 ? (int) ((habitacionesOcupadas * 100) / totalHabitaciones) : 0;

        // Datos para la vista (alineados con la plantilla)
        model.addAttribute("proximasLlegadas", llegadas);
        model.addAttribute("salidas", salidas);
        model.addAttribute("habitaciones", habitacionService.obtenerTodasLasHabitaciones());

        model.addAttribute("llegadasHoy", totalLlegadas);
        model.addAttribute("salidasHoy", totalSalidas);
        model.addAttribute("estanciasActuales", reservaService.contarReservasPorEstado("ACTIVA"));
        model.addAttribute("habitacionesOcupadas", habitacionesOcupadas);
        model.addAttribute("habitacionesDisponibles", habitacionesDisponibles);
        model.addAttribute("porcentajeOcupacion", porcentajeOcupacion);

        // Actividad reciente desde Auditoria
        var logs = auditoriaService.obtenerTodosLosLogs(PageRequest.of(0, 5)).getContent();
        var actividadReciente = logs.stream().map(log -> {
            Map<String, Object> map = new HashMap<>();
            String tipo = "INFO";
            if (log.getTipoAccion() != null) {
                if (log.getTipoAccion().contains("RESERVA"))
                    tipo = "RESERVA";
                else if (log.getTipoAccion().contains("CHECKIN"))
                    tipo = "CHECKIN";
                else if (log.getTipoAccion().contains("CANCEL"))
                    tipo = "CANCEL";
            }

            map.put("tipo", tipo);
            map.put("titulo", log.getTipoAccion());
            map.put("descripcion", log.getDetalleAccion());

            long minutes = Duration.between(log.getTimestamp(), LocalDateTime.now()).toMinutes();
            String tiempo;
            if (minutes < 60)
                tiempo = "Hace " + minutes + " minutos";
            else if (minutes < 1440)
                tiempo = "Hace " + (minutes / 60) + " horas";
            else
                tiempo = "Hace " + (minutes / 1440) + " días";

            map.put("tiempo", tiempo);
            return map;
        }).collect(Collectors.toList());

        model.addAttribute("actividadReciente", actividadReciente);

        // Calculo para el gráfico SVG (Circunferencia aprox 339.3, 75% es 254.4)
        double circumference = 339.3;
        double dashOffset = circumference * (1 - (double) porcentajeOcupacion / 100);
        model.addAttribute("dashOffset", String.format(Locale.US, "%.1f", dashOffset));

        return "recepcion";
    }

    @GetMapping("/express")
    public String mostrarRecepcionExpress(Model model) {
        return "recepcion-express";
    }
}

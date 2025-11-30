package com.gestion.hotelera.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.ui.Model;
import org.springframework.data.domain.PageRequest;
import com.gestion.hotelera.service.AuditoriaService;

/**
 * Controlador para la página de monitoreo del sistema
 * Solo accesible para usuarios con rol ADMIN
 */
@Controller
@RequestMapping("/monitoreo")
public class MonitoreoController {

    private final AuditoriaService auditoriaService;

    public MonitoreoController(AuditoriaService auditoriaService) {
        this.auditoriaService = auditoriaService;
    }

    /**
     * Muestra la página de monitoreo con métricas en tiempo real
     */
    @GetMapping
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public String mostrarDashboardMonitoreo(
            @org.springframework.web.bind.annotation.RequestParam(required = false) String query,
            @org.springframework.web.bind.annotation.RequestParam(required = false) String nivel,
            @org.springframework.web.bind.annotation.RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) java.time.LocalDate fecha,
            Model model) {

        org.springframework.data.domain.Page<com.gestion.hotelera.model.Auditoria> logsPage;

        if ((nivel != null && !nivel.isEmpty() && !nivel.equals("Todos los niveles")) || fecha != null) {
            String tipoAccion = null;
            if (nivel != null) {
                switch (nivel) {
                    case "Info":
                        tipoAccion = "CREACION";
                        break; // Simplification
                    case "Advertencia":
                        tipoAccion = "ACTUALIZACION";
                        break;
                    case "Error":
                        tipoAccion = "ERROR";
                        break;
                    case "Crítico":
                        tipoAccion = "ELIMINACION";
                        break;
                    default:
                        tipoAccion = null;
                }
            }
            logsPage = auditoriaService.filtrarLogs(tipoAccion, fecha, fecha, PageRequest.of(0, 50));
        } else if (query != null && !query.isEmpty()) {
            logsPage = auditoriaService.searchLogs(query, PageRequest.of(0, 50));
            model.addAttribute("query", query);
        } else {
            logsPage = auditoriaService.obtenerTodosLosLogs(PageRequest.of(0, 50));
        }

        model.addAttribute("logs", logsPage.getContent());
        model.addAttribute("nivel", nivel);
        model.addAttribute("fecha", fecha);

        return "monitoreo";
    }
}

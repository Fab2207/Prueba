package com.gestion.hotelera.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/notificaciones")
@PreAuthorize("hasAnyRole('ADMIN', 'RECEPCIONISTA')")
public class NotificacionController {

    private final com.gestion.hotelera.service.NotificacionService notificacionService;

    public NotificacionController(com.gestion.hotelera.service.NotificacionService notificacionService) {
        this.notificacionService = notificacionService;
    }

    @GetMapping
    public String mostrarPanelNotificaciones(
            @org.springframework.web.bind.annotation.RequestParam(required = false) String filtro, Model model) {
        if ("no-leidas".equals(filtro)) {
            model.addAttribute("notificaciones", notificacionService.obtenerNoLeidas());
            model.addAttribute("filtroActual", "no-leidas");
        } else if ("archivadas".equals(filtro)) {
            model.addAttribute("notificaciones", notificacionService.obtenerArchivadas());
            model.addAttribute("filtroActual", "archivadas");
        } else {
            model.addAttribute("notificaciones", notificacionService.obtenerTodas());
            model.addAttribute("filtroActual", "todas");
        }
        return "notificaciones";
    }

    @GetMapping("/ver/{id}")
    public String verNotificacion(@org.springframework.web.bind.annotation.PathVariable Long id, Model model) {
        notificacionService.marcarComoLeida(id);
        return "redirect:/notificaciones";
    }

    @GetMapping("/archivar/{id}")
    public String archivarNotificacion(@org.springframework.web.bind.annotation.PathVariable Long id) {
        notificacionService.archivar(id);
        return "redirect:/notificaciones";
    }

    @GetMapping("/marcar-todas-leidas")
    public String marcarTodasLeidas() {
        notificacionService.marcarTodasComoLeidas();
        return "redirect:/notificaciones";
    }
}

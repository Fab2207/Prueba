package com.gestion.hotelera.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/configuracion")
@PreAuthorize("hasRole('ADMIN')")
public class ConfiguracionController {

    private final com.gestion.hotelera.service.ConfiguracionService configuracionService;

    public ConfiguracionController(com.gestion.hotelera.service.ConfiguracionService configuracionService) {
        this.configuracionService = configuracionService;
    }

    @GetMapping
    public String mostrarConfiguracion(Model model) {
        if (!model.containsAttribute("configuracion")) {
            model.addAttribute("configuracion", configuracionService.obtenerConfiguracion());
        }
        return "configuracion";
    }

    @PostMapping("/guardar")
    public String guardarConfiguracion(
            @org.springframework.web.bind.annotation.ModelAttribute("configuracion") com.gestion.hotelera.dto.ConfiguracionDTO configuracion,
            RedirectAttributes redirectAttributes) {
        try {
            configuracionService.guardarConfiguracion(configuracion);
            redirectAttributes.addFlashAttribute("successMessage", "Configuración guardada exitosamente.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage",
                    "Error al guardar la configuración: " + e.getMessage());
        }
        return "redirect:/configuracion";
    }
}

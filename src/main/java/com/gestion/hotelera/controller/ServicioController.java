package com.gestion.hotelera.controller;

import com.gestion.hotelera.model.Servicio;
import com.gestion.hotelera.service.ServicioService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/servicios")
@PreAuthorize("hasAnyRole('ADMIN', 'RECEPCIONISTA')")
public class ServicioController {

    private final ServicioService servicioService;

    public ServicioController(ServicioService servicioService) {
        this.servicioService = servicioService;
    }

    @GetMapping
    public String listarServicios(Model model) {
        model.addAttribute("servicios", servicioService.obtenerTodosLosServicios());
        return "servicios";
    }

    @GetMapping("/nuevo")
    public String mostrarFormularioNuevo(Model model) {
        model.addAttribute("servicio", new Servicio());
        return "registrarServicio";
    }

    @GetMapping("/editar/{id}")
    public String mostrarFormularioEditar(@PathVariable Long id, Model model, RedirectAttributes redirectAttributes) {
        return servicioService.obtenerServicioPorId(id)
                .map(servicio -> {
                    model.addAttribute("servicio", servicio);
                    return "editarServicio";
                })
                .orElseGet(() -> {
                    redirectAttributes.addFlashAttribute("errorMessage", "Servicio no encontrado");
                    return "redirect:/servicios";
                });
    }

    @PostMapping("/guardar")
    public String guardarServicio(@ModelAttribute Servicio servicio, RedirectAttributes redirectAttributes) {
        try {
            if (servicio.getId() == null) {
                servicioService.crearServicio(servicio);
                redirectAttributes.addFlashAttribute("successMessage", "Servicio creado exitosamente");
            } else {
                servicioService.actualizarServicio(servicio);
                redirectAttributes.addFlashAttribute("successMessage", "Servicio actualizado exitosamente");
            }
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Error al guardar servicio: " + e.getMessage());
        }
        return "redirect:/servicios";
    }

    @PostMapping("/{id}/eliminar")
    public String eliminarServicio(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            servicioService.eliminarServicio(id);
            redirectAttributes.addFlashAttribute("successMessage", "Servicio eliminado exitosamente");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Error al eliminar servicio: " + e.getMessage());
        }
        return "redirect:/servicios";
    }
}

package com.gestion.hotelera.controller;

import com.gestion.hotelera.model.Resena;
import com.gestion.hotelera.model.Reserva;
import com.gestion.hotelera.service.ResenaService;
import com.gestion.hotelera.service.ReservaService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.Optional;

@Controller
@RequestMapping("/resenas")
public class ResenaController {

    private final ResenaService resenaService;
    private final ReservaService reservaService;

    public ResenaController(ResenaService resenaService, ReservaService reservaService) {
        this.resenaService = resenaService;
        this.reservaService = reservaService;
    }

    @GetMapping("/crear/{reservaId}")
    @PreAuthorize("hasRole('CLIENTE')")
    public String mostrarFormularioResena(@PathVariable Long reservaId, Model model, Authentication auth) {
        Optional<Reserva> reservaOpt = reservaService.obtenerReservaPorId(reservaId);

        if (reservaOpt.isEmpty()) {
            return "redirect:/cliente/historial";
        }

        Reserva reserva = reservaOpt.get();
        // Verificar que la reserva pertenezca al usuario autenticado
        if (reserva.getCliente().getUsuario() == null
                || !reserva.getCliente().getUsuario().getUsername().equals(auth.getName())) {
            return "redirect:/cliente/historial";
        }

        // Verificar que la reserva esté finalizada
        if (!"FINALIZADA".equals(reserva.getEstadoReserva())) {
            return "redirect:/cliente/historial";
        }

        if (resenaService.existeResenaParaReserva(reservaId)) {
            // Ya existe reseña
            return "redirect:/cliente/historial";
        }

        Resena resena = new Resena();
        resena.setReserva(reserva);
        resena.setCliente(reserva.getCliente());

        model.addAttribute("resena", resena);
        model.addAttribute("reserva", reserva);

        return "resena-form";
    }

    @PostMapping("/guardar")
    @PreAuthorize("hasRole('CLIENTE')")
    public String guardarResena(@ModelAttribute Resena resena, RedirectAttributes redirectAttributes) {
        try {
            resenaService.guardarResena(resena);
            redirectAttributes.addFlashAttribute("successMessage", "¡Gracias por tu reseña!");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Error al guardar la reseña.");
        }
        return "redirect:/cliente/historial";
    }
}

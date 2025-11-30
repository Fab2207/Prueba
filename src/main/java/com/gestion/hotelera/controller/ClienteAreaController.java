package com.gestion.hotelera.controller;

import com.gestion.hotelera.model.Cliente;
import com.gestion.hotelera.model.Reserva;
import com.gestion.hotelera.service.ClienteService;
import com.gestion.hotelera.service.ReservaService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

/**
 * Controlador para el área personal del cliente
 * Solo los clientes pueden acceder y editar su propia información
 */
@Controller
@RequestMapping("/cliente")
@PreAuthorize("hasAnyAuthority('ROLE_CLIENTE', 'CLIENTE')")
public class ClienteAreaController {

    private final ClienteService clienteService;
    private final ReservaService reservaService;

    public ClienteAreaController(ClienteService clienteService, ReservaService reservaService) {
        this.clienteService = clienteService;
        this.reservaService = reservaService;
    }

    /**
     * Área personal del cliente - Dashboard
     */
    @GetMapping("/area")
    public String mostrarAreaCliente(Model model, Authentication auth) {
        try {
            String username = auth.getName();
            Cliente cliente = clienteService.obtenerPorUsername(username);

            if (cliente == null) {
                model.addAttribute("errorMessage", "No se encontró el perfil de cliente.");
                return "error"; // Vista de error genérica
            }

            // Obtener reservas del cliente
            List<Reserva> todasReservas = reservaService.obtenerReservasPorClienteId(cliente.getId());
            long reservasActivas = todasReservas.stream()
                    .filter(r -> "ACTIVA".equals(r.getEstadoReserva()) || "PENDIENTE".equals(r.getEstadoReserva()))
                    .count();
            long reservasFinalizadas = todasReservas.stream()
                    .filter(r -> "FINALIZADA".equals(r.getEstadoReserva()))
                    .count();

            model.addAttribute("cliente", cliente);
            model.addAttribute("reservas", todasReservas);
            model.addAttribute("totalReservas", todasReservas.size());
            model.addAttribute("reservasActivas", reservasActivas);
            model.addAttribute("reservasFinalizadas", reservasFinalizadas);

            return "cliente-area";
        } catch (Exception e) {
            model.addAttribute("errorMessage", "Error al cargar información del cliente: " + e.getMessage());
            return "error";
        }
    }

    /**
     * Formulario de edición - Solo el propio cliente puede editar sus datos
     */
    @GetMapping("/editar")
    public String mostrarFormularioEdicion(Model model, Authentication auth) {
        try {
            String username = auth.getName();
            Cliente cliente = clienteService.obtenerPorUsername(username);

            if (cliente == null) {
                model.addAttribute("errorMessage", "Cliente no encontrado");
                return "error";
            }

            model.addAttribute("cliente", cliente);
            return "cliente-editar";
        } catch (Exception e) {
            model.addAttribute("errorMessage", "Error al cargar datos: " + e.getMessage());
            return "cliente-area";
        }
    }

    /**
     * Guardar cambios - Solo el propio cliente puede actualizar sus datos
     */
    @PostMapping("/actualizar")
    public String actualizarDatos(@ModelAttribute Cliente clienteActualizado,
            Authentication auth,
            RedirectAttributes redirectAttributes) {
        try {
            String username = auth.getName();
            Cliente clienteActual = clienteService.obtenerPorUsername(username);

            if (clienteActual == null) {
                redirectAttributes.addFlashAttribute("errorMessage", "Cliente no encontrado");
                return "redirect:/cliente/area";
            }

            // Verificar que el cliente solo edite sus propios datos
            if (!clienteActual.getId().equals(clienteActualizado.getId())) {
                redirectAttributes.addFlashAttribute("errorMessage", "No tiene permiso para editar estos datos");
                return "redirect:/cliente/area";
            }

            // Actualizar solo campos permitidos
            clienteActual.setNombres(clienteActualizado.getNombres());
            clienteActual.setApellidos(clienteActualizado.getApellidos());
            clienteActual.setTelefono(clienteActualizado.getTelefono());
            clienteActual.setEmail(clienteActualizado.getEmail());
            // NO permitir cambio de DNI ni username por seguridad

            clienteService.actualizarCliente(clienteActual);

            redirectAttributes.addFlashAttribute("successMessage", "Datos actualizados correctamente");
            return "redirect:/cliente/area";
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Error al actualizar datos: " + e.getMessage());
            return "redirect:/cliente/editar";
        }
    }

    /**
     * Historial de reservas del cliente
     */
    @GetMapping("/historial")
    public String mostrarHistorial(Model model, Authentication auth) {
        try {
            String username = auth.getName();
            Cliente cliente = clienteService.obtenerPorUsername(username);

            if (cliente == null) {
                model.addAttribute("errorMessage", "Cliente no encontrado");
                return "error";
            }

            List<Reserva> reservas = reservaService.obtenerReservasPorClienteId(cliente.getId());

            model.addAttribute("cliente", cliente);
            model.addAttribute("reservas", reservas);

            return "cliente-historial";
        } catch (Exception e) {
            model.addAttribute("errorMessage", "Error al cargar historial: " + e.getMessage());
            return "cliente-area";
        }
    }
}
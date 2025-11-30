package com.gestion.hotelera.controller;

import com.gestion.hotelera.model.Cliente;
import com.gestion.hotelera.model.Habitacion;
import com.gestion.hotelera.model.Reserva;
import com.gestion.hotelera.service.ClienteService;
import com.gestion.hotelera.service.HabitacionService;
import com.gestion.hotelera.service.ReservaService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;
import java.util.Optional;

@Controller
@RequestMapping("/cliente/reservas")
public class ClienteReservaController {

    private final ClienteService clienteService;
    private final HabitacionService habitacionService;
    private final ReservaService reservaService;

    public ClienteReservaController(ClienteService clienteService,
            HabitacionService habitacionService,
            ReservaService reservaService) {
        this.clienteService = clienteService;
        this.habitacionService = habitacionService;
        this.reservaService = reservaService;
    }

    @GetMapping("/crear")
    public String mostrarFormularioReserva(Model model,
            Authentication auth,
            @RequestParam(name = "habitacionId", required = false) Long habitacionId) {
        if (auth == null || !auth.isAuthenticated()) {
            return "redirect:/login";
        }

        try {
            Cliente cliente = clienteService.obtenerPorUsername(auth.getName());
            if (cliente == null) {
                model.addAttribute("errorMessage", "No se encontró el perfil de cliente asociado a su usuario.");
                return "error"; // O una vista de error genérica
            }

            model.addAttribute("cliente", cliente);
            model.addAttribute("reserva", new Reserva());
            model.addAttribute("habitacionesDisponibles",
                    habitacionService.obtenerHabitacionesDisponiblesParaCliente(cliente.getId()));
            if (habitacionId != null && habitacionId > 0) {
                model.addAttribute("habitacionId", habitacionId);
            }
            return "generarReserva";
        } catch (Exception e) {
            model.addAttribute("errorMessage", "Error al cargar el formulario: " + e.getMessage());
            return "generarReserva";
        }
    }

    @PostMapping("/guardar")
    public String guardarReserva(@ModelAttribute Reserva reserva,
            @RequestParam("habitacionId") Long habitacionId,
            Authentication auth,
            RedirectAttributes redirectAttributes) {
        try {
            if (habitacionId == null || habitacionId <= 0) {
                redirectAttributes.addFlashAttribute("errorMessage", "Habitación inválida");
                return "redirect:/cliente/reservas/crear";
            }

            Cliente cliente = clienteService.obtenerPorUsername(auth.getName());
            if (cliente == null) {
                return "redirect:/login";
            }

            Optional<Habitacion> habitacionOpt = habitacionService.buscarHabitacionPorId(habitacionId);
            if (habitacionOpt.isEmpty()) {
                redirectAttributes.addFlashAttribute("errorMessage", "Habitación no encontrada");
                return "redirect:/cliente/reservas/crear";
            }

            reserva.setCliente(cliente);
            reserva.setHabitacion(habitacionOpt.get());

            if (reserva.getFechaInicio().isBefore(LocalDate.now())) {
                redirectAttributes.addFlashAttribute("errorMessage", "La fecha de inicio no puede ser anterior a hoy.");
                return "redirect:/cliente/reservas/crear";
            }

            reserva.setEstadoReserva("PENDIENTE");

            Reserva reservaGuardada = reservaService.crearOActualizarReserva(reserva);

            redirectAttributes.addFlashAttribute("successMessage",
                    "Reserva creada exitosamente. Ahora puedes añadir servicios opcionales.");
            return "redirect:/reservas/" + reservaGuardada.getId() + "/servicios?returnTo=dashboard";

        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Error al crear la reserva: " + e.getMessage());
            return "redirect:/cliente/reservas/crear";
        }
    }

    @PostMapping("/solicitar")
    public String solicitarReserva(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaEntrada,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaSalida,
            @RequestParam String tipo,
            Authentication auth,
            RedirectAttributes redirectAttributes) {
        try {
            Cliente cliente = clienteService.obtenerPorUsername(auth.getName());
            if (cliente == null) {
                return "redirect:/login";
            }

            if (fechaEntrada.isBefore(LocalDate.now())) {
                redirectAttributes.addFlashAttribute("errorMessage",
                        "La fecha de entrada no puede ser anterior a hoy.");
                return "redirect:/cliente/reservas/crear";
            }

            if (fechaSalida.isBefore(fechaEntrada.plusDays(1))) {
                redirectAttributes.addFlashAttribute("errorMessage",
                        "La fecha de salida debe ser posterior a la entrada.");
                return "redirect:/cliente/reservas/crear";
            }

            // Buscar habitación disponible del tipo seleccionado
            // Nota: Esto asume que el servicio devuelve habitaciones disponibles para las
            // fechas por defecto o lógica actual
            // Idealmente deberíamos pasar las fechas al servicio, pero usaremos la lógica
            // existente
            var disponibles = habitacionService.obtenerHabitacionesDisponiblesParaCliente(cliente.getId());

            var habitacionOpt = disponibles.stream()
                    .filter(h -> h.getTipo().equalsIgnoreCase(tipo))
                    .findFirst();

            if (habitacionOpt.isEmpty()) {
                redirectAttributes.addFlashAttribute("errorMessage",
                        "No hay habitaciones disponibles del tipo " + tipo + " para estas fechas.");
                return "redirect:/cliente/reservas/crear";
            }

            Reserva reserva = new Reserva();
            reserva.setCliente(cliente);
            reserva.setHabitacion(habitacionOpt.get());
            reserva.setFechaInicio(fechaEntrada);
            reserva.setFechaFin(fechaSalida);
            reserva.setEstadoReserva("PENDIENTE");

            Reserva reservaGuardada = reservaService.crearOActualizarReserva(reserva);

            redirectAttributes.addFlashAttribute("successMessage",
                    "Habitación asignada exitosamente. Personaliza tu estancia.");
            return "redirect:/reservas/" + reservaGuardada.getId() + "/servicios?returnTo=dashboard";

        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Error al procesar la solicitud: " + e.getMessage());
            return "redirect:/cliente/reservas/crear";
        }
    }

    @GetMapping("/calcular-costo")
    @ResponseBody
    public String calcularCosto(
            @RequestParam("habitacionId") Long habitacionId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaInicio,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaFin) {

        Optional<Habitacion> habitacionOptional = habitacionService.buscarHabitacionPorId(habitacionId);
        if (habitacionOptional.isEmpty()) {
            return "{\"error\": \"Habitación no encontrada\"}";
        }

        Habitacion habitacion = habitacionOptional.get();
        Integer dias = reservaService.calcularDiasEstadia(fechaInicio, fechaFin);
        Double total = reservaService.calcularTotalPagar(habitacion.getPrecioPorNoche(), dias);
        return String.format("{\"dias\": %d, \"total\": %.2f}", dias, total);
    }
}
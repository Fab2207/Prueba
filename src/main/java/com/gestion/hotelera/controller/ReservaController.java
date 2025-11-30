package com.gestion.hotelera.controller;

import com.gestion.hotelera.model.Cliente;
import com.gestion.hotelera.model.Habitacion;
import com.gestion.hotelera.model.Reserva;
import com.gestion.hotelera.service.ClienteService;
import com.gestion.hotelera.service.HabitacionService;
import com.gestion.hotelera.service.ReservaService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Controller
@RequestMapping("/reservas")
public class ReservaController {

    private final ClienteService clienteService;
    private final HabitacionService habitacionService;
    private final ReservaService reservaService;

    public ReservaController(ClienteService clienteService, HabitacionService habitacionService,
            ReservaService reservaService) {
        this.clienteService = clienteService;
        this.habitacionService = habitacionService;
        this.reservaService = reservaService;
    }

    @GetMapping
    public String listarReservas(Model model, Authentication auth,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) List<String> estados) {
        if (auth == null || !auth.isAuthenticated()) {
            return "redirect:/login";
        }

        List<Reserva> reservas;
        boolean isAdminOrRecep = auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN") || a.getAuthority().equals("ROLE_RECEPCIONISTA"));

        if (isAdminOrRecep) {
            reservas = reservaService.obtenerTodasLasReservas();
        } else {
            Cliente cliente = clienteService.obtenerPorEmail(auth.getName());
            if (cliente != null) {
                reservas = reservaService.obtenerReservasPorCliente(cliente);
                model.addAttribute("clienteEncontrado", cliente);
            } else {
                reservas = new java.util.ArrayList<>();
            }
        }

        // Filtrado básico en memoria (idealmente debería ser en BD para grandes
        // volúmenes)
        if (search != null && !search.isEmpty()) {
            String term = search.toLowerCase();
            reservas = reservas.stream()
                    .filter(r -> (r.getCliente() != null &&
                            (r.getCliente().getNombres().toLowerCase().contains(term) ||
                                    r.getCliente().getApellidos().toLowerCase().contains(term)))
                            ||
                            String.valueOf(r.getId()).contains(term))
                    .collect(java.util.stream.Collectors.toList());
        }

        if (estados != null && !estados.isEmpty()) {
            reservas = reservas.stream()
                    .filter(r -> estados.contains(r.getEstadoReserva()))
                    .collect(java.util.stream.Collectors.toList());
        }

        // Ordenar por ID descendente
        reservas.sort((r1, r2) -> r2.getId().compareTo(r1.getId()));

        model.addAttribute("reservas", reservas);
        model.addAttribute("isAdminOrRecep", isAdminOrRecep);

        // Mantener filtros en la vista
        model.addAttribute("search", search);
        model.addAttribute("estadosSeleccionados", estados);

        return "reservas";
    }

    @GetMapping("/crear")
    public String showCrearReservaForm(Model model,
            @RequestParam(name = "dni", required = false) String dni,
            @RequestParam(name = "idCliente", required = false) Long idCliente) {
        model.addAttribute("cliente", new Cliente());
        model.addAttribute("reserva", new Reserva());

        Long clienteIdParaHabitaciones = null;

        try {
            if (idCliente != null) {
                Optional<Cliente> clientePorId = clienteService.obtenerClientePorId(idCliente);
                if (clientePorId.isPresent()) {
                    clienteIdParaHabitaciones = clientePorId.get().getId();
                }
            } else if (dni != null && !dni.trim().isEmpty()) {
                String dniLimpio = dni.trim();
                // Validar que el DNI tenga exactamente 8 dígitos numéricos
                if (dniLimpio.matches("^\\d{8}$")) {
                    Optional<Cliente> clienteOptional = clienteService.buscarClientePorDni(dniLimpio);
                    if (clienteOptional.isPresent()) {
                        clienteIdParaHabitaciones = clienteOptional.get().getId();
                    }
                }
            }

            model.addAttribute("habitacionesDisponibles",
                    clienteIdParaHabitaciones != null
                            ? habitacionService.obtenerHabitacionesDisponiblesParaCliente(clienteIdParaHabitaciones)
                            : habitacionService.obtenerHabitacionesDisponibles());
        } catch (Exception e) {
            model.addAttribute("errorMessage", "Error al cargar habitaciones disponibles");
            return "reservas";
        }

        if (idCliente != null) {
            try {
                Optional<Cliente> clientePorId = clienteService.obtenerClientePorId(idCliente);
                if (clientePorId.isPresent()) {
                    model.addAttribute("clienteEncontrado", clientePorId.get());
                    model.addAttribute("cliente", clientePorId.get());
                } else {
                    model.addAttribute("errorMessage", "Cliente no encontrado");
                }
            } catch (Exception e) {
                model.addAttribute("errorMessage", "Error al buscar cliente");
            }
        } else if (dni != null && !dni.trim().isEmpty()) {
            try {
                String dniLimpio = dni.trim();
                if (!dniLimpio.matches("^\\d{8}$")) {
                    model.addAttribute("errorMessage", "El DNI debe contener exactamente 8 dígitos numéricos");
                } else {
                    Optional<Cliente> clienteOptional = clienteService.buscarClientePorDni(dniLimpio);
                    if (clienteOptional.isPresent()) {
                        model.addAttribute("clienteEncontrado", clienteOptional.get());
                        model.addAttribute("cliente", clienteOptional.get());
                    } else {
                        model.addAttribute("errorMessage", "Cliente no encontrado");
                    }
                }
            } catch (Exception e) {
                model.addAttribute("errorMessage", "Error al buscar cliente");
            }
        }
        return "reservas";
    }

    @PostMapping("/buscar-cliente")
    public String buscarClienteParaReserva(@RequestParam("dniBuscar") String dni,
            RedirectAttributes redirectAttributes) {
        // Validar que el DNI tenga exactamente 8 dígitos numéricos
        String dniLimpio = dni != null ? dni.trim() : "";
        if (!dniLimpio.matches("^\\d{8}$")) {
            redirectAttributes.addFlashAttribute("errorMessage",
                    "El DNI debe contener exactamente 8 dígitos numéricos");
            return "redirect:/reservas/crear";
        }

        Optional<Cliente> clienteOptional = clienteService.buscarClientePorDni(dniLimpio);
        if (clienteOptional.isPresent()) {
            redirectAttributes.addFlashAttribute("successMessage", "Cliente encontrado!");
            return "redirect:/reservas/crear?dni=" + dniLimpio;
        } else {
            redirectAttributes.addFlashAttribute("errorMessage",
                    "Cliente con DNI " + dniLimpio + " no encontrado. Por favor, regístrelo primero.");
            return "redirect:/reservas/crear";
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
            return "Error: Habitación no encontrada";
        }

        Habitacion habitacion = habitacionOptional.get();
        Integer dias = reservaService.calcularDiasEstadia(fechaInicio, fechaFin);
        Double total = reservaService.calcularTotalPagar(habitacion.getPrecioPorNoche(), dias);
        return String.format("{\"dias\": %d, \"total\": %.2f}", dias, total);
    }

    @PostMapping("/guardar")
    public String guardarReserva(@ModelAttribute Reserva reserva,
            @RequestParam("clienteDni") String clienteDni,
            @RequestParam("habitacionId") Long habitacionId,
            RedirectAttributes redirectAttributes,
            Authentication auth) {
        try {
            String dniLimpio = clienteDni != null ? clienteDni.trim() : "";
            if (!dniLimpio.matches("^\\d{8}$")) {
                redirectAttributes.addFlashAttribute("errorMessage",
                        "El DNI debe contener exactamente 8 dígitos numéricos");
                return "redirect:/reservas";
            }

            Optional<Cliente> clienteOptional = clienteService.buscarClientePorDni(dniLimpio);
            if (clienteOptional.isEmpty()) {
                redirectAttributes.addFlashAttribute("errorMessage",
                        "Error: Cliente no encontrado para el DNI proporcionado.");
                return "redirect:/reservas";
            }
            reserva.setCliente(clienteOptional.get());
            Optional<Habitacion> habitacionOpt = habitacionService.buscarHabitacionPorId(habitacionId);
            if (habitacionOpt.isEmpty()) {
                redirectAttributes.addFlashAttribute("errorMessage", "Habitación no encontrada.");
                return "redirect:/reservas";
            }
            reserva.setHabitacion(habitacionOpt.get());
            if (reserva.getFechaInicio().isBefore(LocalDate.now())) {
                redirectAttributes.addFlashAttribute("errorMessage",
                        "La fecha de inicio de la reserva no puede ser anterior a la fecha actual.");
                return "redirect:/reservas";
            }
            reserva.setEstadoReserva("PENDIENTE");
            Reserva reservaGuardada = reservaService.crearOActualizarReserva(reserva);
            redirectAttributes.addFlashAttribute("successMessage",
                    "Reserva creada exitosamente. Puedes añadir servicios adicionales antes del pago.");

            String returnTo = (auth != null && auth.isAuthenticated() &&
                    auth.getAuthorities().stream()
                            .anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority())
                                    || "ROLE_RECEPCIONISTA".equals(a.getAuthority())))
                                            ? "historial"
                                            : null;

            String redirectUrl = "/reservas/" + reservaGuardada.getId() + "/servicios";
            if (returnTo != null) {
                redirectUrl += "?returnTo=" + returnTo;
            }
            return "redirect:" + redirectUrl;
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Error al crear la reserva: " + e.getMessage());
            return "redirect:/reservas";
        }
    }

    @PostMapping("/cancelar/{id}")
    public String cancelarReserva(@PathVariable Long id, RedirectAttributes redirectAttributes,
            @RequestHeader(value = "Referer", required = false) String referer,
            Authentication auth) {
        try {
            String userRole = auth.getAuthorities().iterator().next().getAuthority();
            if (reservaService.cancelarReserva(id, userRole)) {
                redirectAttributes.addFlashAttribute("successMessage", "Reserva cancelada exitosamente.");
            } else {
                redirectAttributes.addFlashAttribute("errorMessage", "No se pudo cancelar la reserva.");
            }
        } catch (IllegalStateException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:" + (referer != null ? referer : "/dashboard");
    }

    @PostMapping("/cancelar-cliente/{id}")
    public String cancelarReservaCliente(@PathVariable Long id, RedirectAttributes redirectAttributes,
            Authentication auth) {
        redirectAttributes.addFlashAttribute("errorMessage",
                "Usted no tiene permisos para cancelar su reserva. Por favor, comuníquese con recepción para realizar esta acción.");
        return "redirect:/dashboard";
    }

    @PostMapping("/finalizar/{id}")
    public String finalizarReserva(@PathVariable Long id, RedirectAttributes redirectAttributes,
            @RequestHeader(value = "Referer", required = false) String referer) {
        if (id == null || id <= 0) {
            redirectAttributes.addFlashAttribute("errorMessage", "ID de reserva inválido.");
            return "redirect:" + (referer != null ? referer : "/dashboard");
        }

        try {
            var reservaOpt = reservaService.obtenerReservaPorId(id);
            if (reservaOpt.isEmpty()) {
                redirectAttributes.addFlashAttribute("errorMessage", "Reserva no encontrada.");
                return "redirect:" + (referer != null ? referer : "/dashboard");
            }

            var reserva = reservaOpt.get();
            if ("FINALIZADA".equals(reserva.getEstadoReserva())) {
                redirectAttributes.addFlashAttribute("errorMessage", "La reserva ya está finalizada.");
                return "redirect:" + (referer != null ? referer : "/dashboard");
            }

            reservaService.finalizarReserva(id);
            redirectAttributes.addFlashAttribute("successMessage", "Reserva finalizada exitosamente.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Error al finalizar reserva: " + e.getMessage());
        }
        return "redirect:" + (referer != null ? referer : "/dashboard");
    }

    @PostMapping("/checkin/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'RECEPCIONISTA')")
    public String realizarCheckIn(@PathVariable Long id, RedirectAttributes redirectAttributes,
            @RequestHeader(value = "Referer", required = false) String referer) {
        try {
            reservaService.realizarCheckIn(id);
            redirectAttributes.addFlashAttribute("successMessage", "Check-in realizado exitosamente.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Error al realizar check-in: " + e.getMessage());
        }
        return "redirect:" + (referer != null ? referer : "/dashboard");
    }

    @PostMapping("/checkout/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'RECEPCIONISTA')")
    public String realizarCheckOut(@PathVariable Long id, RedirectAttributes redirectAttributes,
            @RequestHeader(value = "Referer", required = false) String referer) {
        try {
            reservaService.realizarCheckOut(id);
            redirectAttributes.addFlashAttribute("successMessage", "Check-out realizado exitosamente.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Error al realizar check-out: " + e.getMessage());
        }
        return "redirect:" + (referer != null ? referer : "/dashboard");
    }

    @GetMapping("/factura/{id}")
    public String verFactura(@PathVariable Long id, Model model) {
        Optional<Reserva> reservaOpt = reservaService.obtenerReservaPorId(id);
        if (reservaOpt.isPresent()) {
            model.addAttribute("reserva", reservaOpt.get());
            return "factura";
        }
        return "redirect:/cliente/historial";
    }
}

package com.gestion.hotelera.controller;

import com.gestion.hotelera.exception.ClienteConReservasActivasException;
import com.gestion.hotelera.model.Cliente;
import com.gestion.hotelera.service.ClienteService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import java.util.Optional;

/**
 * Controlador de gestión de clientes para ADMIN y RECEPCIONISTA
 * - ADMIN: Acceso completo (ver, registrar, editar, eliminar)
 * - RECEPCIONISTA: Solo puede VER información y REGISTRAR nuevos clientes (NO
 * editar ni eliminar)
 * - Los clientes NO tienen acceso a este controlador (usan
 * ClienteAreaController para su área personal)
 */
@Controller
@RequestMapping("/clientes")
@PreAuthorize("hasAnyAuthority('ROLE_ADMIN', 'ROLE_RECEPCIONISTA')")
public class ClienteController {

    private final ClienteService clienteService;

    public ClienteController(ClienteService clienteService) {
        this.clienteService = clienteService;
    }

    /**
     * Ver/buscar lista de clientes - Accesible por ADMIN y RECEPCIONISTA
     */
    @GetMapping
    public String listarClientes(
            Model model,
            @RequestParam(value = "page", required = false, defaultValue = "0") int page,
            @RequestParam(value = "size", required = false, defaultValue = "10") int size,
            @RequestParam(value = "sortBy", required = false, defaultValue = "id") String sortBy,
            @RequestParam(value = "sortDir", required = false, defaultValue = "asc") String sortDir,
            @RequestParam(value = "search", required = false) String search) {

        Sort sort = sortDir.equalsIgnoreCase("asc") ? Sort.by(sortBy).ascending() : Sort.by(sortBy).descending();
        PageRequest pageRequest = PageRequest.of(Math.max(page, 0), Math.max(size, 1), sort);
        Page<Cliente> clientesPage = clienteService.obtenerClientesPaginados(pageRequest, search);

        model.addAttribute("clientesPage", clientesPage);
        model.addAttribute("currentPage", clientesPage.getNumber());
        model.addAttribute("pageSize", clientesPage.getSize());
        model.addAttribute("sortBy", sortBy);
        model.addAttribute("sortDir", sortDir);
        model.addAttribute("search", search);

        return "clientes";
    }

    // Mantener /historial por compatibilidad o redirigir
    @GetMapping("/historial")
    public String mostrarHistorialRedir(Model model) {
        return "redirect:/clientes";
    }

    /**
     * Formulario de registro de cliente - Solo ADMIN y RECEPCIONISTA
     */
    @GetMapping("/registrar")
    public String mostrarFormularioRegistro(Model model) {
        model.addAttribute("cliente", new Cliente());
        return "registroCliente";
    }

    /**
     * Guardar nuevo cliente - Solo ADMIN y RECEPCIONISTA pueden registrar
     */
    @PostMapping("/guardar")
    public String guardarCliente(@ModelAttribute("cliente") Cliente cliente,
            RedirectAttributes redirectAttributes) {
        try {
            Cliente guardado = clienteService.crearCliente(cliente);
            redirectAttributes.addFlashAttribute("successMessage", "Cliente registrado correctamente: "
                    + guardado.getNombres() + " " + guardado.getApellidos());
            // Redirigir a flujo de reserva para el cliente recién creado
            return "redirect:/reservas/crear?idCliente=" + guardado.getId();
        } catch (IllegalArgumentException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
            redirectAttributes.addFlashAttribute("cliente", cliente);
            return "redirect:/clientes/registrar";
        } catch (Exception ex) {
            redirectAttributes.addFlashAttribute("errorMessage", "Ocurrió un error al registrar el cliente.");
            redirectAttributes.addFlashAttribute("cliente", cliente);
            return "redirect:/clientes/registrar";
        }
    }

    /**
     * Formulario de edición - SOLO ADMIN puede editar
     */
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    @GetMapping("/editar/{id}")
    public String editarCliente(@PathVariable Long id, Model model, RedirectAttributes redirectAttributes) {
        if (id == null || id <= 0) {
            redirectAttributes.addFlashAttribute("errorMessage", "ID inválido");
            return "redirect:/clientes";
        }

        try {
            Optional<Cliente> clienteOpt = clienteService.obtenerClientePorId(id);
            if (clienteOpt.isPresent()) {
                model.addAttribute("cliente", clienteOpt.get());
                return "editarCliente";
            }
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Error al cargar cliente: " + e.getMessage());
            return "redirect:/clientes";
        }

        redirectAttributes.addFlashAttribute("errorMessage", "Cliente no encontrado");
        return "redirect:/clientes";
    }

    /**
     * Actualizar cliente - SOLO ADMIN puede actualizar
     */
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    @PostMapping("/editar/{id}")
    public String actualizarCliente(@PathVariable Long id,
            @ModelAttribute Cliente cliente,
            RedirectAttributes redirectAttributes) {
        if (cliente == null || id == null) {
            redirectAttributes.addFlashAttribute("errorMessage", "Datos inválidos.");
            return "redirect:/clientes";
        }

        cliente.setId(id);

        try {
            clienteService.actualizarCliente(cliente);
            redirectAttributes.addFlashAttribute("successMessage", "Cliente actualizado correctamente.");
            return "redirect:/clientes?id=" + id;
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Error: " + e.getMessage());
            return "redirect:/clientes/editar/" + id;
        }
    }

    /**
     * Eliminar cliente - SOLO ADMIN puede eliminar
     */
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    @PostMapping("/eliminar/{id}")
    public String eliminarCliente(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            boolean eliminado = clienteService.eliminarClientePorId(id);
            if (eliminado) {
                redirectAttributes.addFlashAttribute("successMessage", "Cliente eliminado correctamente.");
            } else {
                redirectAttributes.addFlashAttribute("errorMessage", "No se pudo eliminar el cliente.");
            }
            return "redirect:/clientes";
        } catch (ClienteConReservasActivasException ex) {
            redirectAttributes.addFlashAttribute("errorMessage",
                    "No se puede eliminar el cliente porque tiene reservas activas. Debe cancelar o finalizar las reservas primero.");
            redirectAttributes.addFlashAttribute("reservasBloqueo", ex.getReservasActivas());
            return "redirect:/clientes?id=" + ex.getClienteId();
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Error al eliminar cliente: " + e.getMessage());
            return "redirect:/clientes";
        }
    }
}
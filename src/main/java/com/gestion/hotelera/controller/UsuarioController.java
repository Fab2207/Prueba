package com.gestion.hotelera.controller;

import com.gestion.hotelera.model.Empleado;
import com.gestion.hotelera.model.Usuario;
import com.gestion.hotelera.repository.UsuarioRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.NonNull;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;
import java.util.Optional;

@Controller
@RequestMapping("/usuarios")
@PreAuthorize("hasRole('ADMIN')")
public class UsuarioController {

    private static final Logger logger = LoggerFactory.getLogger(UsuarioController.class);

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;

    public UsuarioController(UsuarioRepository usuarioRepository, PasswordEncoder passwordEncoder) {
        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @GetMapping
    public String listarUsuarios(Model model) {
        List<Usuario> usuarios = usuarioRepository.findAll();
        model.addAttribute("usuarios", usuarios);
        return "usuarios";
    }

    @PostMapping("/guardar")
    public String guardarUsuario(@RequestParam(required = false) Long id,
            @RequestParam String nombre,
            @RequestParam String apellido,
            @RequestParam String email,
            @RequestParam(required = false) String password,
            @RequestParam String rol,
            @RequestParam(defaultValue = "true") Boolean activo,
            RedirectAttributes redirectAttributes) {
        try {
            Usuario usuario;
            Empleado empleado;

            if (id != null) {
                // Editar existente
                Optional<Usuario> usuarioOpt = usuarioRepository.findById(id);
                if (usuarioOpt.isEmpty()) {
                    logger.warn("Intento de actualizar usuario no existente: ID={}", id);
                    redirectAttributes.addFlashAttribute("errorMessage", "Usuario no encontrado");
                    return "redirect:/usuarios";
                }

                usuario = usuarioOpt.get();

                // Validar cambio de email
                if (!usuario.getUsername().equalsIgnoreCase(email)
                        && usuarioRepository.findByUsername(email).isPresent()) {
                    logger.warn("Email ya registrado: {}", email);
                    redirectAttributes.addFlashAttribute("errorMessage",
                            "El email ya está registrado por otro usuario");
                    return "redirect:/usuarios";
                }

                empleado = obtenerOCrearEmpleado(usuario);
                actualizarDatosUsuario(usuario, email, password, rol, activo);
                actualizarDatosEmpleado(empleado, nombre, apellido, email);

                logger.info("Usuario actualizado: ID={}, Username={}", id, email);
            } else {
                // Crear nuevo
                if (validarPasswordRequerida(password, redirectAttributes)) {
                    return "redirect:/usuarios";
                }

                if (usuarioRepository.findByUsername(email).isPresent()) {
                    logger.warn("Intento de crear usuario con email ya registrado: {}", email);
                    redirectAttributes.addFlashAttribute("errorMessage", "El email ya está registrado");
                    return "redirect:/usuarios";
                }

                usuario = crearNuevoUsuario(email, password, rol, activo);
                empleado = crearNuevoEmpleado(nombre, apellido, email);

                empleado.setUsuario(usuario);
                usuario.setEmpleado(empleado);

                logger.info("Nuevo usuario creado: Username={}", email);
            }

            usuarioRepository.save(usuario);
            redirectAttributes.addFlashAttribute("successMessage", "Usuario guardado exitosamente");

        } catch (Exception e) {
            logger.error("Error al guardar usuario", e);
            redirectAttributes.addFlashAttribute("errorMessage", "Error al guardar usuario: " + e.getMessage());
        }
        return "redirect:/usuarios";
    }

    @PostMapping("/{id}/eliminar")
    public String eliminarUsuario(@PathVariable @NonNull Long id, RedirectAttributes redirectAttributes) {
        try {
            Optional<Usuario> usuarioOpt = usuarioRepository.findById(id);
            if (usuarioOpt.isEmpty()) {
                logger.warn("Intento de eliminar usuario no existente: ID={}", id);
                redirectAttributes.addFlashAttribute("errorMessage", "Usuario no encontrado");
                return "redirect:/usuarios";
            }

            Usuario usuario = usuarioOpt.get();
            // Soft delete
            usuario.setActivo(false);
            usuarioRepository.save(usuario);

            logger.info("Usuario desactivado: ID={}, Username={}", id, usuario.getUsername());
            redirectAttributes.addFlashAttribute("successMessage", "Usuario desactivado exitosamente");

        } catch (Exception e) {
            logger.error("Error al desactivar usuario ID={}", id, e);
            redirectAttributes.addFlashAttribute("errorMessage", "Error al eliminar usuario: " + e.getMessage());
        }
        return "redirect:/usuarios";
    }

    // ============== MÉTODOS PRIVADOS DE AYUDA ==============

    private Empleado obtenerOCrearEmpleado(Usuario usuario) {
        Empleado empleado = usuario.getEmpleado();
        if (empleado == null) {
            empleado = new Empleado();
            empleado.setUsuario(usuario);
            usuario.setEmpleado(empleado);
        }
        return empleado;
    }

    private void actualizarDatosUsuario(Usuario usuario, String email, String password, String rol, Boolean activo) {
        usuario.setUsername(email);
        if (password != null && !password.trim().isEmpty()) {
            usuario.setPassword(passwordEncoder.encode(password));
        }
        usuario.setRol(rol);
        usuario.setActivo(activo);
    }

    private void actualizarDatosEmpleado(Empleado empleado, String nombre, String apellido, String email) {
        empleado.setNombres(nombre);
        empleado.setApellidos(apellido);
        empleado.setEmail(email);

        if (empleado.getDni() == null) {
            empleado.setDni(generarDniUnico());
        }
    }

    private boolean validarPasswordRequerida(String password, RedirectAttributes redirectAttributes) {
        if (password == null || password.trim().isEmpty()) {
            logger.warn("Intento de crear usuario sin contraseña");
            redirectAttributes.addFlashAttribute("errorMessage", "La contraseña es obligatoria para usuarios nuevos");
            return true;
        }
        return false;
    }

    private Usuario crearNuevoUsuario(String email, String password, String rol, Boolean activo) {
        Usuario usuario = new Usuario();
        usuario.setUsername(email);
        usuario.setPassword(passwordEncoder.encode(password));
        usuario.setRol(rol);
        usuario.setActivo(activo);
        return usuario;
    }

    private Empleado crearNuevoEmpleado(String nombre, String apellido, String email) {
        Empleado empleado = new Empleado();
        empleado.setNombres(nombre);
        empleado.setApellidos(apellido);
        empleado.setEmail(email);
        empleado.setDni(generarDniUnico());
        return empleado;
    }

    /**
     * Genera un DNI único temporal de 8 dígitos.
     * NOTA: En producción, esto debería validarse contra la base de datos
     * para asegurar unicidad real.
     */
    private String generarDniUnico() {
        // Genera un número de 8 dígitos aleatorio
        String dniTemporal;
        int intentos = 0;

        do {
            dniTemporal = String.valueOf((int) (Math.random() * 90000000) + 10000000);
            intentos++;

            // Verificar si ya existe (búsqueda por empleado con ese DNI)
            // Por ahora solo generamos, pero se podría mejorar con validación
            if (intentos > 10) {
                logger.warn("Múltiples intentos para generar DNI único. Usando timestamp.");
                dniTemporal = String.valueOf(System.currentTimeMillis() % 100000000);
                break;
            }
        } while (false); // Simplificado: en producción validar contra BD

        logger.debug("DNI temporal generado: {}", dniTemporal);
        return dniTemporal;
    }
}

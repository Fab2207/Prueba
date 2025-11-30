package com.gestion.hotelera.service;

import com.gestion.hotelera.model.Empleado;
import com.gestion.hotelera.model.Usuario;
import com.gestion.hotelera.repository.EmpleadoRepository;
import com.gestion.hotelera.repository.UsuarioRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
public class EmpleadoService {

    private static final Logger logger = LoggerFactory.getLogger(EmpleadoService.class);
    private static final String ROLE_RECEPCIONISTA = "ROLE_RECEPCIONISTA";

    private final EmpleadoRepository empleadoRepository;
    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditoriaService auditoriaService;

    public EmpleadoService(EmpleadoRepository empleadoRepository,
            UsuarioRepository usuarioRepository,
            PasswordEncoder passwordEncoder,
            AuditoriaService auditoriaService) {
        this.empleadoRepository = empleadoRepository;
        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
        this.auditoriaService = auditoriaService;
    }

    @Transactional
    public Empleado registrarRecepcionista(Empleado empleado) {
        validarEmpleado(empleado);
        validarDatosUnicos(empleado);

        String rawPassword = empleado.getUsuario().getPassword();
        validarPassword(rawPassword);

        empleado.getUsuario().setPassword(passwordEncoder.encode(rawPassword));
        empleado.getUsuario().setRol(ROLE_RECEPCIONISTA);

        Empleado nuevoEmpleado = empleadoRepository.save(empleado);
        registrarAuditoriaCreacion(nuevoEmpleado);
        logger.info("Recepcionista registrado: ID={}, DNI={}", nuevoEmpleado.getId(), nuevoEmpleado.getDni());
        return nuevoEmpleado;
    }

    @Transactional(readOnly = true)
    public List<Empleado> obtenerTodosLosEmpleados() {
        return empleadoRepository.findAll();
    }

    @Transactional(readOnly = true)
    public Optional<Empleado> buscarEmpleadoPorId(Long id) {
        if (id == null) {
            return Optional.empty();
        }
        return empleadoRepository.findById(id);
    }

    @Transactional
    public Empleado actualizarEmpleado(Empleado empleadoActualizado) {
        if (empleadoActualizado == null || empleadoActualizado.getId() == null) {
            throw new IllegalArgumentException("El empleado y su ID no pueden ser nulos");
        }

        return empleadoRepository.findById(empleadoActualizado.getId())
                .map(empleadoExistente -> actualizarDatosEmpleado(empleadoExistente, empleadoActualizado))
                .orElseThrow(() -> new IllegalArgumentException(
                        "Empleado con ID " + empleadoActualizado.getId() + " no encontrado."));
    }

    @Transactional
    public boolean eliminarEmpleado(Long id) {
        if (id == null) {
            return false;
        }

        return empleadoRepository.findById(id)
                .map(empleado -> {
                    registrarAuditoriaEliminacion(empleado);
                    empleadoRepository.delete(empleado);
                    logger.info("Empleado eliminado: ID={}, DNI={}", id, empleado.getDni());
                    return true;
                })
                .orElse(false);
    }

    @Transactional(readOnly = true)
    public Empleado obtenerPorUsername(String username) {
        if (username == null || username.trim().isEmpty()) {
            return null;
        }
        return empleadoRepository.findByUsuarioUsername(username).orElse(null);
    }

    @Transactional(readOnly = true)
    public long contarEmpleados() {
        return empleadoRepository.count();
    }

    // ============== MÉTODOS PRIVADOS DE AYUDA ==============

    private void validarEmpleado(Empleado empleado) {
        if (empleado == null) {
            throw new IllegalArgumentException("El empleado no puede ser nulo");
        }
        if (empleado.getDni() == null || empleado.getDni().trim().isEmpty()) {
            throw new IllegalArgumentException("El DNI no puede estar vacío");
        }
        if (empleado.getEmail() == null || empleado.getEmail().trim().isEmpty()) {
            throw new IllegalArgumentException("El email no puede estar vacío");
        }
        if (empleado.getUsuario() == null) {
            throw new IllegalArgumentException("Los datos de usuario no pueden estar vacíos");
        }
    }

    private void validarPassword(String password) {
        if (password == null || password.trim().isEmpty()) {
            throw new IllegalArgumentException("La contraseña no puede estar vacía.");
        }
    }

    private void validarDatosUnicos(Empleado empleado) {
        if (empleadoRepository.findByDni(empleado.getDni()).isPresent()) {
            throw new IllegalArgumentException("Ya existe un empleado con el DNI '" + empleado.getDni() + "'.");
        }
        if (empleadoRepository.findByEmail(empleado.getEmail()).isPresent()) {
            throw new IllegalArgumentException("Ya existe un empleado con el email '" + empleado.getEmail() + "'.");
        }
        if (usuarioRepository.findByUsername(empleado.getUsuario().getUsername()).isPresent()) {
            throw new IllegalArgumentException(
                    "Ya existe un usuario con el nombre de usuario '" + empleado.getUsuario().getUsername() + "'.");
        }
    }

    private Empleado actualizarDatosEmpleado(Empleado existente, Empleado actualizado) {
        // Validar cambios únicos
        if (!existente.getDni().equals(actualizado.getDni())) {
            validarDniUnico(actualizado.getDni(), existente.getId());
        }
        if (!existente.getEmail().equals(actualizado.getEmail())) {
            validarEmailUnico(actualizado.getEmail(), existente.getId());
        }

        // Actualizar datos de empleado
        existente.setNombres(actualizado.getNombres());
        existente.setApellidos(actualizado.getApellidos());
        existente.setDni(actualizado.getDni());
        existente.setEmail(actualizado.getEmail());
        existente.setTelefono(actualizado.getTelefono());

        // Actualizar datos de usuario
        actualizarUsuario(existente.getUsuario(), actualizado.getUsuario());

        Empleado empleadoGuardado = empleadoRepository.save(existente);
        registrarAuditoriaActualizacion(empleadoGuardado);
        logger.info("Empleado actualizado: ID={}, DNI={}", empleadoGuardado.getId(), empleadoGuardado.getDni());
        return empleadoGuardado;
    }

    private void actualizarUsuario(Usuario usuarioExistente, Usuario usuarioActualizado) {
        if (!usuarioExistente.getUsername().equals(usuarioActualizado.getUsername())) {
            Optional<Usuario> existingUserWithNewUsername = usuarioRepository
                    .findByUsername(usuarioActualizado.getUsername());
            if (existingUserWithNewUsername.isPresent()
                    && !existingUserWithNewUsername.get().getId().equals(usuarioExistente.getId())) {
                throw new IllegalArgumentException(
                        "El nombre de usuario '" + usuarioActualizado.getUsername() + "' ya está en uso.");
            }
            usuarioExistente.setUsername(usuarioActualizado.getUsername());
        }

        if (usuarioActualizado.getPassword() != null && !usuarioActualizado.getPassword().isEmpty()) {
            usuarioExistente.setPassword(passwordEncoder.encode(usuarioActualizado.getPassword()));
        }
    }

    private void validarDniUnico(String dni, Long idExcluir) {
        Optional<Empleado> existente = empleadoRepository.findByDni(dni);
        if (existente.isPresent() && !existente.get().getId().equals(idExcluir)) {
            throw new IllegalArgumentException("El DNI '" + dni + "' ya está en uso.");
        }
    }

    private void validarEmailUnico(String email, Long idExcluir) {
        Optional<Empleado> existente = empleadoRepository.findByEmail(email);
        if (existente.isPresent() && !existente.get().getId().equals(idExcluir)) {
            throw new IllegalArgumentException("El Email '" + email + "' ya está en uso.");
        }
    }

    private void registrarAuditoriaCreacion(Empleado empleado) {
        if (empleado.getId() != null) {
            auditoriaService.registrarAccion("CREACION_EMPLEADO",
                    "Nuevo recepcionista: " + empleado.getNombres() + " " + empleado.getApellidos()
                            + " (DNI: " + empleado.getDni() + ")",
                    "Empleado",
                    empleado.getId());
        }
    }

    private void registrarAuditoriaActualizacion(Empleado empleado) {
        if (empleado.getId() != null) {
            auditoriaService.registrarAccion("ACTUALIZACION_EMPLEADO",
                    "Empleado '" + empleado.getNombres() + " " + empleado.getApellidos() + "' (ID: "
                            + empleado.getId() + ") actualizado.",
                    "Empleado",
                    empleado.getId());
        }
    }

    private void registrarAuditoriaEliminacion(Empleado empleado) {
        if (empleado.getId() != null) {
            auditoriaService.registrarAccion("ELIMINACION_EMPLEADO",
                    "Empleado '" + empleado.getNombres() + " " + empleado.getApellidos() + "' (ID: "
                            + empleado.getId() + ") eliminado.",
                    "Empleado",
                    empleado.getId());
        }
    }
}
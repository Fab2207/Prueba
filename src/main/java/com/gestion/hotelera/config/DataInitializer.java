package com.gestion.hotelera.config;

import com.gestion.hotelera.model.Habitacion;
import com.gestion.hotelera.model.Usuario;
import com.gestion.hotelera.repository.HabitacionRepository;
import com.gestion.hotelera.repository.UsuarioRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Inicializador de datos para la base de datos
 * Se ejecuta automáticamente al iniciar la aplicación
 */
@Component
public class DataInitializer implements CommandLineRunner {

    @Autowired
    private HabitacionRepository habitacionRepository;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) throws Exception {
        System.out.println("\n🔄 Verificando datos iniciales...");

        // Inicializar usuario admin
        initAdminUser();

        // Inicializar habitaciones
        initHabitaciones();

        System.out.println("✅ Datos iniciales verificados correctamente!\n");
    }

    private void initAdminUser() {
        Usuario admin = usuarioRepository.findByUsername("admin").orElse(new Usuario());

        boolean esNuevo = admin.getId() == null;

        admin.setUsername("admin");
        admin.setPassword(passwordEncoder.encode("admin123"));
        admin.setRol("ROLE_ADMIN");
        admin.setActivo(true); // ¡CRÍTICO! Asegurar que el usuario esté habilitado

        usuarioRepository.save(admin);

        if (esNuevo) {
            System.out.println("   ✓ Usuario admin creado (username: admin, password: admin123)");
        } else {
            System.out.println("   ✓ Usuario admin actualizado/restablecido (username: admin, password: admin123)");
        }
    }

    private void initHabitaciones() {
        long count = habitacionRepository.count();

        if (count == 0) {
            System.out.println("🏨 Creando 20 habitaciones...");

            // Suites Presidenciales (3)
            crearHabitacion("101", "SUITE PRESIDENCIAL", 450.00);
            crearHabitacion("102", "SUITE PRESIDENCIAL", 450.00);
            crearHabitacion("103", "SUITE PRESIDENCIAL", 450.00);

            // Suites Junior (4)
            crearHabitacion("201", "SUITE JUNIOR", 350.00);
            crearHabitacion("202", "SUITE JUNIOR", 350.00);
            crearHabitacion("203", "SUITE JUNIOR", 350.00);
            crearHabitacion("204", "SUITE JUNIOR", 350.00);

            // Habitaciones Dobles (6)
            crearHabitacion("301", "DOBLE", 280.00);
            crearHabitacion("302", "DOBLE", 280.00);
            crearHabitacion("303", "DOBLE", 280.00);
            crearHabitacion("304", "DOBLE", 280.00);
            crearHabitacion("305", "DOBLE", 280.00);
            crearHabitacion("306", "DOBLE", 280.00);

            // Habitaciones Simples (8)
            crearHabitacion("401", "SIMPLE", 180.00);
            crearHabitacion("402", "SIMPLE", 180.00);
            crearHabitacion("403", "SIMPLE", 180.00);
            crearHabitacion("404", "SIMPLE", 180.00);
            crearHabitacion("405", "SIMPLE", 180.00);
            crearHabitacion("406", "SIMPLE", 180.00);
            crearHabitacion("407", "SIMPLE", 180.00);
            crearHabitacion("408", "SIMPLE", 180.00);

            System.out.println("   ✓ 20 habitaciones creadas exitosamente");
        } else {
            System.out.println("   ℹ Habitaciones ya existen (" + count + " en total)");
        }
    }

    private void crearHabitacion(String numero, String tipo, Double precio) {
        Habitacion habitacion = new Habitacion();
        habitacion.setNumero(numero);
        habitacion.setTipo(tipo);
        habitacion.setPrecioPorNoche(precio);
        habitacion.setEstado("DISPONIBLE");
        habitacionRepository.save(habitacion);
    }
}
package com.gestion.hotelera;

import com.gestion.hotelera.config.JwtProperties;
import com.gestion.hotelera.config.MailConfigurationProperties;
import com.gestion.hotelera.model.Usuario;
import com.gestion.hotelera.repository.UsuarioRepository;
import com.gestion.hotelera.service.HabitacionService;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.security.crypto.password.PasswordEncoder;

@SpringBootApplication
@EnableConfigurationProperties({ JwtProperties.class, MailConfigurationProperties.class })
@EnableScheduling
public class HoteleraApplication {

    public static void main(String[] args) {
        SpringApplication.run(HoteleraApplication.class, args);
    }

    @Bean
    @org.springframework.context.annotation.Profile("!test")
    public CommandLineRunner initDatabase(
            HabitacionService habitacionService,
            UsuarioRepository usuarioRepository,
            PasswordEncoder passwordEncoder) {
        return args -> {
            habitacionService.inicializarHabitacionesSiNoExisten();

            crearUsuarioSiNoExiste(usuarioRepository, passwordEncoder, "admin", "admin123", "ROLE_ADMIN");
            crearUsuarioSiNoExiste(usuarioRepository, passwordEncoder, "recep", "recep123", "ROLE_RECEPCIONISTA");
            crearUsuarioSiNoExiste(usuarioRepository, passwordEncoder, "cliente", "cliente123", "ROLE_CLIENTE");
        };
    }

    private void crearUsuarioSiNoExiste(
            UsuarioRepository usuarioRepository,
            PasswordEncoder passwordEncoder,
            String username,
            String rawPassword,
            String rol) {

        if (usuarioRepository.findByUsername(username).isEmpty()) {
            Usuario usuario = new Usuario(username, passwordEncoder.encode(rawPassword), rol);
            usuarioRepository.save(usuario);
        }
    }
}

package com.gestion.hotelera.service;

import com.gestion.hotelera.dto.ConfiguracionDTO;
import com.gestion.hotelera.model.Configuracion;
import com.gestion.hotelera.repository.ConfiguracionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ConfiguracionService {

    private final ConfiguracionRepository configuracionRepository;

    public ConfiguracionService(ConfiguracionRepository configuracionRepository) {
        this.configuracionRepository = configuracionRepository;
    }

    public ConfiguracionDTO obtenerConfiguracion() {
        return configuracionRepository.findAll().stream().findFirst()
                .map(this::mapToDTO)
                .orElseGet(() -> {
                    // Retornar configuración por defecto si no existe
                    ConfiguracionDTO dto = new ConfiguracionDTO();
                    dto.setNombreHotel("Oasis Digital");
                    dto.setMoneda("USD");
                    dto.setZonaHoraria("UTC");
                    return dto;
                });
    }

    @Transactional
    public void guardarConfiguracion(ConfiguracionDTO dto) {
        Configuracion config = configuracionRepository.findAll().stream().findFirst().orElse(new Configuracion());

        config.setNombreHotel(dto.getNombreHotel());
        config.setDireccion(dto.getDireccion());
        config.setTelefono(dto.getTelefono());
        config.setEmailContacto(dto.getEmailContacto());
        config.setMoneda(dto.getMoneda());
        config.setZonaHoraria(dto.getZonaHoraria());

        configuracionRepository.save(config);
    }

    private ConfiguracionDTO mapToDTO(Configuracion entity) {
        ConfiguracionDTO dto = new ConfiguracionDTO();
        dto.setNombreHotel(entity.getNombreHotel());
        dto.setDireccion(entity.getDireccion());
        dto.setTelefono(entity.getTelefono());
        dto.setEmailContacto(entity.getEmailContacto());
        dto.setMoneda(entity.getMoneda());
        dto.setZonaHoraria(entity.getZonaHoraria());
        return dto;
    }
}

package com.gestion.hotelera.repository;

import com.gestion.hotelera.model.Configuracion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ConfiguracionRepository extends JpaRepository<Configuracion, Long> {
    // Generalmente solo habrá un registro de configuración
}

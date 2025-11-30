package com.gestion.hotelera.service;

import com.gestion.hotelera.model.Resena;
import com.gestion.hotelera.repository.ResenaRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ResenaService {

    private static final Logger logger = LoggerFactory.getLogger(ResenaService.class);

    private final ResenaRepository resenaRepository;

    public ResenaService(ResenaRepository resenaRepository) {
        this.resenaRepository = resenaRepository;
    }

    @Transactional
    public Resena guardarResena(Resena resena) {
        Resena guardada = resenaRepository.save(resena);
        logger.info("Reseña guardada: ID={}, Cliente ID={}", guardada.getId(),
                guardada.getCliente() != null ? guardada.getCliente().getId() : "N/A");
        return guardada;
    }

    @Transactional(readOnly = true)
    public List<Resena> obtenerPorCliente(Long clienteId) {
        return resenaRepository.findByClienteId(clienteId);
    }

    @Transactional(readOnly = true)
    public boolean existeResenaParaReserva(Long reservaId) {
        return !resenaRepository.findByReservaId(reservaId).isEmpty();
    }
}

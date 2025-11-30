package com.gestion.hotelera.service;

import com.gestion.hotelera.enums.EstadoHabitacion;
import com.gestion.hotelera.enums.EstadoReserva;
import com.gestion.hotelera.model.Habitacion;habitacionRepository.deleteById(id);registrarAuditoriaEliminacion(habitacion);logger.info("Habitación eliminada: ID={}, Número={}",id,habitacion.getNumero());}

@Transactional(readOnly=true)public Page<Habitacion>obtenerHabitacionesPaginadas(Pageable pageable,String search){if(search!=null&&!search.trim().isEmpty()){return habitacionRepository.findByNumeroContainingIgnoreCaseOrTipoContainingIgnoreCase(search,search,pageable);}return habitacionRepository.findAll(pageable);}

// ============== MÉTODOS PRIVADOS DE AYUDA ==============

/**
 * Optimización: Obtiene IDs de habitaciones con reservas activas en una sola
 * consulta,
 * evitando N+1 queries.
 */
private Set<Long>obtenerIdsHabitacionesConReservasActivas(LocalDate fecha){List<Reserva>todasReservas=reservaRepository.findAll();

return todasReservas.stream().filter(r->esReservaActivaEnFecha(r,fecha)).map(r->r.getHabitacion().getId()).collect(Collectors.toSet());}

private boolean esReservaActivaEnFecha(Reserva reserva,LocalDate fecha){if(reserva.getHabitacion()==null||reserva.getFechaInicio()==null||reserva.getFechaFin()==null){return false;}

String estado=reserva.getEstadoReserva();boolean esEstadoActivo=EstadoReserva.ACTIVA.getValor().equals(estado)||EstadoReserva.PENDIENTE.getValor().equals(estado);

return esEstadoActivo&&!reserva.getFechaInicio().isAfter(fecha)&&!reserva.getFechaFin().isBefore(fecha);}

private void validarNumeroUnico(String numero,Habitacion habitacionExistente){if(!habitacionExistente.getNumero().equals(numero)){Optional<Habitacion>existeOtra=habitacionRepository.findByNumero(numero);if(existeOtra.isPresent()&&!existeOtra.get().getId().equals(habitacionExistente.getId())){throw new IllegalArgumentException("El número de habitación '"+numero+"' ya está en uso.");}}}

private Habitacion actualizarDatosHabitacion(Habitacion existente,Habitacion actualizada){existente.setNumero(actualizada.getNumero());existente.setTipo(actualizada.getTipo());existente.setPrecioPorNoche(actualizada.getPrecioPorNoche());existente.setEstado(actualizada.getEstado());

Habitacion habitacionGuardada=habitacionRepository.save(existente);registrarAuditoriaActualizacion(habitacionGuardada);logger.info("Habitación actualizada: ID={}, Número={}",habitacionGuardada.getId(),habitacionGuardada.getNumero());return habitacionGuardada;}

private void eliminarReservasAsociadas(Habitacion habitacion){List<Reserva>reservas=reservaRepository.findByHabitacion(habitacion);if(!reservas.isEmpty()){reservaRepository.deleteAll(reservas);logger.debug("Eliminadas {} reservas asociadas a habitación ID={}",reservas.size(),habitacion.getId());}}

private void registrarAuditoriaCreacion(Habitacion habitacion){if(habitacion.getId()!=null){auditoriaService.registrarAccion("CREACION_HABITACION","Nueva habitación registrada: #"+habitacion.getNumero()+" ("+habitacion.getTipo()+", $"+habitacion.getPrecioPorNoche()+")","Habitacion",habitacion.getId());}}

private void registrarAuditoriaActualizacion(Habitacion habitacion){if(habitacion.getId()!=null){auditoriaService.registrarAccion("ACTUALIZACION_HABITACION","Habitación #"+habitacion.getNumero()+" (ID: "+habitacion.getId()+") actualizada. Nuevo estado: "+habitacion.getEstado(),"Habitacion",habitacion.getId());}}

private void registrarAuditoriaCambioEstado(Habitacion habitacion,String estadoAnterior,String nuevoEstado){if(habitacion.getId()!=null){auditoriaService.registrarAccion("CAMBIO_ESTADO_HABITACION","Estado de habitación #"+habitacion.getNumero()+" (ID: "+habitacion.getId()+") cambiado de '"+estadoAnterior+"' a '"+nuevoEstado+"'.","Habitacion",habitacion.getId());}}

private void registrarAuditoriaEliminacion(Habitacion habitacion){if(habitacion.getId()!=null){auditoriaService.registrarAccion("ELIMINACION_HABITACION","Habitación #"+habitacion.getNumero()+" (ID: "+habitacion.getId()+") eliminada.","Habitacion",habitacion.getId());}}}
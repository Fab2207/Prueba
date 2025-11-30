package com.gestion.hotelera.service;

import com.gestion.hotelera.enums.EstadoHabitacion;
import com.gestion.hotelera.enums.EstadoReserva;
import com.gestion.hotelera.model.Cliente;
import com.gestion.hotelera.model.Descuento;
import com.gestion.hotelera.model.Reserva;
import com.gestion.hotelera.model.Servicio;
import com.gestion.hotelera.repository.ReservaRepository;
import com.gestion.hotelera.repository.ServicioRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.*;

@Service
public class ReservaService {

    private static final Logger logger = LoggerFactory.getLogger(ReservaService.class);

    private final ReservaRepository reservaRepository;
    private final AuditoriaService auditoriaService;
    private final ServicioRepository servicioRepository;
    private final HabitacionService habitacionService;
    private final DescuentoService descuentoService;
    private final EmailService emailService;

    @Autowired
    public ReservaService(ReservaRepository reservaRepository,
            AuditoriaService auditoriaService,
            ServicioRepository servicioRepository,
            HabitacionService habitacionService,
            DescuentoService descuentoService,
            EmailService emailService) {
        this.reservaRepository = reservaRepository;
        this.auditoriaService = auditoriaService;
        this.servicioRepository = servicioRepository;
        this.habitacionService = habitacionService;
        this.descuentoService = descuentoService;
        this.emailService = emailService;
    }

    @Transactional
    public @NonNull Reserva crearOActualizarReserva(@NonNull Reserva reserva) {
        validarReserva(reserva);
        Long habitacionId = reserva.getHabitacion().getId();
        Long reservaId = reserva.getId();

        verificarHabitacionExisteYDisponible(habitacionId);
        verificarDisponibilidadFechas(habitacionId, reserva, reservaId);

        try {
            Reserva guardada = reservaRepository.save(reserva);
            actualizarEstadoHabitacionSegunReserva(guardada);
            registrarAuditoriaCreacionOActualizacion(guardada);
            enviarEmailConfirmacionSiEsNueva(reserva.getId() == null, guardada);

            logger.info("Reserva creada/actualizada: ID={}, Cliente={}, Habitación={}",
                    guardada.getId(),
                    guardada.getCliente() != null ? guardada.getCliente().getNombres() : "N/A",
                    guardada.getHabitacion() != null ? guardada.getHabitacion().getNumero() : "N/A");

            return guardada;
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            logger.error("Error al guardar reserva", e);
            throw new RuntimeException("Error al guardar la reserva: " + e.getMessage(), e);
        }
    }

    @Transactional(readOnly = true)
    public List<Reserva> obtenerTodasLasReservas() {
        return reservaRepository.findAll();
    }

    @Transactional(readOnly = true)
    public List<Reserva> obtenerUltimasReservas(int limite) {
        return reservaRepository.findAll(
                PageRequest.of(0, limite, Sort.by(Sort.Direction.DESC, "id")))
                .getContent();
    }

    @Transactional(readOnly = true)
    public Optional<Reserva> buscarReservaPorId(Long id) {
        if (id == null) {
            return Optional.empty();
        }
        return reservaRepository.findById(id);
    }

    @Transactional(readOnly = true)
    public Optional<Reserva> obtenerReservaPorId(Long id) {
        return buscarReservaPorId(id);
    }

    @Transactional(readOnly = true)
    public List<Reserva> obtenerReservasPorCliente(Cliente cliente) {
        return reservaRepository.findByCliente(cliente);
    }

    @Transactional(readOnly = true)
    public List<Reserva> obtenerReservasPorClienteId(Long clienteId) {
        if (clienteId == null) {
            return new ArrayList<>();
        }
        List<Reserva> todas = reservaRepository.findAll();
        return todas.stream()
                .filter(r -> r.getCliente() != null && Objects.equals(r.getCliente().getId(), clienteId))
                .toList();
    }

    @Transactional(readOnly = true)
    public long contarReservas() {
        long count = reservaRepository.count();
        logger.debug("Total reservas en BD: {}", count);
        return count;
    }

    @Transactional
    public boolean cancelarReserva(@NonNull Long id, String userRole) {
        if (id == null) {
            return false;
        }

        return reservaRepository.findById(id)
                .map(reserva -> {
                    validarCancelacion(reserva, userRole);
                    reserva.setEstadoReserva(EstadoReserva.CANCELADA.getValor());
                    Reserva reservaCancelada = reservaRepository.save(reserva);

                    liberarHabitacion(reservaCancelada);
                    registrarAuditoriaCancelacion(reservaCancelada, userRole);

                    logger.info("Reserva cancelada: ID={}, Estado={}", id, reservaCancelada.getEstadoReserva());
                    return true;
                })
                .orElse(false);
    }

    @Transactional
    public boolean cancelarReserva(Long id) {
        return cancelarReserva(id, "ROLE_ADMIN");
    }

    @Transactional
    public boolean eliminarReservaFisica(Long id) {
        if (id == null) {
            return false;
        }
        return reservaRepository.findById(id)
                .map(reserva -> {
                    reservaRepository.deleteById(id);
                    auditoriaService.registrarAccion("ELIMINACION_RESERVA",
                            "Reserva (ID: " + id + ") eliminada físicamente.", "Reserva", id);
                    logger.info("Reserva eliminada físicamente: ID={}", id);
                    return true;
                })
                .orElse(false);
    }

    @Transactional
    public void finalizarReserva(Long id) {
        if (id == null) {
            throw new IllegalArgumentException("El ID de la reserva no puede ser nulo");
        }

        reservaRepository.findById(id).ifPresent(reserva -> {
            if (EstadoReserva.FINALIZADA.getValor().equalsIgnoreCase(reserva.getEstadoReserva())) {
                return;
            }

            String estadoAnterior = reserva.getEstadoReserva();
            reserva.setEstadoReserva(EstadoReserva.FINALIZADA.getValor());

            if (reserva.getFechaSalidaReal() == null) {
                reserva.setFechaSalidaReal(LocalDate.now());
            }

            reservaRepository.save(reserva);
            liberarHabitacion(reserva);

            auditoriaService.registrarAccion("FINALIZACION_RESERVA",
                    "Reserva finalizada (ID: " + reserva.getId() + ") - Estado anterior: " + estadoAnterior,
                    "Reserva", reserva.getId());

            logger.info("Reserva finalizada: ID={}, Estado anterior={}", id, estadoAnterior);
        });
    }

    @Transactional(readOnly = true)
    public Integer calcularDiasEstadia(LocalDate inicio, LocalDate fin) {
        long dias = java.time.temporal.ChronoUnit.DAYS.between(inicio, fin);
        return dias == 0 ? 1 : (int) dias;
    }

    @Transactional(readOnly = true)
    public Double calcularTotalPagar(Double precioPorNoche, Integer dias) {
        return precioPorNoche * dias;
    }

    @Transactional(readOnly = true)
    public double calcularIngresosTotales() {
        List<Reserva> reservas = reservaRepository.findAll();
        return reservas.stream()
                .filter(r -> EstadoReserva.FINALIZADA.getValor().equalsIgnoreCase(r.getEstadoReserva()))
                .mapToDouble(Reserva::getTotalPagar)
                .sum();
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> getIngresosPorPeriodo(LocalDate inicio, LocalDate fin) {
        List<Reserva> reservas = reservaRepository.findAll();
        Map<LocalDate, Double> ingresosPorFecha = inicializarMapaFechas(inicio, fin);

        for (Reserva r : reservas) {
            if (estaEnRangoDeFechas(r.getFechaInicio(), inicio, fin) && esReservaContabilizable(r)) {
                double total = calcularTotalReserva(r);
                ingresosPorFecha.merge(r.getFechaInicio(), total, Double::sum);
            }
        }

        return convertirMapaALista(ingresosPorFecha);
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> getIngresosUltimosDias(int dias) {
        LocalDate fin = LocalDate.now();
        LocalDate inicio = fin.minusDays(dias - 1);
        return getIngresosPorPeriodo(inicio, fin);
    }

    @Transactional(readOnly = true)
    public List<Reserva> obtenerReservasPorPeriodo(LocalDate inicio, LocalDate fin) {
        List<Reserva> todas = reservaRepository.findAll();
        return todas.stream()
                .filter(r -> hayOverlap(r.getFechaInicio(), r.getFechaFin(), inicio, fin))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> getMovimientoPorPeriodo(LocalDate inicio, LocalDate fin) {
        List<Reserva> reservas = reservaRepository.findAll();
        Map<LocalDate, Map<String, Integer>> movimientos = inicializarMapaMovimientos(inicio, fin);

        for (Reserva r : reservas) {
            if (estaEnRangoDeFechas(r.getFechaInicio(), inicio, fin)) {
                movimientos.get(r.getFechaInicio()).merge("checkIns", 1, Integer::sum);
            }
            if (estaEnRangoDeFechas(r.getFechaFin(), inicio, fin)) {
                movimientos.get(r.getFechaFin()).merge("checkOuts", 1, Integer::sum);
            }
        }

        return convertirMovimientosALista(movimientos);
    }

    @Transactional(readOnly = true)
    public long contarReservasPorEstado(String estado) {
        return reservaRepository.countByEstadoReservaIgnoreCase(estado);
    }

    @Transactional(readOnly = true)
    public long contarCheckInsHoy() {
        return reservaRepository.countByFechaInicio(LocalDate.now());
    }

    @Transactional(readOnly = true)
    public long contarCheckOutsHoy() {
        return reservaRepository.countByFechaFin(LocalDate.now());
    }

    @Transactional(readOnly = true)
    public List<Reserva> obtenerLlegadasHoy() {
        LocalDate hoy = LocalDate.now();
        return reservaRepository.findAll().stream()
                .filter(r -> r.getFechaInicio() != null && r.getFechaInicio().equals(hoy)
                        && EstadoReserva.PENDIENTE.getValor().equalsIgnoreCase(r.getEstadoReserva()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<Reserva> obtenerSalidasHoy() {
        LocalDate hoy = LocalDate.now();
        return reservaRepository.findAll().stream()
                .filter(r -> r.getFechaFin() != null && r.getFechaFin().equals(hoy)
                        && EstadoReserva.ACTIVA.getValor().equalsIgnoreCase(r.getEstadoReserva()))
                .toList();
    }

    @Transactional
    public @NonNull Reserva asignarServicios(@NonNull Long reservaId, List<Long> servicioIds, List<String> opciones) {
        if (reservaId == null) {
            throw new IllegalArgumentException("El ID de la reserva no puede ser nulo");
        }

        Reserva reserva = reservaRepository.findById(reservaId)
                .orElseThrow(() -> new IllegalArgumentException("Reserva no encontrada para el ID " + reservaId));

        if (servicioRepository == null) {
            throw new IllegalStateException("Repositorio de servicios no disponible en este contexto.");
        }

        asignarServiciosAReserva(reserva, servicioIds, opciones);
        Reserva actualizada = reservaRepository.save(reserva);

        auditoriaService.registrarAccion("ASIGNACION_SERVICIOS_RESERVA",
                "Servicios actualizados para la reserva ID: " + reservaId,
                "Reserva", reservaId);

        logger.info("Servicios asignados a reserva ID={}", reservaId);
        return actualizada;
    }

    @Transactional(readOnly = true)
    public double calcularTotalConServicios(Reserva reserva) {
        double base = reserva.getTotalPagar() != null ? reserva.getTotalPagar() : 0.0;
        double extras = reserva.calcularTotalServicios();
        return base + extras;
    }

    @Transactional(readOnly = true)
    public long contarReservasPorCliente(String username) {
        return reservaRepository.findAll().stream()
                .filter(r -> perteneceACliente(r, username))
                .count();
    }

    @Transactional(readOnly = true)
    public long contarReservasActivasPorCliente(String username) {
        return reservaRepository.findAll().stream()
                .filter(r -> perteneceACliente(r, username) && esReservaActiva(r))
                .count();
    }

    @Transactional(readOnly = true)
    public long contarReservasFinalizadasPorCliente(String username) {
        return reservaRepository.findAll().stream()
                .filter(r -> perteneceACliente(r, username) &&
                        EstadoReserva.FINALIZADA.getValor().equalsIgnoreCase(r.getEstadoReserva()))
                .count();
    }

    @Transactional
    public @NonNull Reserva realizarCheckIn(@NonNull Long reservaId) {
        Reserva reserva = reservaRepository.findById(reservaId)
                .orElseThrow(() -> new IllegalArgumentException("Reserva no encontrada"));

        validarCheckIn(reserva);

        LocalDate hoy = LocalDate.now();
        reserva.setEstadoReserva(EstadoReserva.ACTIVA.getValor());
        reserva.setFechaCheckinReal(hoy);
        reserva.setHoraCheckinReal(java.time.LocalTime.now());

        actualizarHabitacionAOcupada(reserva);
        Reserva actualizada = reservaRepository.save(reserva);
        enviarEmailCheckIn(actualizada);

        logger.info("Check-in realizado para reserva ID={}", reservaId);
        return actualizada;
    }

    @Transactional
    public @NonNull Reserva realizarCheckOut(@NonNull Long reservaId) {
        Reserva reserva = reservaRepository.findById(reservaId)
                .orElseThrow(() -> new IllegalArgumentException("Reserva no encontrada"));

        validarCheckOut(reserva);

        LocalDate hoy = LocalDate.now();
        reserva.setEstadoReserva(EstadoReserva.FINALIZADA.getValor());
        reserva.setFechaSalidaReal(hoy);
        reserva.setHoraCheckoutReal(java.time.LocalTime.now());

        actualizarHabitacionADisponible(reserva);
        Reserva actualizada = reservaRepository.save(reserva);
        enviarEmailCheckOut(actualizada, hoy);

        logger.info("Check-out realizado para reserva ID={}", reservaId);
        return actualizada;
    }

    @Transactional
    public @NonNull Reserva aplicarDescuento(@NonNull Long reservaId, String codigoDescuento) {
        if (descuentoService == null) {
            throw new IllegalStateException("Servicio de descuentos no disponible");
        }

        Reserva reserva = reservaRepository.findById(reservaId)
                .orElseThrow(() -> new IllegalArgumentException("Reserva no encontrada"));

        if (reserva.getDescuento() != null) {
            throw new IllegalStateException("La reserva ya tiene un descuento aplicado");
        }

        double montoTotal = calcularMontoTotalParaDescuento(reserva);
        Descuento descuento = validarYObtenerDescuento(codigoDescuento, montoTotal);

        double montoDescuento = descuento.calcularDescuento(montoTotal);
        reserva.setDescuento(descuento);
        reserva.setMontoDescuento(montoDescuento);

        descuentoService.incrementarUso(descuento);

        logger.info("Descuento aplicado a reserva ID={}, Código={}, Monto={}",
                reservaId, codigoDescuento, montoDescuento);

        return reservaRepository.save(reserva);
    }

    // ============== MÉTODOS PRIVADOS DE AYUDA ==============

    private void validarReserva(Reserva reserva) {
        if (reserva == null) {
            throw new IllegalArgumentException("La reserva no puede ser nula");
        }
        if (reserva.getCliente() == null) {
            throw new IllegalArgumentException("La reserva debe tener un cliente asignado");
        }
        if (reserva.getHabitacion() == null) {
            throw new IllegalArgumentException("La reserva debe tener una habitación asignada");
        }
        if (reserva.getFechaInicio() == null || reserva.getFechaFin() == null) {
            throw new IllegalArgumentException("Las fechas de inicio y fin son obligatorias");
        }
        if (reserva.getFechaInicio().isAfter(reserva.getFechaFin())
                || reserva.getFechaInicio().equals(reserva.getFechaFin())) {
            throw new IllegalArgumentException("La fecha de inicio debe ser anterior a la fecha de fin");
        }
    }

    private void verificarHabitacionExisteYDisponible(Long habitacionId) {
        if (habitacionService != null) {
            var habitacionOpt = habitacionService.buscarHabitacionPorId(habitacionId);
            if (habitacionOpt.isEmpty()) {
                throw new IllegalArgumentException("La habitación seleccionada no existe");
            }

            var habitacion = habitacionOpt.get();
            if (EstadoHabitacion.MANTENIMIENTO.getValor().equalsIgnoreCase(habitacion.getEstado())) {
                throw new IllegalArgumentException("La habitación está en mantenimiento y no puede ser reservada");
            }
        }
    }

    private void verificarDisponibilidadFechas(Long habitacionId, Reserva reserva, Long reservaId) {
        if (habitacionService != null && !habitacionService.estaDisponible(
                habitacionId, reserva.getFechaInicio(), reserva.getFechaFin())) {

            boolean existeConflicto = reservaRepository.existeReservaEnRangoFechas(
                    habitacionId, reserva.getFechaInicio(), reserva.getFechaFin(), reservaId);

            if (existeConflicto) {
                List<Reserva> reservasConflictivas = reservaRepository.findReservasConflictivas(
                        habitacionId, reserva.getFechaInicio(), reserva.getFechaFin(), reservaId);

                StringBuilder mensaje = new StringBuilder(
                        "La habitación ya está reservada en el rango de fechas seleccionado. ");
                if (!reservasConflictivas.isEmpty()) {
                    Reserva conflicto = reservasConflictivas.get(0);
                    mensaje.append("Ya existe una reserva del ").append(conflicto.getFechaInicio())
                            .append(" al ").append(conflicto.getFechaFin())
                            .append(" con estado: ").append(conflicto.getEstadoReserva());
                }
                throw new IllegalArgumentException(mensaje.toString());
            } else {
                throw new IllegalArgumentException("La habitación seleccionada no está disponible");
            }
        }
    }

    private void actualizarEstadoHabitacionSegunReserva(Reserva guardada) {
        if (habitacionService == null || guardada.getHabitacion() == null) {
            return;
        }

        Long habitacionId = guardada.getHabitacion().getId();
        LocalDate hoy = LocalDate.now();
        String estadoReserva = guardada.getEstadoReserva();

        if (EstadoReserva.ACTIVA.getValor().equalsIgnoreCase(estadoReserva) ||
                EstadoReserva.PENDIENTE.getValor().equalsIgnoreCase(estadoReserva)) {
            if (guardada.getFechaInicio() != null && !guardada.getFechaInicio().isAfter(hoy)) {
                habitacionService.actualizarEstadoHabitacion(habitacionId, EstadoHabitacion.OCUPADA.getValor());
            } else if (guardada.getFechaInicio() != null && guardada.getFechaInicio().isAfter(hoy)) {
                var habitacionOpt = habitacionService.buscarHabitacionPorId(habitacionId);
                if (habitacionOpt.isPresent() &&
                        !EstadoHabitacion.MANTENIMIENTO.getValor().equalsIgnoreCase(habitacionOpt.get().getEstado())) {
                    habitacionService.actualizarEstadoHabitacion(habitacionId, EstadoHabitacion.DISPONIBLE.getValor());
                }
            }
        } else if (EstadoReserva.FINALIZADA.getValor().equalsIgnoreCase(estadoReserva) ||
                EstadoReserva.CANCELADA.getValor().equalsIgnoreCase(estadoReserva)) {
            habitacionService.actualizarEstadoHabitacion(habitacionId, EstadoHabitacion.DISPONIBLE.getValor());
        }
    }

    private void registrarAuditoriaCreacionOActualizacion(Reserva guardada) {
        if (guardada.getId() != null && guardada.getCliente() != null) {
            auditoriaService.registrarAccion("CREACION_O_ACTUALIZACION_RESERVA",
                    "Reserva creada o actualizada (ID: " + guardada.getId() + ") para cliente "
                            + guardada.getCliente().getNombres(),
                    "Reserva", guardada.getId());
        }
    }

    private void enviarEmailConfirmacionSiEsNueva(boolean esNueva, Reserva guardada) {
        if (esNueva && emailService != null && guardada.getCliente() != null) {
            String email = guardada.getCliente().getEmail();
            if (email != null && !email.trim().isEmpty()) {
                emailService.enviarConfirmacionReserva(
                        email,
                        guardada.getCliente().getNombres(),
                        String.valueOf(guardada.getId()),
                        guardada.getFechaInicio() != null ? guardada.getFechaInicio().toString() : "",
                        guardada.getFechaFin() != null ? guardada.getFechaFin().toString() : "",
                        guardada.getHabitacion() != null ? guardada.getHabitacion().getNumero() : "",
                        guardada.getTotalPagar());
            }
        }
    }

    private void validarCancelacion(Reserva reserva, String userRole) {
        if ("ROLE_CLIENTE".equals(userRole)) {
            throw new IllegalStateException(
                    "Usted no puede cancelar su reserva. Acérquese a recepción para generar su cancelación de reserva.");
        }
        if (reserva.getPago() != null) {
            throw new IllegalStateException(
                    "No se puede cancelar una reserva que ya tiene pago. Use 'Finalizar' en su lugar.");
        }
    }

    private void liberarHabitacion(Reserva reserva) {
        if (habitacionService != null && reserva.getHabitacion() != null) {
            habitacionService.actualizarEstadoHabitacion(
                    reserva.getHabitacion().getId(),
                    EstadoHabitacion.DISPONIBLE.getValor());
        }
    }

    private void registrarAuditoriaCancelacion(Reserva reserva, String userRole) {
        if (reserva.getId() != null) {
            auditoriaService.registrarAccion("CANCELACION_RESERVA",
                    "Reserva cancelada por " + userRole + " (ID: " + reserva.getId() + ").",
                    "Reserva", reserva.getId());
        }
    }

    private Map<LocalDate, Double> inicializarMapaFechas(LocalDate inicio, LocalDate fin) {
        Map<LocalDate, Double> mapa = new HashMap<>();
        LocalDate fecha = inicio;
        while (!fecha.isAfter(fin)) {
            mapa.put(fecha, 0.0);
            fecha = fecha.plusDays(1);
        }
        return mapa;
    }

    private boolean estaEnRangoDeFechas(LocalDate fecha, LocalDate inicio, LocalDate fin) {
        return fecha != null && !fecha.isBefore(inicio) && !fecha.isAfter(fin);
    }

    private boolean esReservaContabilizable(Reserva r) {
        return EstadoReserva.FINALIZADA.getValor().equalsIgnoreCase(r.getEstadoReserva()) ||
                EstadoReserva.ACTIVA.getValor().equalsIgnoreCase(r.getEstadoReserva());
    }

    private double calcularTotalReserva(Reserva r) {
        double total = r.getTotalPagar() != null ? r.getTotalPagar() : 0.0;
        return total + r.calcularTotalServicios();
    }

    private List<Map<String, Object>> convertirMapaALista(Map<LocalDate, Double> mapa) {
        List<Map<String, Object>> resultado = new ArrayList<>();
        for (Map.Entry<LocalDate, Double> entry : mapa.entrySet()) {
            Map<String, Object> map = new HashMap<>();
            map.put("fecha", entry.getKey().toString());
            map.put("ingresos", entry.getValue());
            resultado.add(map);
        }
        resultado.sort((a, b) -> ((String) a.get("fecha")).compareTo((String) b.get("fecha")));
        return resultado;
    }

    private boolean hayOverlap(LocalDate inicio1, LocalDate fin1, LocalDate inicio2, LocalDate fin2) {
        return inicio1 != null && fin1 != null && !inicio1.isAfter(fin2) && !fin1.isBefore(inicio2);
    }

    private Map<LocalDate, Map<String, Integer>> inicializarMapaMovimientos(LocalDate inicio, LocalDate fin) {
        Map<LocalDate, Map<String, Integer>> mapa = new HashMap<>();
        LocalDate fecha = inicio;
        while (!fecha.isAfter(fin)) {
            Map<String, Integer> movimientos = new HashMap<>();
            movimientos.put("checkIns", 0);
            movimientos.put("checkOuts", 0);
            mapa.put(fecha, movimientos);
            fecha = fecha.plusDays(1);
        }
        return mapa;
    }

    private List<Map<String, Object>> convertirMovimientosALista(Map<LocalDate, Map<String, Integer>> mapa) {
        List<Map<String, Object>> resultado = new ArrayList<>();
        for (Map.Entry<LocalDate, Map<String, Integer>> entry : mapa.entrySet()) {
            Map<String, Object> map = new HashMap<>();
            map.put("fecha", entry.getKey().toString());
            map.put("checkIns", entry.getValue().get("checkIns"));
            map.put("checkOuts", entry.getValue().get("checkOuts"));
            resultado.add(map);
        }
        resultado.sort((a, b) -> ((String) a.get("fecha")).compareTo((String) b.get("fecha")));
        return resultado;
    }

    private void asignarServiciosAReserva(Reserva reserva, List<Long> servicioIds, List<String> opciones) {
        List<Long> idsNormalizados = servicioIds != null ? servicioIds : Collections.emptyList();
        Set<Servicio> serviciosSeleccionados = idsNormalizados.isEmpty()
                ? new HashSet<>()
                : new HashSet<>(servicioRepository.findAllById(idsNormalizados));

        reserva.getServicios().clear();
        reserva.getServicios().addAll(serviciosSeleccionados);

        if (opciones != null && !opciones.isEmpty()) {
            int i = 0;
            for (Servicio servicio : serviciosSeleccionados) {
                if (i < opciones.size()) {
                    String opcion = opciones.get(i);
                    if (opcion != null && !opcion.isEmpty()) {
                        reserva.getOpcionesServicios().put(servicio.getNombre(), opcion);
                    }
                    i++;
                }
            }
        }
    }

    private boolean perteneceACliente(Reserva r, String username) {
        return r.getCliente() != null &&
                (username.equals(r.getCliente().getDni()) ||
                        (r.getCliente().getUsuario() != null &&
                                username.equals(r.getCliente().getUsuario().getUsername())));
    }

    private boolean esReservaActiva(Reserva r) {
        return EstadoReserva.ACTIVA.getValor().equalsIgnoreCase(r.getEstadoReserva()) ||
                EstadoReserva.PENDIENTE.getValor().equalsIgnoreCase(r.getEstadoReserva());
    }

    private void validarCheckIn(Reserva reserva) {
        if (!EstadoReserva.PENDIENTE.getValor().equalsIgnoreCase(reserva.getEstadoReserva())) {
            throw new IllegalStateException("Solo se puede realizar check-in de reservas pendientes");
        }
        LocalDate hoy = LocalDate.now();
        if (reserva.getFechaInicio().isAfter(hoy)) {
            throw new IllegalStateException("No se puede realizar check-in antes de la fecha de inicio de la reserva");
        }
    }

    private void actualizarHabitacionAOcupada(Reserva reserva) {
        if (habitacionService != null && reserva.getHabitacion() != null) {
            habitacionService.actualizarEstadoHabitacion(
                    reserva.getHabitacion().getId(),
                    EstadoHabitacion.OCUPADA.getValor());
        }
    }

    private void enviarEmailCheckIn(Reserva actualizada) {
        if (emailService != null && actualizada.getCliente() != null) {
            String email = actualizada.getCliente().getEmail();
            if (email != null && !email.trim().isEmpty()) {
                emailService.enviarNotificacionCheckIn(
                        email,
                        actualizada.getCliente().getNombres(),
                        String.valueOf(actualizada.getId()));
            }
        }
    }

    private void validarCheckOut(Reserva reserva) {
        if (!EstadoReserva.ACTIVA.getValor().equalsIgnoreCase(reserva.getEstadoReserva())) {
            throw new IllegalStateException("Solo se puede realizar check-out de reservas activas");
        }
    }

    private void actualizarHabitacionADisponible(Reserva reserva) {
        if (habitacionService != null && reserva.getHabitacion() != null) {
            habitacionService.actualizarEstadoHabitacion(
                    reserva.getHabitacion().getId(),
                    EstadoHabitacion.DISPONIBLE.getValor());
        }
    }

    private void enviarEmailCheckOut(Reserva actualizada, LocalDate hoy) {
        if (emailService != null && actualizada.getCliente() != null) {
            String email = actualizada.getCliente().getEmail();
            if (email != null && !email.trim().isEmpty()) {
                String nombre = actualizada.getCliente().getNombres();
                String numeroReserva = String.valueOf(actualizada.getId());

                emailService.enviarNotificacionCheckOut(email, nombre, numeroReserva);
                emailService.enviarEncuestaPostEstadia(email, nombre, numeroReserva, hoy.toString());
            }
        }
    }

    private double calcularMontoTotalParaDescuento(Reserva reserva) {
        double montoBase = reserva.getTotalPagar() != null ? reserva.getTotalPagar() : 0.0;
        double montoServicios = reserva.calcularTotalServicios();
        return montoBase + montoServicios;
    }

    private Descuento validarYObtenerDescuento(String codigoDescuento, double montoTotal) {
        Optional<Descuento> descuentoOpt = descuentoService.validarYBuscarDescuento(codigoDescuento, montoTotal);
        if (descuentoOpt.isEmpty()) {
            throw new IllegalArgumentException("Código de descuento inválido, expirado o no aplicable");
        }
        return descuentoOpt.get();
    }
}

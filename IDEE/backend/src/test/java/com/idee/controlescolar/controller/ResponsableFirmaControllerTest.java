package com.idee.controlescolar.controller;

import com.idee.controlescolar.model.ResponsableFirma;
import com.idee.controlescolar.repository.ResponsableFirmaRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Tests unitarios — ResponsableFirmaController
 * ============================================
 * Componente probado : ResponsableFirmaController
 * Dependencias mock  : ResponsableFirmaRepository
 * Framework          : JUnit 5 + Mockito + AssertJ
 *
 * Nota RF-07 (seguridad): el controlador NO tiene @PreAuthorize en sus endpoints.
 * El test de que ADMIN no puede crear responsables está en ConfiguracionSepSecurityTest,
 * que carga el contexto de Spring Security completo.
 *
 * Ejecutar con: mvn test -Dtest=ResponsableFirmaControllerTest
 */
@ExtendWith(MockitoExtension.class)
class ResponsableFirmaControllerTest {

    @Mock
    private ResponsableFirmaRepository responsableRepository;

    @InjectMocks
    private ResponsableFirmaController controller;

    // =========================================================
    // RF-01 — GET / retorna lista de responsables activos ordenada
    // =========================================================
    @Test
    @DisplayName("RF-01 GET /responsables-firma — retorna HTTP 200 con lista ordenada por ordenFirma")
    void listarResponsables_retornaListaOrdenadaYHttp200() {
        ResponsableFirma r1 = mock(ResponsableFirma.class);
        ResponsableFirma r2 = mock(ResponsableFirma.class);
        when(responsableRepository.findByActivoTrueOrderByOrdenFirmaAsc()).thenReturn(List.of(r1, r2));

        ResponseEntity<List<ResponsableFirma>> resp = controller.listarResponsables();

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody()).hasSize(2).containsExactly(r1, r2);
    }

    // =========================================================
    // RF-01b — GET / BD vacía → lista vacía, no 404
    // =========================================================
    @Test
    @DisplayName("RF-01b GET /responsables-firma — sin responsables retorna HTTP 200 con lista vacía")
    void listarResponsables_sinRegistros_retornaListaVaciaYHttp200() {
        when(responsableRepository.findByActivoTrueOrderByOrdenFirmaAsc()).thenReturn(List.of());

        ResponseEntity<List<ResponsableFirma>> resp = controller.listarResponsables();

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody()).isNotNull().isEmpty();
    }

    // =========================================================
    // RF-02 — POST / CURP nuevo → crea responsable nuevo con activo=true
    // =========================================================
    @Test
    @DisplayName("RF-02 POST /responsables-firma — CURP nuevo crea responsable con activo=true y HTTP 200")
    void crearResponsable_curpNuevo_creaRegistroYRetorna200() {
        ResponsableFirma nuevo = new ResponsableFirma();
        nuevo.setNombre("Ana");
        nuevo.setPrimerApellido("Martínez");
        nuevo.setCurp("MAAA900101MDFRRSA01");
        nuevo.setIdCargo("01");
        nuevo.setCargo("Director General");
        nuevo.setOrdenFirma(1);

        ResponsableFirma guardado = new ResponsableFirma();
        guardado.setId(10L);
        guardado.setActivo(true);

        when(responsableRepository.findByCurp("MAAA900101MDFRRSA01")).thenReturn(Optional.empty());
        when(responsableRepository.save(nuevo)).thenReturn(guardado);

        ResponseEntity<ResponsableFirma> resp = controller.crearResponsable(nuevo);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody()).isNotNull();
        assertThat(resp.getBody().getActivo()).isTrue();
        // El controlador debe setear activo=true antes de guardar
        verify(responsableRepository, times(1)).save(argThat(r -> r.getActivo() == Boolean.TRUE));
    }

    // =========================================================
    // RF-03 — POST / CURP ya existente (inactivo) → reactiva con datos nuevos
    // =========================================================
    @Test
    @DisplayName("RF-03 POST /responsables-firma — CURP existente reactiva el registro y actualiza datos")
    void crearResponsable_curpExistente_reactivaYActualizaDatos() {
        ResponsableFirma existente = new ResponsableFirma();
        existente.setId(5L);
        existente.setCurp("MAAA900101MDFRRSA01");
        existente.setNombre("Ana Viejo");
        existente.setActivo(false);

        ResponsableFirma solicitud = new ResponsableFirma();
        solicitud.setNombre("Ana Nuevo");
        solicitud.setPrimerApellido("Martínez");
        solicitud.setCurp("MAAA900101MDFRRSA01");
        solicitud.setIdCargo("02");
        solicitud.setCargo("Secretaria Académica");
        solicitud.setOrdenFirma(2);

        when(responsableRepository.findByCurp("MAAA900101MDFRRSA01")).thenReturn(Optional.of(existente));
        when(responsableRepository.save(existente)).thenReturn(existente);

        ResponseEntity<ResponsableFirma> resp = controller.crearResponsable(solicitud);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        // El registro existente debe haberse reactivado con los nuevos datos
        assertThat(existente.getActivo()).isTrue();
        assertThat(existente.getNombre()).isEqualTo("Ana Nuevo");
        assertThat(existente.getCargo()).isEqualTo("Secretaria Académica");
        // No debe haber creado un registro nuevo
        verify(responsableRepository, times(1)).save(existente);
    }

    // =========================================================
    // RF-04 — PUT /{id} id existente → actualiza y retorna 200
    // =========================================================
    @Test
    @DisplayName("RF-04 PUT /responsables-firma/{id} — ID existente actualiza y retorna HTTP 200")
    void actualizarResponsable_idExistente_retorna200YPersisteCambios() {
        ResponsableFirma existente = mock(ResponsableFirma.class);
        ResponsableFirma cambios = new ResponsableFirma();
        cambios.setCargo("Director Académico");

        when(responsableRepository.findById(1L)).thenReturn(Optional.of(existente));
        when(responsableRepository.save(any(ResponsableFirma.class))).thenReturn(cambios);

        ResponseEntity<?> resp = controller.actualizarResponsable(1L, cambios);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        // Se debe haber asignado el ID antes de guardar
        verify(responsableRepository, times(1)).save(argThat(r -> r.getId() != null && r.getId() == 1L));
    }

    // =========================================================
    // RF-05 — PUT /{id} id inexistente → 404
    // =========================================================
    @Test
    @DisplayName("RF-05 PUT /responsables-firma/9999 — ID inexistente retorna HTTP 404")
    void actualizarResponsable_idInexistente_retorna404() {
        when(responsableRepository.findById(9999L)).thenReturn(Optional.empty());

        ResponseEntity<?> resp = controller.actualizarResponsable(9999L, new ResponsableFirma());

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        verify(responsableRepository, never()).save(any());
    }

    // =========================================================
    // RF-06 — DELETE /{id} id existente → soft delete (activo=false), retorna 200
    // =========================================================
    @Test
    @DisplayName("RF-06 DELETE /responsables-firma/{id} — ID existente hace soft-delete (activo=false) y retorna HTTP 200")
    void eliminarResponsable_idExistente_softDeleteYRetorna200() {
        ResponsableFirma responsable = new ResponsableFirma();
        responsable.setId(3L);
        responsable.setActivo(true);

        when(responsableRepository.findById(3L)).thenReturn(Optional.of(responsable));
        when(responsableRepository.save(responsable)).thenReturn(responsable);

        ResponseEntity<?> resp = controller.eliminarResponsable(3L);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        // Verificar que el registro se marcó como inactivo (soft delete)
        assertThat(responsable.getActivo()).isFalse();
        verify(responsableRepository, times(1)).save(responsable);
        // Verificar que NO se hizo delete físico
        verify(responsableRepository, never()).delete(any());
        verify(responsableRepository, never()).deleteById(any());
    }

    // =========================================================
    // RF-06b — DELETE /{id} id inexistente → 404
    // =========================================================
    @Test
    @DisplayName("RF-06b DELETE /responsables-firma/9999 — ID inexistente retorna HTTP 404")
    void eliminarResponsable_idInexistente_retorna404() {
        when(responsableRepository.findById(9999L)).thenReturn(Optional.empty());

        ResponseEntity<?> resp = controller.eliminarResponsable(9999L);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        verify(responsableRepository, never()).save(any());
    }
}

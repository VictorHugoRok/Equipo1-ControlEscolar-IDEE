package com.idee.controlescolar.controller;

import com.idee.controlescolar.model.ProgramaEducativo;
import com.idee.controlescolar.repository.ProgramaEducativoRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Pruebas unitarias del módulo de Programas Educativos
 * =====================================================
 * Componente probado : ProgramaEducativoController
 * Dependencias mock  : ProgramaEducativoRepository
 * Framework          : JUnit 5 + Mockito + AssertJ
 *
 * Las entidades ProgramaEducativo se crean como mocks de Mockito.
 * Los stubs sobre el repositorio usan any() para no depender de los
 * metodos generados por Lombok (@Data) en tiempo de compilacion del IDE.
 *
 * Ejecutar con: mvn test -Dtest=ProgramaEducativoControllerTest
 */
@ExtendWith(MockitoExtension.class)
class ProgramaEducativoControllerTest {

    @Mock
    private ProgramaEducativoRepository repo;

    @InjectMocks
    private ProgramaEducativoController controller;

    // =========================================================
    // PT-001 — Listar todos los programas (lista con registros)
    // =========================================================
    @Test
    @DisplayName("PT-001 GET /api/programas-educativos — lista con registros retorna HTTP 200")
    void listarTodos_retornaListaYHttp200() {
        ProgramaEducativo p = mock(ProgramaEducativo.class);
        when(repo.findAll()).thenReturn(List.of(p));

        ResponseEntity<List<ProgramaEducativo>> resp = controller.obtenerTodos();

        List<ProgramaEducativo> body = resp.getBody();
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(body).isNotNull().hasSize(1);
        assertThat(body).element(0).isSameAs(p);
    }

    // =========================================================
    // PT-002 — Listar cuando la base de datos está vacía
    // =========================================================
    @Test
    @DisplayName("PT-002 GET /api/programas-educativos — BD vacía retorna lista vacía y HTTP 200")
    void listarTodos_sinRegistros_retornaListaVacia() {
        when(repo.findAll()).thenReturn(List.of());

        ResponseEntity<List<ProgramaEducativo>> resp = controller.obtenerTodos();

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody()).isNotNull().isEmpty();
    }

    // =========================================================
    // PT-003 — Crear programa nuevo con datos válidos
    // =========================================================
    @Test
    @DisplayName("PT-003 POST /api/programas-educativos — datos válidos retorna HTTP 201 y objeto creado")
    void crearPrograma_datosValidos_retornaHttp201() {
        ProgramaEducativo nuevo = mock(ProgramaEducativo.class);
        // El controlador llama existsByClave(programa.getClave()).
        // Al ser un mock, getClave() devuelve null por defecto; stubamos
        // existsByClave con any() para no depender del valor concreto.
        when(repo.existsByClave(any())).thenReturn(false);
        when(repo.save(nuevo)).thenReturn(nuevo);

        ResponseEntity<?> resp = controller.crear(nuevo);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(resp.getBody()).isSameAs(nuevo);
        verify(repo, times(1)).save(nuevo);
    }

    // =========================================================
    // PT-004 — Crear programa con clave duplicada
    // =========================================================
    @Test
    @DisplayName("PT-004 POST /api/programas-educativos — clave duplicada retorna HTTP 409 CONFLICT")
    void crearPrograma_claveDuplicada_retornaHttp409() {
        ProgramaEducativo duplicado = mock(ProgramaEducativo.class);
        when(repo.existsByClave(any())).thenReturn(true);

        ResponseEntity<?> resp = controller.crear(duplicado);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(resp.getBody()).isNotNull();
        verify(repo, never()).save(any());
    }

    // =========================================================
    // PT-005 — Obtener programa por ID existente
    // =========================================================
    @Test
    @DisplayName("PT-005 GET /api/programas-educativos/{id} — ID existente retorna HTTP 200 y el programa")
    void obtenerPorId_idExistente_retornaHttp200() {
        ProgramaEducativo p = mock(ProgramaEducativo.class);
        when(repo.findById(1L)).thenReturn(Optional.of(p));

        ResponseEntity<?> resp = controller.obtenerPorId(1L);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody()).isSameAs(p);
    }

    // =========================================================
    // PT-006 — Obtener programa por ID inexistente
    // =========================================================
    @Test
    @DisplayName("PT-006 GET /api/programas-educativos/{id} — ID inexistente retorna HTTP 404")
    void obtenerPorId_idInexistente_retornaHttp404() {
        when(repo.findById(999L)).thenReturn(Optional.empty());

        ResponseEntity<?> resp = controller.obtenerPorId(999L);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    // =========================================================
    // PT-007 — Obtener programa por clave existente
    // =========================================================
    @Test
    @DisplayName("PT-007 GET /api/programas-educativos/clave/{clave} — clave válida retorna HTTP 200")
    void obtenerPorClave_claveExistente_retornaHttp200() {
        ProgramaEducativo p = mock(ProgramaEducativo.class);
        when(repo.findByClave("LIS-SIS")).thenReturn(Optional.of(p));

        ResponseEntity<?> resp = controller.obtenerPorClave("LIS-SIS");

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody()).isSameAs(p);
    }

    // =========================================================
    // PT-008 — Obtener programa por clave inexistente
    // =========================================================
    @Test
    @DisplayName("PT-008 GET /api/programas-educativos/clave/{clave} — clave inexistente retorna HTTP 404")
    void obtenerPorClave_claveInexistente_retornaHttp404() {
        when(repo.findByClave("XXX-000")).thenReturn(Optional.empty());

        ResponseEntity<?> resp = controller.obtenerPorClave("XXX-000");

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    // =========================================================
    // PT-009 — Actualizar programa existente con datos válidos
    // =========================================================
    @Test
    @DisplayName("PT-009 PUT /api/programas-educativos/{id} — programa existente retorna HTTP 200 y persiste cambios")
    void actualizarPrograma_idExistente_retornaHttp200() {
        ProgramaEducativo existente = mock(ProgramaEducativo.class);
        ProgramaEducativo cambios   = mock(ProgramaEducativo.class);

        when(repo.findById(1L)).thenReturn(Optional.of(existente));
        when(repo.save(existente)).thenReturn(existente);

        ResponseEntity<?> resp = controller.actualizar(1L, cambios);

        // Verifica HTTP 200 y que el controlador persistio la entidad
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody()).isSameAs(existente);
        verify(repo, times(1)).save(existente);
    }

    // =========================================================
    // PT-010 — Actualizar programa inexistente
    // =========================================================
    @Test
    @DisplayName("PT-010 PUT /api/programas-educativos/{id} — ID inexistente retorna HTTP 404")
    void actualizarPrograma_idInexistente_retornaHttp404() {
        when(repo.findById(99L)).thenReturn(Optional.empty());

        ResponseEntity<?> resp = controller.actualizar(99L, mock(ProgramaEducativo.class));

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        verify(repo, never()).save(any());
    }

    // =========================================================
    // PT-011 — Eliminar programa existente
    // =========================================================
    @Test
    @DisplayName("PT-011 DELETE /api/programas-educativos/{id} — programa existente retorna HTTP 200")
    void eliminarPrograma_idExistente_retornaHttp200() {
        ProgramaEducativo p = mock(ProgramaEducativo.class);
        when(repo.findById(1L)).thenReturn(Optional.of(p));

        ResponseEntity<?> resp = controller.eliminar(1L);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(repo, times(1)).delete(p);
    }

    // =========================================================
    // PT-012 — Eliminar programa inexistente
    // =========================================================
    @Test
    @DisplayName("PT-012 DELETE /api/programas-educativos/{id} — ID inexistente retorna HTTP 404")
    void eliminarPrograma_idInexistente_retornaHttp404() {
        when(repo.findById(50L)).thenReturn(Optional.empty());

        ResponseEntity<?> resp = controller.eliminar(50L);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        verify(repo, never()).delete(any());
    }
}

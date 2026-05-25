package com.idee.controlescolar.service;

import com.idee.controlescolar.repository.ConfiguracionInstitucionalRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Servicio para operaciones de arranque de configuración institucional.
 * Garantiza que solo exista una configuración activa.
 */
@Service
@RequiredArgsConstructor
public class ConfiguracionInstitucionalStartupService {

    private static final Logger logger = LoggerFactory.getLogger(ConfiguracionInstitucionalStartupService.class);

    private final ConfiguracionInstitucionalRepository configRepository;

    /**
     * Corrige duplicados: si hay más de una configuración activa, deja solo la más reciente.
     */
    @Transactional
    public void corregirDuplicadosActivos() {
        if (configRepository.countByActivoTrue() > 1) {
            logger.warn("Detectadas múltiples configuraciones activas. Corrigiendo: solo una puede estar activa.");
            configRepository.findFirstByActivoTrueOrderByIdDesc().ifPresent(configAMantener -> {
                configRepository.desactivarTodas();
                configAMantener.setActivo(true);
                configRepository.save(configAMantener);
                logger.info("✓ Configuración corregida: activa solo la de ID {}", configAMantener.getId());
            });
        }
    }
}

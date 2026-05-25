package com.idee.controlescolar.util;

/**
 * Validación mínima de RFC (persona física/moral): vacío permitido; si hay valor, 12 o 13 caracteres; máximo 13.
 */
public final class ValidacionRfc {

    private ValidacionRfc() {
    }

    /**
     * @throws IllegalArgumentException si el valor no es vacío y no cumple longitud 12–13
     */
    public static void validarFormatoOpcional(String rfc) {
        if (rfc == null || rfc.isBlank()) {
            return;
        }
        String r = rfc.trim();
        if (r.length() > 13 || r.length() < 12) {
            throw new IllegalArgumentException("El RFC debe tener 12 o 13 caracteres, o dejarse vacío");
        }
    }
}

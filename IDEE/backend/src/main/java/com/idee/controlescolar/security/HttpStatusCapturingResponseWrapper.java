package com.idee.controlescolar.security;

import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletResponseWrapper;

import java.io.IOException;

/**
 * Delega en la respuesta real pero conserva el último código HTTP establecido
 * (para contar intentos fallidos de login tras procesar el controlador).
 */
public class HttpStatusCapturingResponseWrapper extends HttpServletResponseWrapper {

    private int httpStatus = HttpServletResponse.SC_OK;

    public HttpStatusCapturingResponseWrapper(HttpServletResponse response) {
        super(response);
    }

    @Override
    public void setStatus(int sc) {
        this.httpStatus = sc;
        super.setStatus(sc);
    }

    @Override
    public void sendError(int sc) throws IOException {
        this.httpStatus = sc;
        super.sendError(sc);
    }

    @Override
    public void sendError(int sc, String msg) throws IOException {
        this.httpStatus = sc;
        super.sendError(sc, msg);
    }

    @Override
    public void sendRedirect(String location) throws IOException {
        this.httpStatus = HttpServletResponse.SC_FOUND;
        super.sendRedirect(location);
    }

    public int getCapturedStatus() {
        return httpStatus;
    }
}

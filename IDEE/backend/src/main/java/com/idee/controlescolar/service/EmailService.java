package com.idee.controlescolar.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class EmailService {

    @Autowired(required = false)
    private JavaMailSender mailSender;

    @Value("${spring.mail.host:}")
    private String mailHost;

    @Value("${spring.mail.username:}")
    private String mailFrom;

    public void enviarCorreoInscripcion(String destinatario, String nombreAlumno) {
        if (destinatario == null || destinatario.isBlank()) {
            return;
        }

        if (mailSender == null || mailHost == null || mailHost.isBlank()) {
            log.warn("Correo no enviado (sin configuracion SMTP). Destinatario: {}", destinatario);
            return;
        }

        String asunto = "Continuar proceso de inscripcion";
        String nombre = nombreAlumno == null ? "estudiante" : nombreAlumno;
        String mensaje = "Hola " + nombre + ",\n\n"
            + "Tu registro fue realizado correctamente. "
            + "Por favor continua el proceso de inscripcion entregando tus documentos en la escuela.\n\n"
            + "Atentamente,\nIDEE Control Escolar";

        SimpleMailMessage mail = new SimpleMailMessage();
        mail.setTo(destinatario);
        mail.setSubject(asunto);
        mail.setText(mensaje);

        if (mailFrom != null && !mailFrom.isBlank()) {
            mail.setFrom(mailFrom);
        }

        mailSender.send(mail);
        log.info("Correo de inscripcion enviado a {}", destinatario);
    }
}

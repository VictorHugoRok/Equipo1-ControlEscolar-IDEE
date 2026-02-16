package com.idee.controlescolar.service;

import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.asn1.ASN1InputStream;
import org.bouncycastle.asn1.ASN1Sequence;
import org.bouncycastle.asn1.pkcs.PrivateKeyInfo;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.openssl.PEMKeyPair;
import org.bouncycastle.openssl.PEMParser;
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter;
import org.bouncycastle.openssl.jcajce.JceOpenSSLPKCS8DecryptorProviderBuilder;
import org.bouncycastle.operator.InputDecryptorProvider;
import org.bouncycastle.pkcs.PKCS8EncryptedPrivateKeyInfo;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.*;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;

/**
 * Servicio para gestionar la firma digital de títulos electrónicos
 * usando certificados SAT (.cer y .key).
 */
@Service
@Slf4j
public class FirmaDigitalService {

    static {
        // Registrar BouncyCastle como proveedor de seguridad
        Security.addProvider(new BouncyCastleProvider());
    }

    private static final String SIGNATURE_ALGORITHM = "SHA256withRSA";
    private static final String KEY_ALGORITHM = "RSA";

    /**
     * Firma digitalmente una cadena de texto usando la llave privada.
     * Algoritmo: SHA256withRSA según estándar SEP.
     *
     * @param cadenaOriginal Cadena original a firmar
     * @param llavePrivadaPath Ruta al archivo .key
     * @param password Contraseña de la llave privada
     * @return Sello digital en Base64
     */
    public String firmarCadena(String cadenaOriginal, String llavePrivadaPath, String password) {
        try {
            PrivateKey llavePrivada = cargarLlavePrivada(llavePrivadaPath, password);
            return generarSello(cadenaOriginal, llavePrivada);
        } catch (Exception e) {
            log.error("Error al firmar cadena: {}", e.getMessage(), e);
            throw new RuntimeException("Error al generar firma digital: " + e.getMessage(), e);
        }
    }

    /**
     * Genera el sello digital de la cadena original según normativa SEP.
     *
     * Proceso según DOF 13 abril 2018, sección 4.3:
     * 1. SHA-256: Aplica función hash a la cadena original
     * 2. RSAPrivateEncrypt: Encripta el hash con la llave privada
     * 3. Base64: Convierte el resultado a formato Base64
     *
     * @param cadenaOriginal Cadena a firmar (debe estar en UTF-8)
     * @param llavePrivada Llave privada para firmar
     * @return Sello en Base64
     */
    public String generarSello(String cadenaOriginal, PrivateKey llavePrivada) {
        try {
            // PASO 1: Obtener bytes de la cadena original en UTF-8
            // Según regla general #5: UTF-8 (en Java, utf-8 JAVA)
            byte[] cadenaBytes = cadenaOriginal.getBytes(StandardCharsets.UTF_8);

            log.debug("Cadena original (UTF-8): {} caracteres, {} bytes",
                     cadenaOriginal.length(), cadenaBytes.length);

            // PASO 2 y 3: SHA-256 + RSAPrivateEncrypt
            // El algoritmo SHA256withRSA hace exactamente lo especificado en la norma:
            // - Aplica SHA-256 (función hash de 256 bits / 32 bytes)
            // - Encripta el resultado con RSA usando la llave privada
            Signature signature = Signature.getInstance(SIGNATURE_ALGORITHM, "BC");
            signature.initSign(llavePrivada);
            signature.update(cadenaBytes);

            // Generar la firma digital (equivalente a RSAPrivateEncrypt del hash)
            byte[] firmaBinaria = signature.sign();

            log.debug("Firma digital generada: {} bytes", firmaBinaria.length);

            // PASO 4: Convertir a Base64 según especificaciones del estándar
            String selloBase64 = Base64.getEncoder().encodeToString(firmaBinaria);

            log.info("Sello digital generado exitosamente (longitud Base64: {} caracteres)",
                    selloBase64.length());

            return selloBase64;

        } catch (Exception e) {
            log.error("Error al generar sello digital: {}", e.getMessage(), e);
            throw new RuntimeException("Error al generar sello: " + e.getMessage(), e);
        }
    }

    /**
     * Carga un certificado digital desde un archivo .cer
     *
     * @param certificadoPath Ruta al archivo .cer
     * @return Certificado X509
     */
    public X509Certificate cargarCertificado(String certificadoPath) {
        try (FileInputStream fis = new FileInputStream(certificadoPath)) {
            CertificateFactory cf = CertificateFactory.getInstance("X.509");
            X509Certificate cert = (X509Certificate) cf.generateCertificate(fis);

            log.info("Certificado cargado: {}", cert.getSubjectX500Principal());
            return cert;

        } catch (IOException | CertificateException e) {
            log.error("Error al cargar certificado desde {}: {}", certificadoPath, e.getMessage());
            throw new RuntimeException("Error al cargar certificado: " + e.getMessage(), e);
        }
    }

    /**
     * Carga la llave privada desde un archivo .key
     *
     * @param llavePrivadaPath Ruta al archivo .key
     * @param password Contraseña de la llave (puede ser null si no tiene)
     * @return Llave privada
     */
    public PrivateKey cargarLlavePrivada(String llavePrivadaPath, String password) {
        try {
            // Leer el archivo .key
            byte[] keyBytes = Files.readAllBytes(Paths.get(llavePrivadaPath));

            // Si la llave está encriptada con contraseña, primero desencriptar
            // Nota: Este es un ejemplo básico. Las llaves SAT pueden requerir
            // procesamiento adicional dependiendo del formato

            // Crear la especificación de la llave privada
            PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(keyBytes);

            // Generar la llave privada
            KeyFactory keyFactory = KeyFactory.getInstance(KEY_ALGORITHM);
            PrivateKey privateKey = keyFactory.generatePrivate(keySpec);

            log.info("Llave privada cargada exitosamente");
            return privateKey;

        } catch (Exception e) {
            log.error("Error al cargar llave privada desde {}: {}", llavePrivadaPath, e.getMessage());
            throw new RuntimeException("Error al cargar llave privada: " + e.getMessage(), e);
        }
    }

    /**
     * Convierte un certificado a formato Base64 (para almacenar en BD).
     *
     * @param certificado Certificado X509
     * @return String en Base64
     */
    public String certificadoABase64(X509Certificate certificado) {
        try {
            byte[] encoded = certificado.getEncoded();
            return Base64.getEncoder().encodeToString(encoded);
        } catch (CertificateException e) {
            log.error("Error al convertir certificado a Base64: {}", e.getMessage());
            throw new RuntimeException("Error al procesar certificado: " + e.getMessage(), e);
        }
    }

    /**
     * Obtiene el número de serie del certificado.
     *
     * @param certificado Certificado X509
     * @return Número de serie como String
     */
    public String obtenerNumeroCertificado(X509Certificate certificado) {
        return certificado.getSerialNumber().toString();
    }

    /**
     * Valida un sello digital contra la cadena original según normativa SEP.
     *
     * Proceso inverso de generación (RSAPublicDecrypt):
     * 1. Decodifica el sello desde Base64
     * 2. Usa la llave pública del certificado para desencriptar
     * 3. Compara con el hash SHA-256 de la cadena original
     *
     * @param sello Sello digital en Base64
     * @param cadenaOriginal Cadena original que fue firmada (en UTF-8)
     * @param certificado Certificado con la llave pública
     * @return true si el sello es válido
     */
    public boolean validarSello(String sello, String cadenaOriginal, X509Certificate certificado) {
        try {
            // Obtener la llave pública del certificado
            PublicKey llavePublica = certificado.getPublicKey();

            // Decodificar el sello desde Base64
            byte[] selloBinario = Base64.getDecoder().decode(sello);

            // Convertir cadena original a bytes UTF-8 (mismo encoding que al firmar)
            byte[] cadenaBytes = cadenaOriginal.getBytes(StandardCharsets.UTF_8);

            // Crear verificador de firma con SHA256withRSA
            Signature signature = Signature.getInstance(SIGNATURE_ALGORITHM, "BC");
            signature.initVerify(llavePublica);
            signature.update(cadenaBytes);

            // Verificar la firma (RSAPublicDecrypt)
            boolean esValido = signature.verify(selloBinario);

            log.info("Validación de sello: {}", esValido ? "VÁLIDO" : "INVÁLIDO");
            return esValido;

        } catch (Exception e) {
            log.error("Error al validar sello: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Verifica si un certificado está vigente.
     *
     * @param certificado Certificado a verificar
     * @return true si está vigente
     */
    public boolean esCertificadoVigente(X509Certificate certificado) {
        try {
            certificado.checkValidity();
            return true;
        } catch (Exception e) {
            log.warn("Certificado no vigente: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Encripta una contraseña para almacenamiento seguro.
     *
     * @param password Contraseña en texto plano
     * @return Contraseña encriptada
     */
    public String encriptarPassword(String password) {
        // Implementación básica - en producción usar BCrypt o similar
        return Base64.getEncoder().encodeToString(password.getBytes());
    }

    /**
     * Desencripta una contraseña almacenada.
     *
     * @param passwordEncriptado Contraseña encriptada
     * @return Contraseña en texto plano
     */
    public String desencriptarPassword(String passwordEncriptado) {
        // Implementación básica - en producción usar BCrypt o similar
        byte[] decoded = Base64.getDecoder().decode(passwordEncriptado);
        return new String(decoded);
    }

    // ==================== MÉTODOS PARA TRABAJAR CON DATOS DE BD ====================

    /**
     * Genera el sello digital usando datos almacenados en BD (byte arrays).
     * Este método es el que se usa para generar el atributo "sello" del estándar DOF.
     *
     * @param cadenaOriginal Cadena original según estándar DOF
     * @param llavePrivadaData Contenido del archivo .key (byte[])
     * @param password Contraseña de la llave privada
     * @return Sello digital en formato Base64
     */
    public String generarSelloDesdeBytes(String cadenaOriginal, byte[] llavePrivadaData, String password)
            throws Exception {
        log.info("Generando sello digital desde bytes para cadena de {} caracteres", cadenaOriginal.length());

        // Cargar la llave privada desde bytes
        PrivateKey privateKey = cargarLlavePrivadaDesdeBytes(llavePrivadaData, password);

        // Generar el sello usando el método existente
        return generarSello(cadenaOriginal, privateKey);
    }

    /**
     * Convierte el certificado almacenado en BD a Base64.
     * Este método genera el atributo "certificadoResponsable" del estándar DOF.
     *
     * @param certificadoData Contenido del archivo .cer (byte[])
     * @return Certificado en formato Base64
     */
    public String obtenerCertificadoBase64DesdeBytes(byte[] certificadoData) {
        log.info("Convirtiendo certificado desde bytes a Base64");
        return Base64.getEncoder().encodeToString(certificadoData);
    }

    /**
     * Extrae el número de serie del certificado almacenado en BD.
     * Este método genera el atributo "noCertificadoResponsable" del estándar DOF.
     *
     * @param certificadoData Contenido del archivo .cer (byte[])
     * @return Número de serie del certificado en hexadecimal (20 dígitos)
     */
    public String extraerNumeroCertificadoDesdeBytes(byte[] certificadoData) throws Exception {
        log.info("Extrayendo número de certificado desde bytes");

        X509Certificate cert = cargarCertificadoDesdeBytes(certificadoData);
        String numeroSerie = cert.getSerialNumber().toString(16).toUpperCase();

        // Asegurar formato de 20 dígitos hexadecimales (estándar SAT)
        while (numeroSerie.length() < 20) {
            numeroSerie = "0" + numeroSerie;
        }

        log.info("Número de certificado extraído: {}", numeroSerie);
        return numeroSerie;
    }

    /**
     * Carga un certificado X.509 desde byte array.
     */
    public X509Certificate cargarCertificadoDesdeBytes(byte[] certificadoData) throws Exception {
        CertificateFactory cf = CertificateFactory.getInstance("X.509");
        ByteArrayInputStream bais = new ByteArrayInputStream(certificadoData);
        X509Certificate cert = (X509Certificate) cf.generateCertificate(bais);

        log.info("Certificado cargado desde bytes: {}", cert.getSubjectX500Principal());
        return cert;
    }

    /**
     * Carga una llave privada desde byte array con soporte AUTOMÁTICO para certificados SAT.
     * Soporta formatos: PKCS#8 PEM, PKCS#8 DER (encriptado), PKCS#1, y conversión automática DER→PEM.
     *
     * MEJORA: Ahora detecta y convierte automáticamente archivos .key del SAT en formato DER encriptado.
     */
    public PrivateKey cargarLlavePrivadaDesdeBytes(byte[] llavePrivadaData, String password) throws Exception {
        log.info("Cargando llave privada desde bytes (tamaño: {} bytes)", llavePrivadaData.length);

        // PASO 1: Intentar cargar como PEM (formato texto)
        try {
            InputStreamReader isr = new InputStreamReader(
                new ByteArrayInputStream(llavePrivadaData),
                StandardCharsets.UTF_8
            );
            PEMParser pemParser = new PEMParser(isr);
            Object objeto = pemParser.readObject();
            pemParser.close();

            JcaPEMKeyConverter converter = new JcaPEMKeyConverter().setProvider("BC");

            if (objeto instanceof PKCS8EncryptedPrivateKeyInfo) {
                // Llave privada encriptada en formato PEM (lo ideal)
                log.info("✓ Detectado: PKCS8 Encriptado PEM");
                PKCS8EncryptedPrivateKeyInfo encryptedInfo = (PKCS8EncryptedPrivateKeyInfo) objeto;

                InputDecryptorProvider decryptorProvider = new JceOpenSSLPKCS8DecryptorProviderBuilder()
                    .setProvider("BC")
                    .build(password.toCharArray());

                PrivateKeyInfo privateKeyInfo = encryptedInfo.decryptPrivateKeyInfo(decryptorProvider);
                return converter.getPrivateKey(privateKeyInfo);

            } else if (objeto instanceof PrivateKeyInfo) {
                // Llave privada sin encriptar
                log.info("✓ Detectado: PKCS8 sin encriptar PEM");
                return converter.getPrivateKey((PrivateKeyInfo) objeto);

            } else if (objeto instanceof PEMKeyPair) {
                // Formato PKCS#1 (RSA tradicional)
                log.info("✓ Detectado: PKCS1 (PEMKeyPair)");
                PEMKeyPair keyPair = (PEMKeyPair) objeto;
                return converter.getPrivateKey(keyPair.getPrivateKeyInfo());
            }

        } catch (Exception e) {
            log.info("No es formato PEM, intentando formato DER SAT: {}", e.getMessage());
        }

        // PASO 2: Intentar cargar como DER encriptado (formato típico del SAT)
        // Los archivos .key del SAT vienen en formato PKCS#8 DER encriptado con password
        try {
            log.info("Intentando cargar como PKCS8 DER encriptado (formato SAT)...");

            // Leer la estructura PKCS#8 encriptada desde DER
            PKCS8EncryptedPrivateKeyInfo encryptedInfo = new PKCS8EncryptedPrivateKeyInfo(llavePrivadaData);

            // Crear decryptor con la contraseña
            InputDecryptorProvider decryptorProvider = new JceOpenSSLPKCS8DecryptorProviderBuilder()
                .setProvider("BC")
                .build(password.toCharArray());

            // Desencriptar y obtener la información de la llave privada
            PrivateKeyInfo privateKeyInfo = encryptedInfo.decryptPrivateKeyInfo(decryptorProvider);

            // Convertir a PrivateKey de Java
            JcaPEMKeyConverter converter = new JcaPEMKeyConverter().setProvider("BC");
            PrivateKey privateKey = converter.getPrivateKey(privateKeyInfo);

            log.info("✓ Llave privada cargada exitosamente desde formato DER encriptado (SAT)");
            return privateKey;

        } catch (Exception e) {
            log.warn("No se pudo cargar como PKCS8 DER encriptado: {}", e.getMessage());
        }

        // PASO 3: Intentar cargar como DER sin encriptar (por si acaso)
        try {
            log.info("Intentando cargar como DER sin encriptar...");
            ASN1InputStream asn1 = new ASN1InputStream(new ByteArrayInputStream(llavePrivadaData));
            ASN1Sequence seq = (ASN1Sequence) asn1.readObject();
            asn1.close();

            PrivateKeyInfo privateKeyInfo = PrivateKeyInfo.getInstance(seq);
            JcaPEMKeyConverter converter = new JcaPEMKeyConverter().setProvider("BC");
            PrivateKey privateKey = converter.getPrivateKey(privateKeyInfo);

            log.info("✓ Llave privada cargada desde DER sin encriptar");
            return privateKey;

        } catch (Exception e) {
            log.error("Tampoco se pudo cargar como DER sin encriptar: {}", e.getMessage());
        }

        // Si llegamos aquí, ningún formato funcionó
        throw new Exception("No se pudo cargar la llave privada. Verifique el formato y la contraseña.");
    }

    /**
     * Valida que un certificado y llave privada (desde bytes) sean un par válido.
     *
     * @param certificadoData Contenido del .cer
     * @param llavePrivadaData Contenido del .key
     * @param password Contraseña del .key
     * @return true si el par es válido
     */
    public boolean validarParCertificadoLlaveDesdeBytes(byte[] certificadoData, byte[] llavePrivadaData, String password) {
        try {
            X509Certificate cert = cargarCertificadoDesdeBytes(certificadoData);
            PrivateKey privateKey = cargarLlavePrivadaDesdeBytes(llavePrivadaData, password);

            // Verificar que la llave pública del certificado corresponda a la llave privada
            PublicKey publicKey = cert.getPublicKey();

            // Hacer una firma de prueba
            String testData = "TEST_VALIDATION";
            Signature signature = Signature.getInstance(SIGNATURE_ALGORITHM, "BC");
            signature.initSign(privateKey);
            signature.update(testData.getBytes(StandardCharsets.UTF_8));
            byte[] firma = signature.sign();

            // Verificar la firma con la llave pública
            signature.initVerify(publicKey);
            signature.update(testData.getBytes(StandardCharsets.UTF_8));
            boolean valido = signature.verify(firma);

            log.info("Validación de par certificado/llave desde bytes: {}", valido ? "EXITOSA" : "FALLIDA");
            return valido;

        } catch (Exception e) {
            log.error("Error validando par certificado/llave desde bytes", e);
            return false;
        }
    }

    /**
     * Obtiene información detallada del certificado para visualización.
     */
    public String obtenerInfoCertificadoDesdeBytes(byte[] certificadoData) throws Exception {
        X509Certificate cert = cargarCertificadoDesdeBytes(certificadoData);

        StringBuilder info = new StringBuilder();
        info.append("Emisor: ").append(cert.getIssuerX500Principal()).append("\n");
        info.append("Titular: ").append(cert.getSubjectX500Principal()).append("\n");
        info.append("Número de Serie: ").append(cert.getSerialNumber().toString(16).toUpperCase()).append("\n");
        info.append("Válido desde: ").append(cert.getNotBefore()).append("\n");
        info.append("Válido hasta: ").append(cert.getNotAfter()).append("\n");
        info.append("Versión: ").append(cert.getVersion()).append("\n");
        info.append("Algoritmo de firma: ").append(cert.getSigAlgName()).append("\n");

        return info.toString();
    }

    /**
     * Método de utilidad para debugging: genera información detallada del proceso de firma.
     * Útil para validar que la cadena original y el sello cumplan con la normativa SEP.
     *
     * @param cadenaOriginal Cadena original generada
     * @param sello Sello digital generado
     * @return Información de debugging
     */
    public String obtenerInfoDebugSello(String cadenaOriginal, String sello) {
        StringBuilder info = new StringBuilder();
        info.append("=== INFORMACIÓN DE DEBUG - SELLO DIGITAL ===\n\n");

        // Información de la cadena original
        info.append("1. CADENA ORIGINAL:\n");
        info.append("   Longitud: ").append(cadenaOriginal.length()).append(" caracteres\n");
        info.append("   Bytes UTF-8: ").append(cadenaOriginal.getBytes(StandardCharsets.UTF_8).length).append(" bytes\n");
        info.append("   Inicia con: ||? ").append(cadenaOriginal.startsWith("||") ? "✓ SÍ" : "✗ NO").append("\n");
        info.append("   Termina con: ||? ").append(cadenaOriginal.endsWith("||") ? "✓ SÍ" : "✗ NO").append("\n");
        info.append("   Contiene pipe (|)?: ").append(cadenaOriginal.contains("|") ? "✓ SÍ" : "✗ NO").append("\n");
        info.append("   Contenido: ").append(cadenaOriginal.substring(0, Math.min(100, cadenaOriginal.length())))
                .append(cadenaOriginal.length() > 100 ? "..." : "").append("\n\n");

        // Información del sello
        info.append("2. SELLO DIGITAL:\n");
        info.append("   Algoritmo: SHA-256 + RSA (SHA256withRSA)\n");
        info.append("   Formato salida: Base64\n");
        info.append("   Longitud Base64: ").append(sello.length()).append(" caracteres\n");

        try {
            byte[] selloBytes = Base64.getDecoder().decode(sello);
            info.append("   Bytes decodificados: ").append(selloBytes.length).append(" bytes\n");
            info.append("   Tamaño esperado RSA: 128, 256, o 512 bytes (depende de la llave)\n");
        } catch (Exception e) {
            info.append("   ERROR: No se pudo decodificar el sello desde Base64\n");
        }

        info.append("   Contenido: ").append(sello.substring(0, Math.min(80, sello.length())))
                .append(sello.length() > 80 ? "..." : "").append("\n\n");

        // Recordatorio de la normativa
        info.append("3. CUMPLIMIENTO NORMATIVA SEP (DOF 13 abril 2018):\n");
        info.append("   ✓ Cadena en UTF-8 JAVA\n");
        info.append("   ✓ Cadena inicia y termina con ||\n");
        info.append("   ✓ Campos separados por |\n");
        info.append("   ✓ Algoritmo SHA-256 para hash\n");
        info.append("   ✓ RSAPrivateEncrypt para firma\n");
        info.append("   ✓ Conversión a Base64\n\n");

        return info.toString();
    }
}

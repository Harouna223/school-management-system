package com.school.service;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

/**
 * Génération de QR Code pour les cartes scolaires des élèves.
 */
@Service
@RequiredArgsConstructor
public class QrCodeService {

    /**
     * Payload du QR Code de la carte scolaire d'un élève.
     */
    public String studentQrPayload(String matricule, String fullName, String className) {
        return String.format(
                "CARTE SCOLAIRE\nMatricule: %s\nNom: %s\nClasse: %s",
                matricule, fullName, className);
    }

    /**
     * QR Code (PNG) d'un élève.
     */
    public byte[] studentQrPng(String matricule, String fullName, String className) throws IOException {
        QRCodeWriter writer = new QRCodeWriter();
        try {
            BitMatrix matrix = writer.encode(studentQrPayload(matricule, fullName, className),
                    BarcodeFormat.QR_CODE, 350, 350);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            MatrixToImageWriter.writeToStream(matrix, "PNG", out);
            return out.toByteArray();
        } catch (com.google.zxing.WriterException ex) {
            throw new RuntimeException("Échec de génération du QR Code", ex);
        }
    }

    /**
     * QR Code de la carte scolaire d'un élève, écrit dans la réponse HTTP.
     */
    public void generateStudentQr(HttpServletResponse response, String matricule,
                                  String fullName, String className) throws IOException {
        response.setContentType("image/png");
        response.setHeader("Content-Disposition",
                "attachment; filename=\"carte-" + matricule + ".png\"");
        response.getOutputStream().write(studentQrPng(matricule, fullName, className));
    }
}

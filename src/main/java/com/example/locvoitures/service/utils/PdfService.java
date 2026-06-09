/*
 * ============================================================================
 * PdfService - Generation de PDF (facture de location)
 * ============================================================================
 *
 * Role :
 *   Genere une facture PDF a partir du template Thymeleaf pdf/facture.html.
 *   Le HTML rendu est converti en PDF par Flying Saucer + OpenPDF
 *   (ITextRenderer). Le PDF est retourne en byte[] et attache au mail
 *   envoye au client a l'acceptation de la demande.
 *
 *   Le PDF n'est PAS expose en HTTP : il n'existe qu'en memoire le temps
 *   de la generation, puis vit uniquement dans la boite mail du client
 *   comme piece jointe.
 *
 * Appele depuis :
 *   - service/LocationService.accepterParAdmin() : genere la facture et la
 *     joint au mail "demande acceptee"
 *   - service/LocationService.creerParAdmin() : meme chose si statut ACCEPTEE
 *
 * Appelle :
 *   - TemplateEngine Thymeleaf : rend pdf/facture.html avec les variables
 *     metier (location, voiture, client, dates, prix)
 *   - ITextRenderer : transforme le HTML rendu en PDF binaire
 *
 * Robustesse :
 *   En cas d'erreur de rendu PDF, on leve BusinessException pour interrompre
 *   la transaction et ne pas envoyer un mail "facture jointe" qui aurait
 *   echoue.
 *
 * Choix techniques :
 *   - Flying Saucer / OpenPDF : implementation pure Java, sans installation
 *     wkhtmltopdf ou autre binaire externe -> portable
 *   - try-with-resources sur ByteArrayOutputStream : ferme automatiquement
 *     le stream meme en cas d'exception
 * ============================================================================
 */
package com.example.locvoitures.service.utils;

import com.example.locvoitures.entity.Location;
import com.example.locvoitures.exception.BusinessException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Service;
// Thymeleaf pour rendre le HTML de la facture
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
// ITextRenderer (Flying Saucer) : transforme un XHTML/HTML en PDF
import org.xhtmlrenderer.pdf.ITextRenderer;

import java.io.ByteArrayOutputStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Service
@RequiredArgsConstructor
@Slf4j
public class PdfService {

    /**
     * Injecte par constructeur (Lombok). Meme bean que celui utilise par
     * EmailService -> les templates pdf/* et email/* partagent le moteur.
     */
    private final TemplateEngine templateEngine;

    /**
     * Genere une facture PDF pour une location donnee.
     * Le template pdf/facture.html attend les variables ci-dessous.
     *
     * @return byte[] contenant le PDF binaire pret a etre attache/sauvegarde
     */
    public byte[] genererFacture(Location location) {
        // Construction du contexte Thymeleaf : on expose les entites metier
        // directement (le template fait .getX() / .getY() en EL).
        Context ctx = new Context();
        ctx.setVariable("location", location);
        // Client extends Utilisateur depuis le refactor : on expose les deux noms
        // pour ne pas casser le template existant (client.* et utilisateur.* OK).
        ctx.setVariable("client", location.getClient());
        ctx.setVariable("utilisateur", location.getClient());
        ctx.setVariable("voiture", location.getVoiture());
        // Timestamp de generation formate pour affichage humain
        ctx.setVariable("dateGeneration",
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")));
        // Numero de facture lisible (ex: FACT-000042 si location.id=42)
        ctx.setVariable("numeroFacture", "FACT-" + String.format("%06d", location.getId()));

        // Rendu du HTML
        String html = templateEngine.process("pdf/facture", ctx);

        // try-with-resources : fermeture automatique du stream meme si exception.
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            // ITextRenderer : moteur Flying Saucer cote serveur
            ITextRenderer renderer = new ITextRenderer();
            // Charger le HTML en chaine
            renderer.setDocumentFromString(html);
            // Compute le layout (positionnement)
            renderer.layout();
            // Ecrire le PDF dans le stream
            renderer.createPDF(out);
            return out.toByteArray();
        } catch (Exception e) {
            log.error("Erreur generation PDF facture pour location {} : {}", location.getId(), e.getMessage());
            // BusinessException -> rollback de la transaction appelante
            throw new BusinessException("Impossible de generer la facture PDF : " + e.getMessage(), e);
        }
    }
}

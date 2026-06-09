/*
 * ============================================================================
 * EmailService - Envoi de mails (Gmail SMTP + templates Thymeleaf)
 * ============================================================================
 *
 * Role :
 *   Centralise tous les envois d'emails du projet :
 *   - Authentification : activation compte, reinitialisation mot de passe
 *   - Locations : acceptation (avec facture PDF en piece jointe), refus,
 *     expiration de pre-reservation, rappel pour avis post-location
 *   - Bannissements : notification de suspension / reactivation
 *   - Reclamations : reponse au client, notification a l'admin
 *
 * Appele depuis :
 *   - service/UtilisateurService : flux inscription, reset password
 *   - service/LocationService : transitions accepter/refuser/payer/expirer
 *   - service/LocationScheduler : envoi des rappels d'avis
 *   - service/BannissementService : bannir/debannir
 *   - service/ReclamationService : cloturer
 *   - controller/ClientLocationController : creerReclamation (notif admin)
 *
 * Appelle :
 *   - JavaMailSender (auto-configure par spring-boot-starter-mail)
 *   - TemplateEngine Thymeleaf (rend les templates HTML email/*.html)
 *   - PdfService (indirectement : le PDF est passe en parametre depuis le
 *     service appelant qui a deja appele PdfService)
 *
 * Configuration externe (application.properties) :
 *   - spring.mail.* : host/port/credentials Gmail SMTP
 *   - app.mail.from : adresse expediteur visible
 *   - app.mail.from-name : nom affiche
 *   - app.base-url : prefixe utilise pour construire les liens absolus
 *
 * Robustesse :
 *   Le helper privé envoyer() catch les exceptions de mail et log un
 *   warning, sans propager. Ainsi un mail qui echoue ne fait pas planter
 *   la transaction metier (ex: location creee mais mail non envoye -> la
 *   location reste creee, on logue l'erreur).
 * ============================================================================
 */
package com.example.locvoitures.service.utils;

import com.example.locvoitures.entity.Location;
import com.example.locvoitures.entity.Reclamation;
import com.example.locvoitures.entity.Utilisateur;

// Jakarta Mail (anciennement javax.mail) : API standard d'envoi de mail
import jakarta.mail.MessagingException;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;

// Lombok pour boilerplate et logger
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

// @Value : injection de propriete depuis application.properties
import org.springframework.beans.factory.annotation.Value;
// Ressource Spring permettant d'attacher un byte[] sans creer de fichier temporaire
import org.springframework.core.io.ByteArrayResource;
// API Spring Mail : JavaMailSender (envoi), MimeMessageHelper (construction multipart)
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;

import org.springframework.stereotype.Service;
// Thymeleaf cote serveur pour rendre les templates email avec interpolation
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.io.UnsupportedEncodingException;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailService {

    /**
     * Auto-configures par Spring Boot a partir de spring.mail.* dans
     * application.properties.
     */
    private final JavaMailSender mailSender;
    private final TemplateEngine templateEngine;

    /**
     * Adresse expediteur visible (ex: contact@locvoitures.com).
     */
    @Value("${app.mail.from}")
    private String from;

    /**
     * Nom expediteur affiche dans le client mail (ex: "Loc'Voitures").
     */
    @Value("${app.mail.from-name}")
    private String fromName;

    /**
     * URL de base de l'application (ex: http://localhost:8080).
     * Prefixe les liens d'activation/reset/rappel pour qu'ils soient absolus.
     */
    @Value("${app.base-url}")
    private String baseUrl;

    // ============================================================
    // Authentification : activation compte, reset password
    // ============================================================

    /**
     * Mail d'activation envoye apres inscription.
     * Le lien contient le tokenActivation en query string. Cliquer dessus
     * declenche AuthController.activer() qui passe actif=true.
     */
    public void envoyerActivation(Utilisateur user) {
        Context ctx = new Context();
        ctx.setVariable("nom", user.getNom());
        ctx.setVariable("prenom", user.getPrenom());
        ctx.setVariable("lien", baseUrl + "/auth/activer?token=" + user.getTokenActivation());
        envoyer(user.getEmail(), "Activation de votre compte", "email/activation", ctx, null, null);
    }

    /**
     * Mail envoye au client lorsque l'admin lui cree un compte directement
     * (cas du client qui se presente au bureau). Contient les identifiants
     * de connexion (mot de passe en clair). Le client est invite a le changer
     * apres premiere connexion depuis /client/profil.
     */
    public void envoyerIdentifiantsCompteCree(Utilisateur user, String motDePasseClair) {
        Context ctx = new Context();
        ctx.setVariable("nom", user.getNom());
        ctx.setVariable("prenom", user.getPrenom());
        ctx.setVariable("email", user.getEmail());
        ctx.setVariable("motDePasse", motDePasseClair);
        ctx.setVariable("lienConnexion", baseUrl + "/auth/login");
        envoyer(user.getEmail(), "Votre compte a ete cree",
                "email/compte-cree-admin", ctx, null, null);
    }

    /**
     * Mail de reinitialisation. Lien valable jusqu'a user.dateExpirationToken.
     */
    public void envoyerLienReinitialisation(Utilisateur user) {
        Context ctx = new Context();
        ctx.setVariable("nom", user.getNom());
        ctx.setVariable("prenom", user.getPrenom());
        ctx.setVariable("lien", baseUrl + "/auth/reinitialiser-mot-de-passe?token=" + user.getTokenReinitialisation());
        ctx.setVariable("expiration", user.getDateExpirationToken());
        envoyer(user.getEmail(), "Reinitialisation de votre mot de passe", "email/reinitialisation", ctx, null, null);
    }

    // ============================================================
    // Locations : transitions de cycle de vie
    // ============================================================

    /**
     * Mail envoye au passage en ACCEPTEE. Inclut la facture PDF en piece
     * jointe (genere par PdfService cote LocationService).
     */
    public void envoyerDemandeAcceptee(Location location, byte[] facturePdf) {
        Context ctx = contextLocation(location);
        envoyer(location.getClient().getEmail(),
                "Votre demande de location a ete acceptee",
                "email/demande-acceptee", ctx,
                "facture-" + location.getId() + ".pdf", facturePdf);
    }

    /**
     * Mail envoye au refus. Inclut le motif saisi par l'admin.
     */
    public void envoyerDemandeRefusee(Location location) {
        Context ctx = contextLocation(location);
        ctx.setVariable("motif", location.getMotifRefus());
        envoyer(location.getClient().getEmail(),
                "Votre demande de location a ete refusee",
                "email/demande-refusee", ctx, null, null);
    }

    /**
     * Mail envoye quand le scheduler passe une location ACCEPTEE en EXPIREE
     * (delai de paiement depasse).
     */
    public void envoyerExpirationReservation(Location location) {
        Context ctx = contextLocation(location);
        envoyer(location.getClient().getEmail(),
                "Pre-reservation expiree",
                "email/expiration", ctx, null, null);
    }

    /**
     * Mail de rappel pour deposer un avis post-restitution.
     * Envoye une seule fois (flag avisRappelEnvoye sur Location).
     */
    public void envoyerRappelAvis(Location location) {
        Context ctx = contextLocation(location);
        ctx.setVariable("lienAvis", baseUrl + "/client/locations/" + location.getId() + "/avis");
        envoyer(location.getClient().getEmail(),
                "Donnez-nous votre avis sur votre derniere location",
                "email/rappel-avis", ctx, null, null);
    }

    // ============================================================
    // Bannissements
    // ============================================================

    /**
     * Notification de bannissement (avec motif).
     */
    public void envoyerBannissement(Utilisateur user, String motif) {
        Context ctx = new Context();
        ctx.setVariable("nom", user.getNom());
        ctx.setVariable("prenom", user.getPrenom());
        ctx.setVariable("motif", motif);
        envoyer(user.getEmail(), "Suspension de votre compte", "email/bannissement", ctx, null, null);
    }

    /**
     * Notification de levee du bannissement.
     */
    public void envoyerDebannissement(Utilisateur user) {
        Context ctx = new Context();
        ctx.setVariable("nom", user.getNom());
        ctx.setVariable("prenom", user.getPrenom());
        envoyer(user.getEmail(), "Reactivation de votre compte", "email/debannissement", ctx, null, null);
    }

    // ============================================================
    // Reclamations
    // ============================================================

    /**
     * Reponse au client a la cloture d'une reclamation. Optionnelle :
     * appele seulement si l'admin a saisi une reponse.
     */
    public void envoyerReponseReclamation(Reclamation reclamation) {
        Utilisateur user = reclamation.getLocation().getClient();
        Context ctx = new Context();
        ctx.setVariable("nom", user.getNom());
        ctx.setVariable("prenom", user.getPrenom());
        ctx.setVariable("categorie", reclamation.getCategorie().name());
        ctx.setVariable("description", reclamation.getDescription());
        ctx.setVariable("reponse", reclamation.getReponseAdmin());
        ctx.setVariable("statut", reclamation.getStatut().name());
        envoyer(user.getEmail(), "Reponse a votre reclamation", "email/reponse-reclamation", ctx, null, null);
    }

    /**
     * Notification a l'admin d'une nouvelle reclamation (alerte temps reel).
     */
    public void notifierAdminNouvelleReclamation(String adminEmail, Reclamation reclamation) {
        Utilisateur client = reclamation.getLocation().getClient();
        Context ctx = new Context();
        ctx.setVariable("clientNom", client.getNom() + " " + client.getPrenom());
        ctx.setVariable("clientEmail", client.getEmail());
        ctx.setVariable("categorie", reclamation.getCategorie().name());
        ctx.setVariable("description", reclamation.getDescription());
        ctx.setVariable("locationId", reclamation.getLocation().getId());
        ctx.setVariable("lien", baseUrl + "/admin/reclamations/" + reclamation.getId());
        envoyer(adminEmail, "Nouvelle reclamation client", "email/nouvelle-reclamation-admin", ctx, null, null);
    }

    // ============================================================
    // Helpers
    // ============================================================

    /**
     * Construit un Context Thymeleaf pre-rempli avec les variables communes
     * aux templates de location (voiture, dates, prix...).
     */
    private Context contextLocation(Location location) {
        Context ctx = new Context();
        Utilisateur u = location.getClient();
        ctx.setVariable("nom", u.getNom());
        ctx.setVariable("prenom", u.getPrenom());
        ctx.setVariable("voiture", location.getVoiture().getMarque().getNom() + " " + location.getVoiture().getModele());
        ctx.setVariable("immatriculation", location.getVoiture().getImmatriculation());
        ctx.setVariable("dateDebut", location.getDateDebut());
        ctx.setVariable("dateFin", location.getDateFin());
        ctx.setVariable("nombreJours", location.getNombreJours());
        ctx.setVariable("prixTotal", location.getPrixTotal());
        ctx.setVariable("dateExpiration", location.getDateExpiration());
        return ctx;
    }

    /**
     * Helper central d'envoi.
     * Construit un MimeMessage multipart si une piece jointe est presente,
     * sinon un mail HTML simple.

     * Catch des exceptions : on log et on continue. Une erreur d'envoi mail
     * n'invalide pas la transaction metier.
     */
    private void envoyer(String to, String sujet, String template, Context ctx,
                         String pieceJointeNom, byte[] pieceJointeContenu) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            // MimeMessageHelper : helper Spring pour configurer un MimeMessage
            // multipart=true uniquement si on a une piece jointe (sinon plus rapide)
            MimeMessageHelper helper = new MimeMessageHelper(message, pieceJointeContenu != null, "UTF-8");
            helper.setFrom(new InternetAddress(from, fromName));
            helper.setTo(to);
            helper.setSubject(sujet);
            // Rendu du template Thymeleaf (resolution depuis classpath:/templates/)
            String html = templateEngine.process(template, ctx);
            // setText(html, true) : true = "html" mode (sinon serait du texte brut)
            helper.setText(html, true);
            // Attacher la piece jointe en memoire (ByteArrayResource = pas de fichier)
            if (pieceJointeContenu != null && pieceJointeNom != null) {
                helper.addAttachment(pieceJointeNom, new ByteArrayResource(pieceJointeContenu));
            }
            mailSender.send(message);
            log.info("Email envoye a {} : {}", to, sujet);
        } catch (MessagingException | UnsupportedEncodingException e) {
            // On ne propage PAS : eviter qu'une defaillance SMTP rollback la transaction metier
            log.error("Erreur d'envoi email a {} ({}) : {}", to, sujet, e.getMessage());
        }
    }
}

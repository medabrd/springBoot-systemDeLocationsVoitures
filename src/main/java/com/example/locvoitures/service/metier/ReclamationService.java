/*
 * ============================================================================
 * ReclamationService - Logique metier des reclamations clients
 * ============================================================================
 *
 * Role :
 *   Centralise les operations metier autour de Reclamation :
 *   - Soumission par le client (avec photo justificative facultative)
 *   - Cloture par l'admin (avec reponse facultative envoyee par email)
 *   - Suppression par l'admin (moderation)
 *
 * Appele depuis :
 *   - controller/ClientLocationController.creerReclamation : soumission
 *   - controller/AdminReclamationController : liste, details, cloturer,
 *     supprimer
 *
 * Appelle :
 *   - ReclamationRepository : CRUD et lookups
 *   - UtilisateurRepository : findByRole(ADMIN) pour notifier tous les admins
 *   - FileStorageService : upload de la photo de reclamation
 *   - EmailService : notification admin a la soumission, reponse au client
 *     a la cloture
 *
 * Gardes metier (soumettre) :
 *   1. Client doit etre proprietaire de la location
 *   2. Location doit etre en statut EN_COURS (apres remise des cles)
 *
 * Workflow simplifie :
 *   Initialement plusieurs statuts (NOUVELLE, EN_COURS, RESOLUE, REJETEE).
 *   Simplifie a 2 etats apres feedback : EN_TRAITEMENT -> CLOTUREE.
 *   La reponse a la cloture est facultative.
 * ============================================================================
 */
package com.example.locvoitures.service.metier;

import com.example.locvoitures.service.utils.EmailService;
import com.example.locvoitures.service.utils.FileStorageService;

import com.example.locvoitures.entity.Client;
import com.example.locvoitures.entity.Location;
import com.example.locvoitures.entity.Reclamation;
import com.example.locvoitures.enumeration.CategorieReclamation;
import com.example.locvoitures.enumeration.StatutLocation;
import com.example.locvoitures.enumeration.StatutReclamation;
import com.example.locvoitures.exception.BusinessException;
import com.example.locvoitures.exception.ResourceNotFoundException;
import com.example.locvoitures.repository.ReclamationRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class ReclamationService {

    private final ReclamationRepository reclamationRepo;
    private final AdminService adminService;
    private final FileStorageService fileStorage;
    private final EmailService emailService;

    /**
     * Recherche par id (404 si introuvable).
     */
    @Transactional(readOnly = true)
    public Reclamation findById(Long id) {
        return reclamationRepo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Reclamation", id));
    }

    /**
     * Liste paginee (toutes reclamations confondues, ecran admin).
     */
    @Transactional(readOnly = true)
    public Page<Reclamation> findAll(Pageable pageable) {
        return reclamationRepo.findAll(pageable);
    }

    /**
     * Filtre par statut (file d'attente admin).
     */
    @Transactional(readOnly = true)
    public Page<Reclamation> findByStatut(StatutReclamation statut, Pageable pageable) {
        return reclamationRepo.findByStatut(statut, pageable);
    }

    /**
     * Soumet une reclamation pour une location en cours.
     * Gardes : proprietaire de la location + location EN_COURS.
     * La photo est facultative.
     * A la fin, tous les admins sont notifies par email (alerte).
     */
    public Reclamation soumettre(Location location, Client client,
                                  CategorieReclamation categorie, String description,
                                  MultipartFile photo) {
        // Garde 1 : ownership de la location
        if (!location.getClient().getId().equals(client.getId())) {
            throw new BusinessException("Vous ne pouvez deposer une reclamation que sur vos propres locations");
        }
        // Garde 2 : statut EN_COURS (interdit avant remise des cles ou apres restitution)
        if (location.getStatut() != StatutLocation.EN_COURS) {
            throw new BusinessException("Une reclamation ne peut etre deposee qu'une fois les cles remises (location en cours)");
        }

        // Construction via builder Lombok
        Reclamation r = Reclamation.builder()
                .location(location)
                .categorie(categorie)
                .description(description)
                .statut(StatutReclamation.EN_TRAITEMENT)
                .build();

        // Photo facultative : si fournie, stockage sur disque
        if (photo != null && !photo.isEmpty()) {
            r.setPhoto(fileStorage.storeImage(photo, "reclamations"));
        }

        Reclamation saved = reclamationRepo.save(r);
        log.info("Reclamation soumise : id={} location={} categorie={}",
                saved.getId(), location.getId(), categorie);

        // Notification a tous les admins (delegation a AdminService).
        adminService.findAll()
                .forEach(admin -> emailService.notifierAdminNouvelleReclamation(admin.getEmail(), saved));

        return saved;
    }

    /**
     * Suppression par l'admin (moderation).
     * Nettoie aussi le fichier photo associe si present (defense storage).
     */
    public void supprimer(Long id) {
        Reclamation r = findById(id);
        if (r.getPhoto() != null) {
            fileStorage.delete(r.getPhoto());
        }
        reclamationRepo.delete(r);
        log.info("Reclamation {} supprimee par admin", id);
    }

    /**
     * Cloture d'une reclamation par l'admin.
     * - reponse facultative : si renseignee, sauvegardee + mail au client
     * - sinon : juste passage en CLOTUREE silencieux
     * - refuse si deja CLOTUREE (idempotence stricte)
     */
    public Reclamation repondre(Long id, String reponse) {
        Reclamation r = findById(id);
        if (r.getStatut() == StatutReclamation.CLOTUREE) {
            throw new BusinessException("Cette reclamation est deja cloturee");
        }
        boolean avecReponse = reponse != null && !reponse.isBlank();
        if (avecReponse) {
            r.setReponseAdmin(reponse.trim());
        }
        r.setStatut(StatutReclamation.CLOTUREE);
        r.setDateReponse(LocalDateTime.now());

        // Mail uniquement si reponse renseignee : sinon cloture silencieuse
        if (avecReponse) {
            emailService.envoyerReponseReclamation(r);
        }
        log.info("Reclamation {} cloturee (reponse envoyee : {})", id, avecReponse);
        return r;
    }
}

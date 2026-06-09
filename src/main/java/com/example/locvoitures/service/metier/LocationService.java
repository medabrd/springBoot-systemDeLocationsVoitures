/*
 * ============================================================================
 * LocationService - Coeur metier du systeme de location
 * ============================================================================
 *
 * Role :
 *   Service le plus charge du projet. Centralise le cycle de vie complet
 *   d'une location :
 *   - Soumission de demande (cote client)
 *   - Acceptation/Refus (cote admin)
 *   - Creation directe par l'admin (client au bureau)
 *   - Validation du paiement (passage ACCEPTEE -> PAYEE)
 *   - Demarrage/Cloture manuelle (PAYEE -> EN_COURS -> TERMINEE)
 *   - Expiration automatique (scheduler -> EXPIREE)
 *   - Suppression admin avec decouplage des FK
 *
 * Appele depuis :
 *   - controller/ClientLocationController : soumettreDemande, telecharger
 *     facture
 *   - controller/AdminLocationController : accepter, refuser, creer,
 *     supprimer, demarrer, cloreLocation, validerPaiement
 *   - service/LocationScheduler : expirerReservation, marquerAvisRappele,
 *     findReservationsExpirees, findRelancesAvisAEnvoyer
 *
 * Appelle :
 *   - LocationRepository : tous les acces base (overlap, KPIs)
 *   - ClientService : verifierProfilCompletPourLocation
 *   - BannissementService : estClientBanni avant insertion
 *   - EmailService : transitions (acceptee/refusee/expiree)
 *   - PdfService : genererFacture pour mail accepte
 *
 * Statuts bloquants (pour overlap) :
 *   ACCEPTEE + PAYEE + EN_COURS sont consideres comme "occupant" le creneau.
 *   EN_ATTENTE n'est PAS bloquant (sinon une demande non confirmee
 *   bloquerait les autres). REFUSEE, EXPIREE, TERMINEE liberent le creneau.
 *
 * Configuration externe :
 *   app.location.delai-paiement-heures : combien d'heures le client a pour
 *   payer apres acceptation avant que la reservation expire.
 *
 * Decouplage des FK :
 *   supprimerParAdmin : Avis.location et Reclamation.location sont passees
 *   a null avant le delete pour preserver l'historique.
 * ============================================================================
 */
package com.example.locvoitures.service.metier;

import com.example.locvoitures.service.utils.EmailService;
import com.example.locvoitures.service.utils.PdfService;

// Wildcard pour importer toutes les entites manipulees (Client, Voiture,
// Location, ConducteurSecondaire, Reclamation, ...)
import com.example.locvoitures.entity.*;
import com.example.locvoitures.enumeration.StatutLocation;
import com.example.locvoitures.enumeration.StatutVoiture;
import com.example.locvoitures.exception.BusinessException;
import com.example.locvoitures.exception.ResourceNotFoundException;
import com.example.locvoitures.repository.LocationRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
// ChronoUnit pour calculer le nombre de jours entre deux dates
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class LocationService {

    /**
     * Statuts qui "occupent" le vehicule sur leur intervalle.
     * Statique car immuable, partage entre toutes les invocations.
     */
    private static final List<StatutLocation> STATUTS_BLOQUANTS = List.of(
            StatutLocation.ACCEPTEE,
            StatutLocation.PAYEE,
            StatutLocation.EN_COURS
    );

    // Dependances injectees (Lombok @RequiredArgsConstructor sur champs final).
    private final LocationRepository locationRepo;
    private final ClientService clientService;
    private final PermisService permisService;
    private final EmailService emailService;
    private final PdfService pdfService;

    /**
     * Delai de paiement apres acceptation (en heures).
     * Configurable via application.properties pour faciliter les tests.
     */
    @Value("${app.location.delai-paiement-heures}")
    private int delaiPaiementHeures;

    // ============================================================
    // Lookup (lectures)
    // ============================================================

    @Transactional(readOnly = true)
    public Location findById(Long id) {
        return locationRepo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Location", id));
    }

    @Transactional(readOnly = true)
    public Page<Location> rechercherParStatut(StatutLocation statut, Pageable pageable) {
        return locationRepo.findByStatut(statut, pageable);
    }

    @Transactional(readOnly = true)
    public Page<Location> rechercherToutes(Pageable pageable) {
        return locationRepo.findAll(pageable);
    }

    /**
     * Toutes les locations d'un client, tous statuts confondus.
     */
    @Transactional(readOnly = true)
    public Page<Location> historiqueClient(Client client, Pageable pageable) {
        return locationRepo.findByClient(client, pageable);
    }

    /**
     * Retourne les locations qui occupent la voiture sur l'intervalle
     * [debut, fin]. Statuts consideres comme bloquants : ACCEPTEE, PAYEE,
     * EN_COURS. Utilise par les vues de disponibilite (calendrier client,
     * catalogue).
     */
    @Transactional(readOnly = true)
    public List<Location> findReservationsOccupant(Voiture voiture, LocalDate debut, LocalDate fin) {
        return locationRepo.findChevauchements(
                voiture, debut, fin,
                List.of(StatutLocation.ACCEPTEE, StatutLocation.PAYEE, StatutLocation.EN_COURS),
                null);
    }

    // ============================================================
    // Soumission de demande (cote client)
    // ============================================================

    /**
     * Cree une demande de location en statut EN_ATTENTE.
     * Gardes :
     *   - Client non banni
     *   - Profil complet (telephone, permis, CIN, photos)
     *   - Dates valides (debut >= aujourd'hui, fin > debut)
     *   - Voiture ACTIVE
     *   - Pas de chevauchement avec des locations bloquantes
     */
    public Location soumettreDemande(Client client,
                                      Voiture voiture,
                                      LocalDate dateDebut,
                                      LocalDate dateFin,
                                      ConducteurSecondaire secondConducteur) {

        // Pre-conditions metier en cascade
        if (permisService.estPermisBanni(client.getNumeroPermis())) {
            throw new BusinessException("Votre compte est suspendu. Vous ne pouvez pas soumettre de demande.");
        }
        clientService.verifierProfilCompletPourLocation(client);
        validerDates(dateDebut, dateFin);

        if (voiture.getStatutGeneral() != StatutVoiture.ACTIVE) {
            throw new BusinessException("Cette voiture n'est pas disponible a la location");
        }
        // Pas d'exclusion d'id (nouvelle location -> null)
        verifierChevauchement(voiture, dateDebut, dateFin, null);

        // Calcul du nombre de jours (+1 pour inclure le jour de debut ET de fin)
        long jours = ChronoUnit.DAYS.between(dateDebut, dateFin) + 1;
        BigDecimal prix = voiture.getTarifJournalier().multiply(BigDecimal.valueOf(jours));

        Location location = Location.builder()
                .client(client)
                .voiture(voiture)
                .dateDebut(dateDebut)
                .dateFin(dateFin)
                .nombreJours((int) jours)
                .prixTotal(prix)
                .statut(StatutLocation.EN_ATTENTE)
                .conducteurSecondaire(secondConducteur)
                .build();

        Location saved = locationRepo.save(location);
        log.info("Demande de location soumise : id={} client={} voiture={}", saved.getId(),
                client.getEmail(), voiture.getImmatriculation());
        return saved;
    }

    // ============================================================
    // Acceptation / Refus (cote admin)
    // ============================================================

    /**
     * Accepte une demande EN_ATTENTE.
     * Re-verifie le chevauchement (entre la soumission et l'acceptation,
     * une autre demande a pu etre acceptee pour le meme vehicule).
     * Pose dateExpiration = now + delaiPaiementHeures. Le scheduler la
     * fera passer en EXPIREE si non payee a temps.
     * Genere la facture PDF et envoie le mail au client.
     */
    public void accepterDemande(Long locationId) {
        Location l = findById(locationId);
        if (l.getStatut() != StatutLocation.EN_ATTENTE) {
            throw new BusinessException("Seules les demandes en attente peuvent etre acceptees");
        }
        // excludeId=l.getId() : ne pas se chevaucher soi-meme (defense)
        verifierChevauchement(l.getVoiture(), l.getDateDebut(), l.getDateFin(), l.getId());

        l.setStatut(StatutLocation.ACCEPTEE);
        l.setDateExpiration(LocalDateTime.now().plusHours(delaiPaiementHeures));

        // Facture PDF + mail (EmailService ne propage pas si echec mail)
        byte[] facturePdf = pdfService.genererFacture(l);
        emailService.envoyerDemandeAcceptee(l, facturePdf);

        log.info("Demande {} acceptee, expiration {}", locationId, l.getDateExpiration());
    }

    /**
     * Refuse une demande EN_ATTENTE avec motif obligatoire.
     * Le motif est inclus dans le mail au client pour qu'il comprenne
     * la decision.
     */
    public void refuserDemande(Long locationId, String motif) {
        if (motif == null || motif.isBlank()) {
            throw new BusinessException("Un motif de refus est obligatoire");
        }
        Location l = findById(locationId);
        if (l.getStatut() != StatutLocation.EN_ATTENTE) {
            throw new BusinessException("Seules les demandes en attente peuvent etre refusees");
        }
        l.setStatut(StatutLocation.REFUSEE);
        l.setMotifRefus(motif);
        emailService.envoyerDemandeRefusee(l);
        log.info("Demande {} refusee : {}", locationId, motif);
    }

    /**
     * Creation directe par l'admin (client venu au bureau).
     * Le statut initial peut etre ACCEPTEE, PAYEE ou EN_COURS selon le
     * scenario (paiement futur, paiement immediat, debut immediat).

     * Gardes specifiques :
     *   - Statut initial parmi 3 valeurs autorisees
     *   - EN_COURS impossible si dateDebut > aujourd'hui
     *   - Date passee interdite sauf si statut EN_COURS (cas remise des cles
     *     pour une location qui aurait du commencer hier)
     *   - Voiture ACTIVE et client non banni
     *   - Pas de chevauchement
      
     * Si statut ACCEPTEE : envoie aussi la facture PDF + mail (memes effets
     * que accepterDemande).
     */
    public Location creerParAdmin(Client client, Voiture voiture,
                                   LocalDate dateDebut, LocalDate dateFin,
                                   StatutLocation statutInitial) {
        if (statutInitial != StatutLocation.ACCEPTEE
                && statutInitial != StatutLocation.PAYEE
                && statutInitial != StatutLocation.EN_COURS) {
            throw new BusinessException("Statut initial invalide (ACCEPTEE, PAYEE ou EN_COURS attendus)");
        }
        if (dateDebut == null || dateFin == null) {
            throw new BusinessException("Les dates de debut et de fin sont obligatoires");
        }
        if (!dateFin.isAfter(dateDebut)) {
            throw new BusinessException("La date de fin doit etre posterieure a la date de debut");
        }
        if (statutInitial == StatutLocation.EN_COURS && dateDebut.isAfter(LocalDate.now())) {
            throw new BusinessException("Une location ne peut etre EN_COURS avant sa date de debut");
        }
        if (statutInitial != StatutLocation.EN_COURS && dateDebut.isBefore(LocalDate.now())) {
            throw new BusinessException("La date de debut ne peut etre dans le passe");
        }
        if (voiture.getStatutGeneral() != StatutVoiture.ACTIVE) {
            throw new BusinessException("Cette voiture est hors service");
        }
        if (permisService.estPermisBanni(client.getNumeroPermis())) {
            throw new BusinessException("Ce client est suspendu (permis banni)");
        }
        verifierChevauchement(voiture, dateDebut, dateFin, null);

        long jours = ChronoUnit.DAYS.between(dateDebut, dateFin) + 1;
        BigDecimal prix = voiture.getTarifJournalier().multiply(BigDecimal.valueOf(jours));

        Location location = Location.builder()
                .client(client)
                .voiture(voiture)
                .dateDebut(dateDebut)
                .dateFin(dateFin)
                .nombreJours((int) jours)
                .prixTotal(prix)
                .statut(statutInitial)
                // datePaiement non pose si ACCEPTEE (pas encore paye)
                .datePaiement(statutInitial == StatutLocation.ACCEPTEE ? null : LocalDateTime.now())
                // dateExpiration pose uniquement si ACCEPTEE (delai a courir)
                .dateExpiration(statutInitial == StatutLocation.ACCEPTEE
                        ? LocalDateTime.now().plusHours(delaiPaiementHeures) : null)
                .build();
        Location saved = locationRepo.save(location);
        log.info("Location creee par admin : id={} client={} voiture={} statut={}",
                saved.getId(), client.getEmail(),
                voiture.getImmatriculation(), statutInitial);

        // Si ACCEPTEE : meme traitement que accepterDemande -> facture + mail
        if (statutInitial == StatutLocation.ACCEPTEE) {
            byte[] facturePdf = pdfService.genererFacture(saved);
            emailService.envoyerDemandeAcceptee(saved, facturePdf);
            log.info("Facture envoyee au client pour location {} (statut ACCEPTEE)", saved.getId());
        }
        return saved;
    }

    /**
     * Suppression d'une location par l'admin.
     * Decouplage des FK :
     *   - Avis : passe location=null pour preserver l'avis cote audit
     *   - Reclamations : meme principe
     * On clear() ensuite les collections cote Location pour eviter qu'Hibernate
     * essaie de cascade.
     */
    public void supprimerParAdmin(Long locationId) {
        Location l = findById(locationId);
        if (l.getAvis() != null) {
            l.getAvis().setLocation(null);
            l.setAvis(null);
        }
        if (l.getReclamations() != null && !l.getReclamations().isEmpty()) {
            for (Reclamation r : l.getReclamations()) {
                r.setLocation(null);
            }
            l.getReclamations().clear();
        }
        locationRepo.delete(l);
        log.info("Location {} supprimee par admin (avis et reclamations conserves)", locationId);
    }

    /**
     * Validation du paiement en especes effectue au bureau.
     * ACCEPTEE -> PAYEE. Annule la dateExpiration (plus de risque d'expiration).
     */
    public void validerPaiement(Long locationId) {
        Location l = findById(locationId);
        if (l.getStatut() != StatutLocation.ACCEPTEE) {
            throw new BusinessException("Seules les locations acceptees peuvent passer au paiement");
        }
        l.setStatut(StatutLocation.PAYEE);
        l.setDatePaiement(LocalDateTime.now());
        l.setDateExpiration(null);
        log.info("Paiement valide pour location {}", locationId);
    }

    // ============================================================
    // Transitions automatiques (scheduler) et manuelles (admin)
    // ============================================================

    /**
     * Appele par LocationScheduler pour passer ACCEPTEE -> EXPIREE.
     * Pas de verification de statut ici car la requete repo a deja filtre.
     */
    public void expirerReservation(Location l) {
        l.setStatut(StatutLocation.EXPIREE);
        emailService.envoyerExpirationReservation(l);
        log.info("Pre-reservation {} expiree", l.getId());
    }

    /**
     * Admin remet les cles : PAYEE -> EN_COURS.
     * Refuse si avant la date de debut prevue (pas de location avant l'heure).
     */
    public void demarrerLocation(Long locationId) {
        Location l = findById(locationId);
        if (l.getStatut() != StatutLocation.PAYEE) {
            throw new BusinessException("Seules les locations payees peuvent etre demarrees");
        }
        if (LocalDate.now().isBefore(l.getDateDebut())) {
            throw new BusinessException("La location ne peut etre demarree avant la date de debut prevue ("
                    + l.getDateDebut() + ")");
        }
        l.setStatut(StatutLocation.EN_COURS);
        log.info("Location {} demarree par admin (en cours)", l.getId());
    }

    /**
     * Admin recupere les cles : EN_COURS -> TERMINEE.
     */
    public void cloreLocation(Long locationId) {
        Location l = findById(locationId);
        if (l.getStatut() != StatutLocation.EN_COURS) {
            throw new BusinessException("Seules les locations en cours peuvent etre cloturees");
        }
        l.setStatut(StatutLocation.TERMINEE);
        log.info("Location {} cloturee par admin (terminee)", l.getId());
    }

    /**
     * Pose le flag avisRappelEnvoye=true apres envoi du mail de rappel
     * par le scheduler (evite double-envoi).
     */
    public void marquerAvisRappele(Location l) {
        l.setAvisRappelEnvoye(true);
    }

    // ============================================================
    // Listes utiles au scheduler
    // ============================================================

    @Transactional(readOnly = true)
    public List<Location> findReservationsExpirees() {
        return locationRepo.findReservationsExpirees(LocalDateTime.now());
    }

    @Transactional(readOnly = true)
    public List<Location> findRelancesAvisAEnvoyer() {
        return locationRepo.findRelancesAvisAEnvoyer();
    }

    // ============================================================
    // Verification de chevauchement de dates
    // ============================================================

    /**
     * Verifie qu'aucune location bloquante (ACCEPTEE/PAYEE/EN_COURS) ne
     * chevauche la periode demandee. Excludeid permet d'exclure une
     * location specifique (utile en modification pour ne pas s'auto-bloquer).
     */
    private void verifierChevauchement(Voiture voiture, LocalDate dateDebut, LocalDate dateFin, Long excludeId) {
        List<Location> chevauchantes = locationRepo.findChevauchements(
                voiture, dateDebut, dateFin, STATUTS_BLOQUANTS, excludeId);
        if (!chevauchantes.isEmpty()) {
            throw new BusinessException(
                "La voiture est deja reservee ou louee sur tout ou partie de cette periode. " +
                "Veuillez choisir d'autres dates ou un autre vehicule.");
        }
    }

    /**
     * Validation simple des dates pour soumission cote client.
     * Plus stricte que creerParAdmin : dateDebut pas dans le passe.
     */
    private void validerDates(LocalDate dateDebut, LocalDate dateFin) {
        if (dateDebut == null || dateFin == null) {
            throw new BusinessException("Les dates de debut et de fin sont obligatoires");
        }
        if (dateDebut.isBefore(LocalDate.now())) {
            throw new BusinessException("La date de debut ne peut etre dans le passe");
        }
        if (!dateFin.isAfter(dateDebut)) {
            throw new BusinessException("La date de fin doit etre posterieure a la date de debut");
        }
    }
}

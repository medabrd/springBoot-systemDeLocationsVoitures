/*
 * ============================================================================
 * LocationRepository - Repository JPA pour Location (entite centrale)
 * ============================================================================
 *
 * Role :
 *   Acces base pour Location. Le plus charge en requetes du projet :
 *   listes paginees par client/statut, verification de chevauchement,
 *   purges schedulees, KPIs dashboard.
 *
 * Utilise par :
 *   - service/LocationService : tout le workflow metier (soumettre, accepter,
 *     refuser, payer, demarrer, terminer, expirer)
 *   - service/LocationScheduler : findReservationsExpirees,
 *     findRelancesAvisAEnvoyer pour les passages automatiques
 *   - service/DashboardService : chiffreAffairesTotal, countDemandesEnAttente
 *     et countByStatut pour les KPIs
 *   - controller/ClientLocationController : findByClient
 *   - controller/AdminLocationController : findAll/findByStatut + pagination
 *
 * Requetes complexes (JPQL via @Query) :
 *   - findChevauchements : detection de conflits de dates pour une voiture,
 *     critere principal lors de l'acceptation. Exclut une location specifique
 *     (utile lors d'une modification pour ne pas se chevaucher soi-meme).
 *   - findReservationsExpirees : ACCEPTEE + dateExpiration depassee
 *   - findRelancesAvisAEnvoyer : TERMINEE sans avis et sans rappel envoye
 *   - chiffreAffairesTotal : SUM(prixTotal) sur les statuts payes
 *   - countDemandesEnAttente : nombre de EN_ATTENTE pour badge admin
 *
 * Convention :
 *   Les FQCN com.example.locvoitures.enumeration.StatutLocation.XXX sont
 *   necessaires dans les @Query JPQL car JPQL n'a pas d'import.
 * ============================================================================
 */
package com.example.locvoitures.repository;

// Entites manipulees
import com.example.locvoitures.entity.Client;
import com.example.locvoitures.entity.Location;
import com.example.locvoitures.entity.Voiture;

// Enum statut pour filtres
import com.example.locvoitures.enumeration.StatutLocation;

// Pagination
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

// Spring Data
import org.springframework.data.jpa.repository.JpaRepository;
// @Query : requete JPQL explicite (necessaire pour les cas complexes)
import org.springframework.data.jpa.repository.Query;
// @Param : nomme les parametres de la requete pour le binding
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface LocationRepository extends JpaRepository<Location, Long> {

    /**
     * Liste paginee des locations d'un client (tableau "Mes locations").
     */
    Page<Location> findByClient(Client client, Pageable pageable);

    /**
     * Liste paginee filtree par statut (cote admin).
     */
    Page<Location> findByStatut(StatutLocation statut, Pageable pageable);

    /**
     * Compteur par statut (utilise dans le dashboard).
     */
    long countByStatut(StatutLocation statut);

    /**
     * Detection de chevauchement de dates : retourne les locations bloquantes
     * qui chevauchent l'intervalle [dateDebut, dateFin] pour une voiture donnee.
     *
     * Deux intervalles [a1,a2] et [b1,b2] se chevauchent si :
     *   a1 <= b2 AND b1 <= a2
     *
     * Le parametre locationId permet d'exclure la location courante en cas
     * de modification (sinon elle se chevaucherait elle-meme).
     *
     * @Query("""...""") : JPQL avec text block Java 17 (multilignes lisibles).
     * @Param : nomme chaque parametre pour matching avec :nom dans la requete.
     */
    @Query("""
        SELECT l FROM Location l
        WHERE l.voiture = :voiture
          AND l.statut IN :statutsBloquants
          AND l.dateDebut <= :dateFin
          AND l.dateFin   >= :dateDebut
          AND (:locationId IS NULL OR l.id <> :locationId)
    """)
    List<Location> findChevauchements(@Param("voiture") Voiture voiture,
                                       @Param("dateDebut") LocalDate dateDebut,
                                       @Param("dateFin") LocalDate dateFin,
                                       @Param("statutsBloquants") List<StatutLocation> statutsBloquants,
                                       @Param("locationId") Long locationId);

    /**
     * Locations ACCEPTEES dont le delai de paiement est expire.
     * Utilise par LocationScheduler.purgerReservationsExpirees() pour
     * basculer en EXPIREE et liberer le creneau.
     */
    @Query("""
        SELECT l FROM Location l
        WHERE l.statut = com.example.locvoitures.enumeration.StatutLocation.ACCEPTEE
          AND l.dateExpiration IS NOT NULL
          AND l.dateExpiration < :maintenant
    """)
    List<Location> findReservationsExpirees(@Param("maintenant") LocalDateTime maintenant);

    /**
     * Locations TERMINEES sans avis et sans rappel envoye.
     * Utilise par LocationScheduler.envoyerRelancesAvis() : envoie un mail
     * de relance et flag avisRappelEnvoye=true pour ne pas spammer.
     */
    @Query("""
        SELECT l FROM Location l
        WHERE l.statut = com.example.locvoitures.enumeration.StatutLocation.TERMINEE
          AND l.avis IS NULL
          AND l.avisRappelEnvoye = false
    """)
    List<Location> findRelancesAvisAEnvoyer();

    // ================================================================
    // KPIs Dashboard - calcules cote SQL pour performance
    // ================================================================

    /**
     * Chiffre d'affaires total = SUM(prixTotal) sur PAYEE + EN_COURS + TERMINEE.
     * COALESCE(..., 0) pour eviter null si pas de location.
     */
    @Query("""
        SELECT COALESCE(SUM(l.prixTotal), 0) FROM Location l
        WHERE l.statut IN (
            com.example.locvoitures.enumeration.StatutLocation.PAYEE,
            com.example.locvoitures.enumeration.StatutLocation.EN_COURS,
            com.example.locvoitures.enumeration.StatutLocation.TERMINEE
        )
    """)
    BigDecimal chiffreAffairesTotal();

    /**
     * Compteur des demandes EN_ATTENTE (affiche en badge sur le menu admin).
     */
    @Query("SELECT COUNT(l) FROM Location l WHERE l.statut = com.example.locvoitures.enumeration.StatutLocation.EN_ATTENTE")
    long countDemandesEnAttente();
}

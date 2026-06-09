/*
 * ============================================================================
 * VoitureRepository - Repository JPA pour Voiture (catalogue + flotte)
 * ============================================================================
 *
 * Role :
 *   Acces base pour Voiture. Heberge la requete principale du catalogue
 *   (filtrer) avec criteres dynamiques optionnels, ainsi que les requetes
 *   de KPIs (top voitures louees, repartition par categorie).
 *
 * Utilise par :
 *   - service/VoitureService : CRUD admin
 *   - controller/CatalogueController : filtrer(...) pour le catalogue client
 *   - controller/AdminVoitureController : meme requete cote admin
 *   - controller/VoitureDisponibiliteController : verification de
 *     disponibilite par dates
 *   - service/DashboardService : countByStatutGeneral, topVoituresLouees,
 *     repartitionParCategorie
 *
 * Particularites des requetes :
 *   - filtrer() utilise le pattern "(:param IS NULL OR ...)" pour rendre
 *     chaque critere optionnel sans dynamique
 *   - Le filtre equipements utilise une sous-requete COUNT pour exiger TOUS
 *     les equipements (logique AND, pas OR)
 *   - Le filtre de disponibilite par dates utilise NOT EXISTS sur les
 *     locations non-terminales (ACCEPTEE/PAYEE/EN_COURS)
 *   - topVoituresLouees retourne Object[] (projection libre) parce qu'on
 *     veut id+modele+marque+count sans creer un DTO dedie
 *
 * Convention sur les enums dans JPQL :
 *   Les references aux enums dans @Query doivent etre en FQCN
 *   (com.example.locvoitures.enumeration.StatutLocation.PAYEE) car JPQL
 *   n'a pas de mecanisme d'import.
 * ============================================================================
 */
package com.example.locvoitures.repository;

import com.example.locvoitures.entity.Voiture;
import com.example.locvoitures.enumeration.Carburant;
import com.example.locvoitures.enumeration.StatutVoiture;
import com.example.locvoitures.enumeration.Transmission;

// Pagination
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

// Spring Data
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Repository
public interface VoitureRepository extends JpaRepository<Voiture, Long> {

    /**
     * Test d'unicite avant insertion (anti-doublon explicite).
     */
    boolean existsByImmatriculation(String immatriculation);

    /**
     * Recherche multi-criteres avec tous filtres optionnels.
     *
     * Pattern (:param IS NULL OR <condition>) :
     *   Si le parametre est null cote service, la clause est vraie et donc
     *   le critere n'a aucun effet. Sinon le filtre s'applique.
     *
     * Filtre equipements (logique AND) :
     *   - nbEquipementsRequis = 0L -> critere neutralise
     *   - sinon : la voiture doit posseder TOUS les equipements selectionnes
     *     (COUNT(DISTINCT e.id) WHERE e.id IN ... = nbEquipementsRequis)
     *
     * Filtre dates :
     *   NOT EXISTS sur les locations non-terminales qui chevauchent. Si
     *   aucune chevauche, la voiture est disponible sur la periode.
     */
    @Query("""
        SELECT v FROM Voiture v
        WHERE (:keyword IS NULL OR :keyword = ''
            OR LOWER(v.modele)        LIKE LOWER(CONCAT('%', :keyword, '%'))
            OR LOWER(v.marque.nom)    LIKE LOWER(CONCAT('%', :keyword, '%'))
            OR LOWER(v.categorie.nom) LIKE LOWER(CONCAT('%', :keyword, '%'))
            OR LOWER(v.couleur)       LIKE LOWER(CONCAT('%', :keyword, '%'))
        )
        AND (:marqueId    IS NULL OR v.marque.id = :marqueId)
        AND (:categorieId IS NULL OR v.categorie.id = :categorieId)
        AND (:prixMin     IS NULL OR v.tarifJournalier >= :prixMin)
        AND (:prixMax     IS NULL OR v.tarifJournalier <= :prixMax)
        AND (:transmission IS NULL OR v.details.transmission = :transmission)
        AND (:carburant   IS NULL OR v.details.typeCarburant = :carburant)
        AND (:statut      IS NULL OR v.statutGeneral = :statut)
        AND (:nbEquipementsRequis = 0L OR (
            SELECT COUNT(DISTINCT e.id) FROM Voiture v2 JOIN v2.equipements e
            WHERE v2.id = v.id AND e.id IN :equipementIds
        ) = :nbEquipementsRequis)
        AND (:dateDebut IS NULL OR :dateFin IS NULL OR NOT EXISTS (
            SELECT 1 FROM Location l
            WHERE l.voiture = v
              AND l.statut IN (
                  com.example.locvoitures.enumeration.StatutLocation.ACCEPTEE,
                  com.example.locvoitures.enumeration.StatutLocation.PAYEE,
                  com.example.locvoitures.enumeration.StatutLocation.EN_COURS
              )
              AND l.dateDebut <= :dateFin
              AND l.dateFin   >= :dateDebut
        ))
    """)
    Page<Voiture> filtrer(@Param("keyword") String keyword,
                           @Param("marqueId") Long marqueId,
                           @Param("categorieId") Long categorieId,
                           @Param("prixMin") BigDecimal prixMin,
                           @Param("prixMax") BigDecimal prixMax,
                           @Param("transmission") Transmission transmission,
                           @Param("carburant") Carburant carburant,
                           @Param("statut") StatutVoiture statut,
                           @Param("equipementIds") List<Long> equipementIds,
                           @Param("nbEquipementsRequis") long nbEquipementsRequis,
                           @Param("dateDebut") LocalDate dateDebut,
                           @Param("dateFin") LocalDate dateFin,
                           Pageable pageable);

    // ================================================================
    // KPIs Dashboard (calculs cote SQL via @Query JPQL)
    // ================================================================

    /**
     * Compteur par statut (ACTIVE / HORS_SERVICE). KPI dashboard.
     */
    long countByStatutGeneral(StatutVoiture statut);

    /**
     * Top voitures par nombre de locations confirmees.
     * Retour Object[] = [voitureId, modele, marqueNom, count].
     * JOIN ... WITH : condition sur le JOIN (different de WHERE qui filtre apres).
     */
    @Query("""
        SELECT v.id, v.modele, v.marque.nom, COUNT(l)
        FROM Voiture v
        LEFT JOIN v.locations l
            WITH l.statut IN (
                com.example.locvoitures.enumeration.StatutLocation.PAYEE,
                com.example.locvoitures.enumeration.StatutLocation.EN_COURS,
                com.example.locvoitures.enumeration.StatutLocation.TERMINEE
            )
        GROUP BY v.id, v.modele, v.marque.nom
        ORDER BY COUNT(l) DESC
    """)
    List<Object[]> topVoituresLouees(Pageable pageable);

    /**
     * Repartition de la flotte par categorie. Retour [categorieNom, count].
     * Utilise pour un graphique camembert/donut sur le dashboard.
     */
    @Query("""
        SELECT v.categorie.nom, COUNT(v)
        FROM Voiture v
        GROUP BY v.categorie.nom
        ORDER BY COUNT(v) DESC
    """)
    List<Object[]> repartitionParCategorie();
}

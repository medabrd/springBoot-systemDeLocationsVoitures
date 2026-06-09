/*
 * ============================================================================
 * AvisRepository - Acces aux donnees pour les avis clients
 * ============================================================================
 *
 * Role :
 *   Couche d'acces aux donnees (DAO) pour l'entite Avis. Spring Data JPA
 *   genere automatiquement l'implementation au demarrage en analysant le nom
 *   des methodes (query methods) ou les annotations @Query.
 *
 * Utilise par :
 *   - AvisService : existsByLocation (anti-doublon avant creation),
 *     findAll (moderation admin), delete (suppression apres moderation)
 *   - DashboardService : noteMoyenneGlobale pour KPI
 *
 * Methodes derivees (sans @Query) :
 *   Spring Data analyse le nom (existsByLocation) et genere le SELECT/EXISTS
 *   approprie. Convention : verb + By + attribut.
 *
 * Heritage JpaRepository<Avis, Long> :
 *   Fournit automatiquement : save, saveAll, findById, findAll, count,
 *   delete, deleteById, existsById, etc. + support Pageable et Sort.
 *
 * Pas de @Transactional ici : les transactions sont gerees a la couche
 * service. Les methodes de JpaRepository sont implicitement transactional
 * en lecture seule (read-only) si pas dans une transaction ouverte.
 * ============================================================================
 */
package com.example.locvoitures.repository;

// Entites referencees comme parametres ou type de retour
import com.example.locvoitures.entity.Avis;
import com.example.locvoitures.entity.Location;

// Interface de base fournissant CRUD + pagination
import org.springframework.data.jpa.repository.JpaRepository;

// Annotation pour requete JPQL custom
import org.springframework.data.jpa.repository.Query;

// Annotation declarant un bean Spring (optionnelle pour les interfaces JpaRepository
// car Spring les detecte automatiquement, mais explicite ici pour la clarte)
import org.springframework.stereotype.Repository;


/**
 * @Repository explicite la nature du bean (DAO) et permet la traduction
 *             automatique des SQLException -> DataAccessException de Spring.
 *
 * extends JpaRepository<Avis, Long> :
 *   - Avis : type de l'entite manipulee
 *   - Long : type de la clef primaire
 *   Herite de save, findAll, findById, count, deleteById, exists, etc.
 */
@Repository
public interface AvisRepository extends JpaRepository<Avis, Long> {

    /**
     * Methode derivee : existsBy* genere un SELECT COUNT(*)>0 plus efficace
     * qu'un findBy*().isPresent(). Utile dans AvisService.creerAvis() pour
     * empecher le double depot d'avis sans charger l'entite.
     */
    boolean existsByLocation(Location location);

    /**
     * Requete JPQL custom car aucune methode derivee ne peut exprimer un AVG.
     * COALESCE(..., 0) renvoie 0 si la table est vide (au lieu de null qui
     * ferait NPE cote Java sur un Double).
     * Type de retour Double (objet) car @Query peut renvoyer null en theorie ;
     * le COALESCE garantit qu'on aura un 0 et non null.
     *
     * Utilise dans DashboardService.buildDashboard() pour le KPI "note moyenne".
     */
    @Query("SELECT COALESCE(AVG(a.note), 0) FROM Avis a")
    Double noteMoyenneGlobale();
}

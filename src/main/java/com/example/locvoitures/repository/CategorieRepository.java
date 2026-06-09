/*
 * ============================================================================
 * CategorieRepository - Repository JPA pour Categorie
 * ============================================================================
 *
 * Role :
 *   Acces base de donnees pour l'entite Categorie. Spring Data genere
 *   automatiquement l'implementation a partir de l'interface.
 *
 * Utilise par :
 *   - service/CategorieService : CRUD, verification d'unicite
 *   - service/VoitureService : recuperation de Categorie par id pour
 *     associer a une Voiture
 *   - controller/AdminCategorieController : list/find/save/delete
 *
 * Methodes :
 *   - JpaRepository herite : findAll, findById, save, deleteById, count...
 *   - findByNomIgnoreCase : lookup case-insensitive (saisie utilisateur)
 *   - existsByNomIgnoreCase : test d'unicite avant insertion (genere
 *     SELECT COUNT(*) > 0)
 *
 * Convention Spring Data :
 *   Le nom des methodes est parse par DerivedQuery (findBy<Field><Op>).
 *   IgnoreCase ajoute LOWER() dans la clause WHERE.
 * ============================================================================
 */
package com.example.locvoitures.repository;

// Entite manipulee
import com.example.locvoitures.entity.Categorie;

// Spring Data JPA : interface qui fournit save/find/delete generiques
import org.springframework.data.jpa.repository.JpaRepository;
// @Repository : marque le bean comme repository et traduit les exceptions DB
// en DataAccessException (non strictement obligatoire pour Spring Data)
import org.springframework.stereotype.Repository;

// Optional pour les retours findBy (eviter NullPointerException)
import java.util.Optional;

/**
 * @Repository : stereotype Spring (auto-detection + translation exceptions)
 * extends JpaRepository<Categorie, Long> :
 *   - parametre 1 = entite geree
 *   - parametre 2 = type de la cle primaire
 */
@Repository
public interface CategorieRepository extends JpaRepository<Categorie, Long> {

    /**
     * Recherche par nom (case-insensitive).
     * SQL genere : SELECT * FROM categorie WHERE LOWER(nom) = LOWER(?)
     */
    Optional<Categorie> findByNomIgnoreCase(String nom);

    /**
     * Test d'existence (plus rapide qu'un find suivi d'un isPresent).
     * SQL : SELECT 1 FROM categorie WHERE LOWER(nom) = LOWER(?) LIMIT 1.
     */
    boolean existsByNomIgnoreCase(String nom);
}

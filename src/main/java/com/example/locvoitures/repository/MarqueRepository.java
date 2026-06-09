/*
 * ============================================================================
 * MarqueRepository - Repository JPA pour Marque
 * ============================================================================
 *
 * Role :
 *   Acces base pour Marque. CRUD + lookup case-insensitive comme Categorie
 *   et Equipement.
 *
 * Utilise par :
 *   - service/MarqueService : CRUD avec gestion upload du logo
 *   - service/VoitureService : recuperation par id
 *   - controller/AdminMarqueController : /admin/marques
 *   - controller/CatalogueController : liste des marques pour le filtre
 *
 * Methodes :
 *   - findByNomIgnoreCase / existsByNomIgnoreCase : verification d'unicite.
 * ============================================================================
 */
package com.example.locvoitures.repository;

import com.example.locvoitures.entity.Marque;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface MarqueRepository extends JpaRepository<Marque, Long> {

    /**
     * Lookup case-insensitive sur le nom.
     */
    Optional<Marque> findByNomIgnoreCase(String nom);

    /**
     * Test d'unicite avant insertion (evite "Renault"/"renault" en double).
     */
    boolean existsByNomIgnoreCase(String nom);
}

/*
 * ============================================================================
 * EquipementRepository - Repository JPA pour Equipement
 * ============================================================================
 *
 * Role :
 *   Acces base pour Equipement. Operations CRUD heritees + lookup par nom
 *   case-insensitive.
 *
 * Utilise par :
 *   - service/EquipementService : CRUD admin
 *   - service/VoitureService : recuperation par id pour ajout/retrait
 *     d'equipements sur une voiture
 *   - controller/AdminEquipementController : /admin/equipements
 *   - controller/CatalogueController : findAll pour generer la liste de
 *     filtre cote catalogue
 *
 * Methodes :
 *   - findByNomIgnoreCase / existsByNomIgnoreCase : meme pattern que
 *     CategorieRepository pour eviter les doublons casse-different.
 * ============================================================================
 */
package com.example.locvoitures.repository;

import com.example.locvoitures.entity.Equipement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface EquipementRepository extends JpaRepository<Equipement, Long> {

    /**
     * Lookup case-insensitive : permet "Climatisation" / "climatisation" /
     * "CLIMATISATION" sans creer de doublons.
     */
    Optional<Equipement> findByNomIgnoreCase(String nom);

    /**
     * Test d'unicite avant insertion.
     */
    boolean existsByNomIgnoreCase(String nom);
}

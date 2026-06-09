/*
 * ============================================================================
 * PermisRepository - Repository JPA pour Permis (et son bannissement)
 * ============================================================================
 *
 * Role :
 *   Acces base pour l'entite Permis. Methodes :
 *   - Lookup par numero (unique)
 *   - Verifications anti-doublon et anti-ban pour les flux location
 *   - Liste paginee des permis bannis pour l'ecran admin
 *
 *   Le concept de "permis banni" est porte par l'attribut Permis.banni
 *   (pas une entite separee).
 *
 * Utilise par :
 *   - service/ClientService : findByNumero / existsByNumero (anti-doublon)
 *   - service/BannissementService : flux bannir/debannir/lister
 *   - service/LocationService : existsByNumeroAndBanniTrue avant insertion
 *   - service/DashboardService : countByBanniTrue (KPI)
 *   - controller/AdminPermisBanniController : ecran CRUD permis bannis
 * ============================================================================
 */
package com.example.locvoitures.repository;

import com.example.locvoitures.entity.Permis;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PermisRepository extends JpaRepository<Permis, Long> {

    /**
     * Lookup par numero (unique).
     */
    Optional<Permis> findByNumero(String numero);

    /**
     * Existence par numero (anti-doublon avant creation).
     */
    boolean existsByNumero(String numero);

    /**
     * Test booleen "permis banni" pour le flux de location. Genere
     * SELECT 1 FROM permis WHERE numero=? AND banni=true LIMIT 1.
     */
    boolean existsByNumeroAndBanniTrue(String numero);

    /**
     * Liste paginee des permis bannis (ecran /admin/permis-bannis).
     */
    Page<Permis> findByBanniTrue(Pageable pageable);

    /**
     * Compte des permis bannis (KPI dashboard).
     */
    long countByBanniTrue();
}

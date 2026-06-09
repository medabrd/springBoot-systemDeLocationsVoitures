/*
 * ============================================================================
 * ReclamationRepository - Repository JPA pour Reclamation
 * ============================================================================
 *
 * Role :
 *   Acces base pour Reclamation. Liste paginee filtree par statut pour
 *   l'admin, compteurs pour KPIs.
 *
 * Utilise par :
 *   - service/ReclamationService : CRUD, transitions metier
 *   - service/DashboardService : countByStatut pour KPI "reclamations en attente"
 *   - controller/AdminReclamationController : liste paginee, details
 *
 * Methodes :
 *   - findByStatut : filtre pour la file d'attente admin
 *   - countByStatut : pour KPI dashboard
 * ============================================================================
 */
package com.example.locvoitures.repository;

import com.example.locvoitures.entity.Reclamation;
import com.example.locvoitures.enumeration.StatutReclamation;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ReclamationRepository extends JpaRepository<Reclamation, Long> {

    /**
     * File d'attente admin par statut (EN_TRAITEMENT ou CLOTUREE).
     */
    Page<Reclamation> findByStatut(StatutReclamation statut, Pageable pageable);

    /**
     * Compteur pour le KPI "reclamations en attente" du dashboard.
     */
    long countByStatut(StatutReclamation statut);
}

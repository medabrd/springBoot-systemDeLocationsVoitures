/*
 * ============================================================================
 * ClientRepository - Repository JPA pour Client
 * ============================================================================
 *
 * Role :
 *   Acces base de donnees pour l'entite Client (sous-classe de Utilisateur).
 *   Heritage JOINED : Hibernate joint utilisateur + client sur l'id partage.
 *
 * Utilise par :
 *   - service/ClientService : findById, findAll, MAJ profil
 *   - service/BannissementService : findByPermisNumero pour notification email
 *   - service/UtilisateurService : rechercher (recherche admin paginee)
 *   - service/DashboardService : countByActifTrue pour KPIs
 *   - controller/AdminUtilisateurController : liste paginee + recherche
 *
 * Methodes :
 *   - findByPermisNumero / findByNumeroCIN : recherche par documents
 *   - countByActifTrue : KPI clients actives
 *   - rechercher : full-text email/nom/prenom/permis avec pagination
 *
 * Note : findAll(Pageable) est herite de PagingAndSortingRepository, pas
 * besoin de le redeclarer ici.
 * ============================================================================
 */
package com.example.locvoitures.repository;

import com.example.locvoitures.entity.Client;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ClientRepository extends JpaRepository<Client, Long> {

    /**
     * Recherche par numero de permis (navigation OneToOne -> Permis.numero).
     */
    Optional<Client> findByPermisNumero(String numeroPermis);

    /**
     * Recherche par numero de CIN.
     */
    Optional<Client> findByNumeroCIN(String numeroCIN);

    /**
     * Compteur des clients actives (KPI dashboard).
     */
    long countByActifTrue();

    /**
     * Recherche full-text basique sur email/nom/prenom/numero permis.
     */
    @Query("""
        SELECT c FROM Client c
        LEFT JOIN c.permis p
        WHERE LOWER(c.email)   LIKE LOWER(CONCAT('%', :keyword, '%'))
           OR LOWER(c.nom)     LIKE LOWER(CONCAT('%', :keyword, '%'))
           OR LOWER(c.prenom)  LIKE LOWER(CONCAT('%', :keyword, '%'))
           OR LOWER(p.numero)  LIKE LOWER(CONCAT('%', :keyword, '%'))
    """)
    Page<Client> rechercher(@Param("keyword") String keyword, Pageable pageable);
}

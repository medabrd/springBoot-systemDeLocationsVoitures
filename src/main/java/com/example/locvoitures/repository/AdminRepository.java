/*
 * ============================================================================
 * AdminRepository - Repository JPA pour Admin
 * ============================================================================
 *
 * Role :
 *   Acces base pour les comptes Admin. Heritage JOINED -> Hibernate joint
 *   automatiquement utilisateur + admin sur l'id partage.
 *
 * Utilise par :
 *   - service/UtilisateurService.creerAdmin : save d'un nouvel Admin
 *   - service/ReclamationService : findAll pour notifier tous les admins
 *     d'une nouvelle reclamation
 *
 * Aucune methode derivee n'est ajoutee : le projet utilise findAll() / save()
 * heritees de JpaRepository, et les recherches par email passent par
 * UtilisateurRepository (polymorphe).
 * ============================================================================
 */
package com.example.locvoitures.repository;

import com.example.locvoitures.entity.Admin;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AdminRepository extends JpaRepository<Admin, Long> {
}

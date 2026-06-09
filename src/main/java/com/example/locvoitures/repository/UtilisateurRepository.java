/*
 * ============================================================================
 * UtilisateurRepository - Repository JPA polymorphe sur Utilisateur
 * ============================================================================
 *
 * Role :
 *   Acces base polymorphe pour la classe mere abstraite Utilisateur.
 *   Permet de rechercher un utilisateur par email sans se soucier de son
 *   type concret (Client ou Admin).
 *
 *   Spring Data instanciera Hibernate qui, grace au discriminator implicite
 *   de JOINED, retournera l'instance concrete (Client OU Admin) selon les
 *   tables presentes pour l'id donne.
 *
 * Utilise par :
 *   - service/CustomUserDetailsService.loadUserByUsername : findByEmail
 *     pour Spring Security (login)
 *   - controller/AuthUtil.getCurrentUser : recharge l'utilisateur courant
 *   - controller/GlobalControllerAdvice : meme chose pour enrichir le modele
 *   - service/UtilisateurService : findById, existsByEmail, findByToken*
 *
 * Note :
 *   La majorite des methodes specifiques au profil client sont sur
 *   ClientRepository, et symetriquement sur AdminRepository pour l'admin.
 *   UtilisateurRepository sert principalement aux operations polymorphes
 *   (auth, gestion des tokens, listing global).
 * ============================================================================
 */
package com.example.locvoitures.repository;

import com.example.locvoitures.entity.Utilisateur;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UtilisateurRepository extends JpaRepository<Utilisateur, Long> {

    /**
     * Point d'entree authentification : findByEmail appele par
     * CustomUserDetailsService a chaque tentative de login.
     * Retour Optional<Utilisateur> : peut etre un Client OU un Admin selon
     * la table jointe par Hibernate.
     */
    Optional<Utilisateur> findByEmail(String email);

    /**
     * Verification d'unicite email avant inscription.
     */
    boolean existsByEmail(String email);

    /**
     * Recherche par token d'activation (flow inscription client).
     */
    Optional<Utilisateur> findByTokenActivation(String token);

    /**
     * Recherche par token de reinitialisation de mot de passe.
     */
    Optional<Utilisateur> findByTokenReinitialisation(String token);
}

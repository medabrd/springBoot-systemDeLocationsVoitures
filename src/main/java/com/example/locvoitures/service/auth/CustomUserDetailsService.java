/*
 * ============================================================================
 * CustomUserDetailsService - Pont Spring Security <-> entite Utilisateur
 * ============================================================================
 *
 * Role :
 *   Implementation de UserDetailsService de Spring Security. A chaque
 *   tentative de login, Spring Security appelle loadUserByUsername(email)
 *   pour recuperer un UserDetails (password hash + roles) qu'il compare
 *   au mot de passe saisi via le PasswordEncoder.
 *
 *   Le role Spring Security ("ADMIN" / "CLIENT") est deduit du type Java
 *   de l'utilisateur recupere : instanceof Admin -> "ADMIN", sinon "CLIENT".
 *   Cela remplace l'ancien champ enum Role qui a ete supprime au profit
 *   de l'heritage JPA.
 *
 * Appele depuis :
 *   - Spring Security en interne (DaoAuthenticationProvider) a chaque login
 *
 * Appelle :
 *   - UtilisateurRepository.findByEmail : recuperation polymorphe (Client
 *     ou Admin selon la table jointe par Hibernate)
 *
 * Comportement :
 *   - Email inconnu     -> UsernameNotFoundException
 *   - Compte non actif  -> DisabledException
 *   - Compte actif      -> UserDetails avec authority ROLE_ADMIN ou ROLE_CLIENT
 * ============================================================================
 */
package com.example.locvoitures.service.auth;

import com.example.locvoitures.entity.Admin;
import com.example.locvoitures.entity.Utilisateur;
import com.example.locvoitures.repository.UtilisateurRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.security.authentication.DisabledException;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    private final UtilisateurRepository utilisateurRepo;

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        // 1) Recuperer l'utilisateur (polymorphe : Client ou Admin).
        Utilisateur user = utilisateurRepo.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("Email ou mot de passe incorrect"));

        // 2) Compte actif ?
        if (!user.isActif()) {
            throw new DisabledException("Compte non active. Veuillez consulter votre email pour activer votre compte.");
        }

        // 3) Determination du role Spring Security via le type Java :
        //    - Admin  -> ROLE_ADMIN
        //    - sinon (= Client) -> ROLE_CLIENT
        //    Le prefixe "ROLE_" est ajoute automatiquement par .roles(...)
        String role = (user instanceof Admin) ? "ADMIN" : "CLIENT";

        return User.builder()
                .username(user.getEmail())
                .password(user.getMotDePasse())
                .roles(role)
                .build();
    }
}

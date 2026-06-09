/*
 * ============================================================================
 * AuthUtil - Helper pour recuperer l'utilisateur courant depuis la session
 * ============================================================================
 *
 * Role :
 *   Centralise l'acces au SecurityContext de Spring Security pour
 *   recuperer l'entite Utilisateur courante (sous-classe Client OU Admin
 *   selon le compte connecte).
 *
 *   Trois helpers :
 *     - getCurrentUser()  -> Utilisateur (polymorphe)
 *     - getCurrentClient() -> Client (cast + check instanceof)
 *     - getCurrentAdmin() -> Admin (cast + check instanceof)
 *
 *   Les helpers typed levent une BusinessException si le compte connecte
 *   n'est pas du bon type (defense en profondeur, normalement protege
 *   par hasRole() dans SecurityConfig).
 *
 * Appele depuis :
 *   - ClientController : getCurrentClient, getCurrentUser
 *   - ClientLocationController : getCurrentClient pour scope les requetes
 *   - AdminUtilisateurController : getCurrentAdmin pour audit (qui banni)
 * ============================================================================
 */
package com.example.locvoitures.controller.advice;

import com.example.locvoitures.entity.Admin;
import com.example.locvoitures.entity.Client;
import com.example.locvoitures.entity.Utilisateur;
import com.example.locvoitures.exception.BusinessException;
import com.example.locvoitures.service.auth.UtilisateurService;

import lombok.RequiredArgsConstructor;

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AuthUtil {

    private final UtilisateurService utilisateurService;

    /**
     * Retourne l'Utilisateur courant (polymorphe : Client ou Admin selon
     * la sous-classe materialisee par Hibernate via le JOIN).
     */
    public Utilisateur getCurrentUser() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || "anonymousUser".equals(auth.getPrincipal())) {
            throw new BusinessException("Aucun utilisateur connecte");
        }
        return utilisateurService.findByEmail(auth.getName())
                .orElseThrow(() -> new BusinessException("Compte introuvable"));
    }

    /**
     * Retourne le Client courant. BusinessException si le compte connecte
     * n'est pas un Client (mais un Admin par exemple).
     */
    public Client getCurrentClient() {
        Utilisateur user = getCurrentUser();
        if (!(user instanceof Client client)) {
            throw new BusinessException("Profil client introuvable pour ce compte");
        }
        return client;
    }

    /**
     * Retourne l'Admin courant. BusinessException si le compte connecte
     * n'est pas un Admin.
     */
    public Admin getCurrentAdmin() {
        Utilisateur user = getCurrentUser();
        if (!(user instanceof Admin admin)) {
            throw new BusinessException("Profil administrateur introuvable pour ce compte");
        }
        return admin;
    }
}

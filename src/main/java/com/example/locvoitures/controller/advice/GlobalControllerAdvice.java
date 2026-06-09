/*
 * ============================================================================
 * GlobalControllerAdvice - Enrichit le modele de chaque page automatiquement
 * ============================================================================
 *
 * Role :
 *   Avant chaque rendu de page, ajoute des attributs au modele Thymeleaf
 *   utilises par le layout commun (navbar, bandeau d'alerte) :
 *   - connectedUser : entite Utilisateur du compte connecte (Client OU Admin)
 *   - clientBanni   : booleen pour afficher le bandeau "Compte suspendu"
 *   - motifBan      : motif du bannissement (si applicable)
 *
 * @Transactional(readOnly=true) : necessaire pour les acces lazy.
 * ============================================================================
 */
package com.example.locvoitures.controller.advice;

import com.example.locvoitures.entity.Client;
import com.example.locvoitures.service.auth.UtilisateurService;
import com.example.locvoitures.service.metier.PermisService;

import lombok.RequiredArgsConstructor;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

@ControllerAdvice
@RequiredArgsConstructor
public class GlobalControllerAdvice {

    private final UtilisateurService utilisateurService;
    private final PermisService permisService;

    @ModelAttribute
    @Transactional(readOnly = true)
    public void enrichirModele(Model model) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
            return;
        }

        utilisateurService.findByEmail(auth.getName()).ifPresent(user -> {
            model.addAttribute("connectedUser", user);

            if (user instanceof Client client) {
                boolean banni = client.getNumeroPermis() != null
                        && permisService.estPermisBanni(client.getNumeroPermis());
                model.addAttribute("clientBanni", banni);
                if (banni) {
                    permisService.findBanniByNumero(client.getNumeroPermis())
                            .ifPresent(b -> model.addAttribute("motifBan", b.getMotifBan()));
                }
            } else {
                model.addAttribute("clientBanni", false);
            }
        });
    }
}

/*
 * ============================================================================
 * HomeController - Routes d'accueil et pages d'erreur
 * ============================================================================
 *
 * Role :
 *   Gere :
 *   - La route racine "/" qui redirige selon l'etat (connecte/anonyme,
 *     CLIENT/ADMIN)
 *   - Les pages d'erreur custom 403/404/500 mappees depuis le filter chain
 *     Spring (via SecurityConfig.accessDeniedPage et server.error.path)
 *
 * Appelle :
 *   - SecurityContextHolder : determine l'etat de la session
 *
 * Pas de dependance metier directe (controller "infrastructure").
 *
 * Redirection role-based depuis "/" :
 *   - non connecte -> /auth/login
 *   - ADMIN -> /admin/dashboard
 *   - CLIENT -> /client/accueil
 *
 * Pages erreur :
 *   Les templates correspondants sont sous templates/erreur/*.html
 *   et utilisent le layout commun (avec navbar).
 * ============================================================================
 */
package com.example.locvoitures.controller.home;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
// Model : conteneur de variables pour le rendu template
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
public class HomeController {

    /**
     * "/" et "/index" : redirige vers le bon espace selon le role connecte,
     * ou affiche la page d'accueil publique pour un visiteur.
     
     * GetMapping accepte un array de paths pour mapper plusieurs URLs sur
     * la meme methode.
     */
    @GetMapping({"/", "/index"})
    public String home() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        // Cas non authentifie -> page de login
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
            return "redirect:/auth/login";
        }
        // Authentifie : detection du role pour rediriger au bon dashboard
        boolean isAdmin = auth.getAuthorities().stream()
                .anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
        return isAdmin ? "redirect:/admin/dashboard" : "redirect:/client/accueil";
    }

    /**
     * Page 403 (acces refuse). Mappee par SecurityConfig.accessDeniedPage.
     */
    @RequestMapping("/erreur/403")
    public String erreur403(Model model) {
        model.addAttribute("message", "Vous n'avez pas la permission d'acceder a cette page.");
        return "erreur/403";
    }

    /**
     * Page 404 (ressource introuvable).
     */
    @RequestMapping("/erreur/404")
    public String erreur404(Model model) {
        model.addAttribute("message", "La ressource demandee est introuvable.");
        return "erreur/404";
    }

    /**
     * Page 500 (erreur serveur, message generique).
     */
    @RequestMapping("/erreur/500")
    public String erreur500(Model model) {
        model.addAttribute("message", "Une erreur serveur s'est produite.");
        return "erreur/500";
    }
}

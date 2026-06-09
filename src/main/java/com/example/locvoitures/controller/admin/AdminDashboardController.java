/*
 * ============================================================================
 * AdminDashboardController - Page d'accueil admin (KPIs)
 * ============================================================================
 *
 * Role :
 *   Affiche le dashboard admin avec tous les KPIs (flotte, locations,
 *   utilisateurs, qualite) + graphiques.
 *
 * Mapping :
 *   /admin/dashboard (et /admin -> alias)
 *   Protege par hasRole("ADMIN").
 *
 * Appelle :
 *   - DashboardService.buildDashboard : construit le DTO agrege
 *
 * Le template templates/admin/dashboard.html consomme dashboard.* pour
 * afficher chaque carte/graphique.
 * ============================================================================
 */
package com.example.locvoitures.controller.admin;

import com.example.locvoitures.service.utils.DashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/admin")
@RequiredArgsConstructor
public class AdminDashboardController {

    private final DashboardService dashboardService;

    /**
     * Deux routes mappees : "/admin/dashboard" et "/admin" (alias racine
     * espace admin).
     */
    @GetMapping({"/dashboard", ""})
    public String dashboard(Model model) {
        model.addAttribute("dashboard", dashboardService.buildDashboard());
        return "admin/dashboard";
    }
}

/*
 * ============================================================================
 * AdminAvisController - Moderation des avis cote admin
 * ============================================================================
 *
 * Role :
 *   Page de moderation des avis clients. Permet de lister tous les avis
 *   (page paginee) et de supprimer un avis abusif.
 *   Pas de creation/modification d'avis cote admin (les avis sont deposes
 *   par les clients).
 *
 * Mapping :
 *   /admin/avis : liste paginee (10 par page, tri anti-chrono)
 *   /admin/avis/{id}/supprimer : POST suppression
 *
 * Appelle :
 *   - AvisService : findAll(pageable), supprimerAvis
 * ============================================================================
 */
package com.example.locvoitures.controller.admin;

import com.example.locvoitures.entity.Avis;
import com.example.locvoitures.exception.BusinessException;
import com.example.locvoitures.service.metier.AvisService;

import lombok.RequiredArgsConstructor;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/admin/avis")
@RequiredArgsConstructor
public class AdminAvisController {

    private static final int PAGE_SIZE = 10;

    private final AvisService avisService;

    /**
     * Liste paginee, tri anti-chronologique (avis recents en haut).
     */
    @GetMapping
    public String liste(@RequestParam(defaultValue = "0") int page, Model model) {
        Page<Avis> avis = avisService.findAll(
                PageRequest.of(page, PAGE_SIZE, Sort.by("dateCreation").descending()));
        model.addAttribute("avisList", avis);
        model.addAttribute("urlBase", "/admin/avis");
        return "admin/avis/liste";
    }

    /**
     * Suppression d'un avis (action de moderation).
     */
    @PostMapping("/{id}/supprimer")
    public String supprimer(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            avisService.supprimerAvis(id);
            redirectAttributes.addFlashAttribute("successMessage", "Avis supprime");
        } catch (BusinessException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/admin/avis";
    }
}

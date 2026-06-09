/*
 * ============================================================================
 * AdminCategorieController - CRUD des categories cote admin
 * ============================================================================
 *
 * Role :
 *   Gere les operations CRUD sur les categories de voitures (Citadine,
 *   Berline, SUV...). Toutes les operations sur la meme page (liste +
 *   formulaires inline d'edition).
 *
 * Mapping :
 *   /admin/categories (liste + creation)
 *   /admin/categories/{id}/modifier (POST modification)
 *   /admin/categories/{id}/supprimer (POST suppression)
 *
 * Appelle :
 *   - CategorieService : findAll, creer, modifier, supprimer
 *
 * Pattern :
 *   PRG avec flash messages. En cas d'erreur de validation @Valid sur la
 *   creation, on re-affiche la liste avec les erreurs (pas de redirect
 *   pour preserver BindingResult).
 * ============================================================================
 */
package com.example.locvoitures.controller.admin;

import com.example.locvoitures.entity.Categorie;
import com.example.locvoitures.exception.BusinessException;
import com.example.locvoitures.service.metier.CategorieService;

import jakarta.validation.Valid;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/admin/categories")
@RequiredArgsConstructor
public class AdminCategorieController {

    private final CategorieService categorieService;

    /**
     * Liste + formulaire de creation inline.
     */
    @GetMapping
    public String liste(Model model) {
        model.addAttribute("categories", categorieService.findAll());
        // DTO vide pour le formulaire de creation (sauf si re-affichage avec erreurs)
        if (!model.containsAttribute("categorie")) {
            model.addAttribute("categorie", new Categorie());
        }
        return "admin/categories/liste";
    }

    /**
     * Creation d'une nouvelle categorie.
     */
    @PostMapping
    public String creer(@Valid @ModelAttribute("categorie") Categorie categorie,
                         BindingResult bindingResult,
                         RedirectAttributes redirectAttributes,
                         Model model) {
        if (bindingResult.hasErrors()) {
            model.addAttribute("categories", categorieService.findAll());
            return "admin/categories/liste";
        }
        try {
            categorieService.creer(categorie);
            redirectAttributes.addFlashAttribute("successMessage", "Categorie creee");
        } catch (BusinessException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/admin/categories";
    }

    /**
     * Modification d'une categorie existante. Champs via @RequestParam
     * (formulaire inline simple, pas de DTO dedie).
     */
    @PostMapping("/{id}/modifier")
    public String modifier(@PathVariable Long id,
                            @RequestParam String nom,
                            @RequestParam(required = false) String description,
                            RedirectAttributes redirectAttributes) {
        try {
            Categorie maj = new Categorie();
            maj.setNom(nom);
            maj.setDescription(description);
            categorieService.modifier(id, maj);
            redirectAttributes.addFlashAttribute("successMessage", "Categorie modifiee");
        } catch (BusinessException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/admin/categories";
    }

    /**
     * Suppression d'une categorie. Refusee si utilisee par >= 1 voiture.
     */
    @PostMapping("/{id}/supprimer")
    public String supprimer(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            categorieService.supprimer(id);
            redirectAttributes.addFlashAttribute("successMessage", "Categorie supprimee");
        } catch (BusinessException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/admin/categories";
    }
}

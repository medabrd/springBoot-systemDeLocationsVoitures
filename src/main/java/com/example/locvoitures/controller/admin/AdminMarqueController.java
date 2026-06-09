/*
 * ============================================================================
 * AdminMarqueController - CRUD des marques cote admin
 * ============================================================================
 *
 * Role :
 *   Gere les operations CRUD sur les marques de vehicules. Specificite :
 *   gere l'upload du logo (MultipartFile) en plus du champ nom.
 *
 * Mapping :
 *   /admin/marques
 *   /admin/marques/{id}/modifier (POST)
 *   /admin/marques/{id}/supprimer (POST)
 *
 * Appelle :
 *   - MarqueService : findAll, creer, modifier, supprimer (gere l'upload)
 *
 * Particularite logo :
 *   La methode service prend (nom, MultipartFile) directement. Pas de DTO
 *   intermediaire car le formulaire est simple (un champ texte + un fichier).
 * ============================================================================
 */
package com.example.locvoitures.controller.admin;

import com.example.locvoitures.exception.BusinessException;
import com.example.locvoitures.service.metier.MarqueService;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/admin/marques")
@RequiredArgsConstructor
public class AdminMarqueController {

    private final MarqueService marqueService;

    @GetMapping
    public String liste(Model model) {
        model.addAttribute("marques", marqueService.findAll());
        return "admin/marques/liste";
    }

    /**
     * Creation : nom obligatoire, logo optionnel.
     */
    @PostMapping
    public String creer(@RequestParam String nom,
                         @RequestParam(value = "logo", required = false) MultipartFile logo,
                         RedirectAttributes redirectAttributes) {
        try {
            marqueService.creer(nom, logo);
            redirectAttributes.addFlashAttribute("successMessage", "Marque creee");
        } catch (BusinessException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/admin/marques";
    }

    /**
     * Modification : si nouveau logo fourni, remplace l'ancien.
     */
    @PostMapping("/{id}/modifier")
    public String modifier(@PathVariable Long id,
                            @RequestParam String nom,
                            @RequestParam(value = "logo", required = false) MultipartFile logo,
                            RedirectAttributes redirectAttributes) {
        try {
            marqueService.modifier(id, nom, logo);
            redirectAttributes.addFlashAttribute("successMessage", "Marque modifiee");
        } catch (BusinessException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/admin/marques";
    }

    /**
     * Suppression. Refusee par le service si marque utilisee par des voitures.
     */
    @PostMapping("/{id}/supprimer")
    public String supprimer(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            marqueService.supprimer(id);
            redirectAttributes.addFlashAttribute("successMessage", "Marque supprimee");
        } catch (BusinessException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/admin/marques";
    }
}

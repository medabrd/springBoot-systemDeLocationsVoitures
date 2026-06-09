/*
 * ============================================================================
 * AdminEquipementController - CRUD des equipements cote admin
 * ============================================================================
 *
 * Role :
 *   Gere les operations CRUD sur les equipements (Climatisation, GPS...).
 *   Pattern identique a AdminCategorieController.
 *
 * Mapping :
 *   /admin/equipements
 *   /admin/equipements/{id}/modifier (POST)
 *   /admin/equipements/{id}/supprimer (POST)
 *
 * Appelle :
 *   - EquipementService : findAll, creer, modifier, supprimer
 *
 * Pattern :
 *   PRG + flash messages. Liste + formulaire inline.
 * ============================================================================
 */
package com.example.locvoitures.controller.admin;

import com.example.locvoitures.entity.Equipement;
import com.example.locvoitures.exception.BusinessException;
import com.example.locvoitures.service.metier.EquipementService;

import jakarta.validation.Valid;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/admin/equipements")
@RequiredArgsConstructor
public class AdminEquipementController {

    private final EquipementService equipementService;

    @GetMapping
    public String liste(Model model) {
        model.addAttribute("equipements", equipementService.findAll());
        if (!model.containsAttribute("equipement")) {
            model.addAttribute("equipement", new Equipement());
        }
        return "admin/equipements/liste";
    }

    @PostMapping
    public String creer(@Valid @ModelAttribute("equipement") Equipement equipement,
                         BindingResult bindingResult,
                         RedirectAttributes redirectAttributes,
                         Model model) {
        if (bindingResult.hasErrors()) {
            model.addAttribute("equipements", equipementService.findAll());
            return "admin/equipements/liste";
        }
        try {
            equipementService.creer(equipement);
            redirectAttributes.addFlashAttribute("successMessage", "Equipement cree");
        } catch (BusinessException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/admin/equipements";
    }

    @PostMapping("/{id}/modifier")
    public String modifier(@PathVariable Long id,
                            @RequestParam String nom,
                            @RequestParam(required = false) String description,
                            RedirectAttributes redirectAttributes) {
        try {
            Equipement maj = new Equipement();
            maj.setNom(nom);
            maj.setDescription(description);
            equipementService.modifier(id, maj);
            redirectAttributes.addFlashAttribute("successMessage", "Equipement modifie");
        } catch (BusinessException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/admin/equipements";
    }

    @PostMapping("/{id}/supprimer")
    public String supprimer(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            equipementService.supprimer(id);
            redirectAttributes.addFlashAttribute("successMessage", "Equipement supprime");
        } catch (BusinessException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/admin/equipements";
    }
}

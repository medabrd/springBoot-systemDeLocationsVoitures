/*
 * ============================================================================
 * AdminReclamationController - Gestion des reclamations cote admin
 * ============================================================================
 *
 * Role :
 *   - Liste paginee avec filtre par statut (EN_TRAITEMENT / CLOTUREE)
 *   - Detail d'une reclamation avec formulaire de cloture
 *   - Cloture avec reponse facultative (envoie mail si reponse renseignee)
 *   - Suppression (moderation)
 *
 * Mapping :
 *   /admin/reclamations[?statut=...]
 *   /admin/reclamations/{id}
 *   /admin/reclamations/{id}/repondre (POST)
 *   /admin/reclamations/{id}/supprimer (POST)
 *
 * Appelle :
 *   - ReclamationService : findAll, findByStatut, findById, repondre, supprimer
 *
 * Filtre par statut :
 *   Le query param statut est optionnel. S'il est present, findByStatut,
 *   sinon findAll. UriComponentsBuilder preserve le filtre dans la pagination.
 *
 * Cloture flexible :
 *   ReponseReclamationForm.reponse est facultative. Si renseignee : mail au
 *   client + cloture. Sinon : cloture silencieuse.
 * ============================================================================
 */
package com.example.locvoitures.controller.admin;

import com.example.locvoitures.dto.form.ReponseReclamationForm;
import com.example.locvoitures.entity.Reclamation;
import com.example.locvoitures.enumeration.StatutReclamation;
import com.example.locvoitures.exception.BusinessException;
import com.example.locvoitures.service.metier.ReclamationService;

import jakarta.validation.Valid;

import lombok.RequiredArgsConstructor;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.util.UriComponentsBuilder;

@Controller
@RequestMapping("/admin/reclamations")
@RequiredArgsConstructor
public class AdminReclamationController {

    private static final int PAGE_SIZE = 10;

    private final ReclamationService reclamationService;

    /**
     * Liste paginee filtree optionnellement par statut.
     */
    @GetMapping
    public String liste(@RequestParam(required = false) StatutReclamation statut,
                         @RequestParam(defaultValue = "0") int page,
                         Model model) {
        PageRequest pageable = PageRequest.of(page, PAGE_SIZE, Sort.by("dateCreation").descending());
        // Branche conditionnelle : filtre statut ou non
        Page<Reclamation> reclamations = (statut != null)
                ? reclamationService.findByStatut(statut, pageable)
                : reclamationService.findAll(pageable);

        model.addAttribute("reclamations", reclamations);
        model.addAttribute("filtreStatut", statut);
        model.addAttribute("statuts", StatutReclamation.values());
        // URL de base preservant le filtre statut pour la pagination
        model.addAttribute("urlBase",
                UriComponentsBuilder.fromPath("/admin/reclamations")
                        .queryParamIfPresent("statut", java.util.Optional.ofNullable(statut))
                        .toUriString());
        return "admin/reclamations/liste";
    }

    /**
     * Detail d'une reclamation + formulaire de cloture.
     */
    @GetMapping("/{id}")
    public String detail(@PathVariable Long id, Model model) {
        model.addAttribute("reclamation", reclamationService.findById(id));
        if (!model.containsAttribute("reponseForm")) {
            model.addAttribute("reponseForm", new ReponseReclamationForm());
        }
        return "admin/reclamations/detail";
    }

    /**
     * Cloture avec reponse facultative.
     */
    @PostMapping("/{id}/repondre")
    public String repondre(@PathVariable Long id,
                            @Valid @ModelAttribute("reponseForm") ReponseReclamationForm form,
                            BindingResult bindingResult,
                            RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            redirectAttributes.addFlashAttribute("errorMessage", "Reponse trop longue (1000 caracteres max)");
            return "redirect:/admin/reclamations/" + id;
        }
        try {
            // Distingue message succes selon presence de la reponse
            boolean avecReponse = form.getReponse() != null && !form.getReponse().isBlank();
            reclamationService.repondre(id, form.getReponse());
            redirectAttributes.addFlashAttribute("successMessage",
                    avecReponse
                        ? "Reponse envoyee au client. La reclamation est cloturee."
                        : "Reclamation cloturee sans envoi de message.");
        } catch (BusinessException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/admin/reclamations/" + id;
    }

    /**
     * Suppression d'une reclamation (moderation).
     */
    @PostMapping("/{id}/supprimer")
    public String supprimer(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            reclamationService.supprimer(id);
            redirectAttributes.addFlashAttribute("successMessage", "Reclamation supprimee");
        } catch (BusinessException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/admin/reclamations";
    }
}

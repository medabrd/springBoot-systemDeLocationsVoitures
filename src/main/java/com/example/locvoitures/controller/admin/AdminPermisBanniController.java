/*
 * ============================================================================
 * AdminPermisBanniController - Gestion de la liste noire des permis bannis
 * ============================================================================
 *
 * Role :
 *   Ecran dedie a la gestion globale des permis bannis (independamment d'un
 *   compte client particulier). L'admin peut :
 *   - Lister tous les permis bannis (avec motif, date, admin qui a banni)
 *   - Ajouter un permis a la liste noire (numero + motif)
 *   - Retirer un permis de la liste noire
 *
 * Mapping :
 *   /admin/permis-bannis            (GET liste)
 *   /admin/permis-bannis            (POST ajouter)
 *   /admin/permis-bannis/supprimer  (POST retirer)
 *
 * Differences avec AdminUtilisateurController.bannir :
 *   - L'ecran client cible un compte (le permis du client est connu).
 *   - Cet ecran est global : on saisit un numero de permis "brut", meme s'il
 *     n'y a pas de compte associe en base.
 *
 * Appelle :
 *   - BannissementService : findAll, bannir, debannir
 *   - AuthUtil.getCurrentAdmin : audit (qui a banni)
 * ============================================================================
 */
package com.example.locvoitures.controller.admin;

import com.example.locvoitures.controller.advice.AuthUtil;

import com.example.locvoitures.dto.form.BanForm;
import com.example.locvoitures.entity.Permis;
import com.example.locvoitures.exception.BusinessException;
import com.example.locvoitures.service.metier.PermisService;

import jakarta.validation.Valid;

import lombok.RequiredArgsConstructor;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/admin/permis-bannis")
@RequiredArgsConstructor
public class AdminPermisBanniController {

    private static final int PAGE_SIZE = 15;

    private final PermisService permisService;
    private final AuthUtil authUtil;

    /**
     * Liste paginee des permis bannis. Tri anti-chrono sur dateBan
     * (les plus recents en premier).
     */
    @GetMapping
    public String liste(@RequestParam(defaultValue = "0") int page, Model model) {
        Page<Permis> bannis = permisService.findAllBannis(
                PageRequest.of(page, PAGE_SIZE, Sort.by("dateBan").descending()));
        model.addAttribute("bannis", bannis);
        model.addAttribute("urlBase", "/admin/permis-bannis");
        if (!model.containsAttribute("banForm")) {
            model.addAttribute("banForm", new BanForm());
        }
        return "admin/permis-bannis/liste";
    }

    /**
     * Ajoute un permis a la liste noire.
     */
    @PostMapping
    public String ajouter(@Valid @ModelAttribute("banForm") BanForm form,
                           BindingResult bindingResult,
                           RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            redirectAttributes.addFlashAttribute("errorMessage",
                    "Numero de permis et motif sont obligatoires");
            return "redirect:/admin/permis-bannis";
        }
        try {
            permisService.bannir(form.getNumeroPermis(), form.getMotif(),
                    authUtil.getCurrentAdmin());
            redirectAttributes.addFlashAttribute("successMessage",
                    "Permis " + form.getNumeroPermis() + " ajoute a la liste noire.");
        } catch (BusinessException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/admin/permis-bannis";
    }

    /**
     * Retire un permis de la liste noire.
     */
    @PostMapping("/supprimer")
    public String supprimer(@RequestParam String numeroPermis,
                             RedirectAttributes redirectAttributes) {
        try {
            permisService.debannir(numeroPermis);
            redirectAttributes.addFlashAttribute("successMessage",
                    "Permis " + numeroPermis + " retire de la liste noire.");
        } catch (BusinessException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/admin/permis-bannis";
    }
}

/*
 * ============================================================================
 * AdminUtilisateurController - Gestion des comptes clients cote admin
 * ============================================================================
 *
 * Role :
 *   - Liste paginee + recherche full-text des clients
 *   - Creation d'un compte client (client qui se presente au bureau)
 *   - Fiche detail (compte + profil + statut ban)
 *   - Bannir / Debannir un permis depuis la fiche
 *   - Supprimer un compte (locations decouplees)
 *
 * Mapping : /admin/utilisateurs[/...]
 *
 * Appelle :
 *   - ClientService : liste, creation, suppression, lookup
 *   - PermisService : ban / debannir / statut depuis la fiche
 *   - AuthUtil : recupere l'admin courant pour l'audit du ban
 * ============================================================================
 */
package com.example.locvoitures.controller.admin;

import com.example.locvoitures.controller.advice.AuthUtil;

import com.example.locvoitures.dto.form.BanForm;
import com.example.locvoitures.dto.form.CreerClientForm;
import com.example.locvoitures.entity.Client;
import com.example.locvoitures.exception.BusinessException;
import com.example.locvoitures.service.metier.ClientService;
import com.example.locvoitures.service.metier.PermisService;

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
@RequestMapping("/admin/utilisateurs")
@RequiredArgsConstructor
public class AdminUtilisateurController {

    private static final int PAGE_SIZE = 10;

    private final ClientService clientService;
    private final PermisService permisService;
    private final AuthUtil authUtil;

    /**
     * Liste paginee des clients avec recherche full-text.
     * On precharge le Set des numeros bannis pour afficher un badge "Banni"
     * sans faire N requetes.
     */
    @GetMapping
    public String liste(@RequestParam(required = false) String keyword,
                         @RequestParam(defaultValue = "0") int page,
                         Model model) {
        Page<Client> clients = clientService.rechercher(keyword,
                PageRequest.of(page, PAGE_SIZE, Sort.by("dateInscription").descending()));

        model.addAttribute("utilisateurs", clients);
        model.addAttribute("keyword", keyword);
        model.addAttribute("permisBannis", permisService.findAllNumerosBannis());
        model.addAttribute("urlBase",
                UriComponentsBuilder.fromPath("/admin/utilisateurs")
                        .queryParamIfPresent("keyword",
                                java.util.Optional.ofNullable(keyword).filter(s -> !s.isBlank()))
                        .toUriString());
        return "admin/utilisateurs/liste";
    }

    /**
     * Formulaire de creation d'un compte client par l'admin.
     */
    @GetMapping("/nouveau")
    public String formulaireCreation(Model model) {
        if (!model.containsAttribute("creerForm")) {
            model.addAttribute("creerForm", new CreerClientForm());
        }
        return "admin/utilisateurs/nouveau";
    }

    /**
     * Traite la creation. Mot de passe genere cote service et envoye par mail.
     */
    @PostMapping("/nouveau")
    public String creer(@Valid @ModelAttribute("creerForm") CreerClientForm form,
                         BindingResult bindingResult,
                         RedirectAttributes redirectAttributes,
                         Model model) {
        if (bindingResult.hasErrors()) {
            return "admin/utilisateurs/nouveau";
        }
        try {
            Client cree = clientService.creerParAdmin(
                    form.getEmail(), form.getNom(), form.getPrenom(),
                    form.getTelephone(),
                    form.getNumeroPermis(),
                    form.getDateObtentionPermis(),
                    form.getDateExpirationPermis(),
                    form.getNumeroCIN());
            redirectAttributes.addFlashAttribute("successMessage",
                    "Compte cree. Le mot de passe a ete envoye a " + cree.getEmail());
            return "redirect:/admin/utilisateurs/" + cree.getId();
        } catch (BusinessException e) {
            model.addAttribute("errorMessage", e.getMessage());
            return "admin/utilisateurs/nouveau";
        }
    }

    /**
     * Fiche detail. Charge le statut ban + motif si applicable.
     */
    @GetMapping("/{id}")
    public String detail(@PathVariable Long id, Model model) {
        Client client = clientService.findById(id);
        model.addAttribute("utilisateur", client);
        if (client.getNumeroPermis() != null) {
            model.addAttribute("estBanni",
                    permisService.estPermisBanni(client.getNumeroPermis()));
            permisService.findBanniByNumero(client.getNumeroPermis())
                    .ifPresent(b -> model.addAttribute("permisBanni", b));
        }
        if (!model.containsAttribute("banForm")) {
            BanForm banForm = new BanForm();
            banForm.setNumeroPermis(client.getNumeroPermis());
            model.addAttribute("banForm", banForm);
        }
        return "admin/utilisateurs/detail";
    }

    /**
     * Bannit le permis du client. authUtil.getCurrentAdmin() trace l'audit.
     */
    @PostMapping("/{id}/bannir")
    public String bannir(@PathVariable Long id,
                          @Valid @ModelAttribute("banForm") BanForm form,
                          BindingResult bindingResult,
                          RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            redirectAttributes.addFlashAttribute("errorMessage", "Tous les champs sont obligatoires");
            return "redirect:/admin/utilisateurs/" + id;
        }
        try {
            permisService.bannir(form.getNumeroPermis(), form.getMotif(), authUtil.getCurrentAdmin());
            redirectAttributes.addFlashAttribute("successMessage",
                    "Utilisateur banni. Il a ete notifie par email.");
        } catch (BusinessException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/admin/utilisateurs/" + id;
    }

    /**
     * Leve le bannissement d'un permis.
     */
    @PostMapping("/{id}/debannir")
    public String debannir(@PathVariable Long id,
                            @RequestParam String numeroPermis,
                            RedirectAttributes redirectAttributes) {
        try {
            permisService.debannir(numeroPermis);
            redirectAttributes.addFlashAttribute("successMessage",
                    "Utilisateur debanni. Il a ete notifie par email.");
        } catch (BusinessException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/admin/utilisateurs/" + id;
    }

    /**
     * Suppression du compte client.
     */
    @PostMapping("/{id}/supprimer")
    public String supprimer(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            clientService.supprimerParAdmin(id);
            redirectAttributes.addFlashAttribute("successMessage", "Compte client supprime");
            return "redirect:/admin/utilisateurs";
        } catch (BusinessException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
            return "redirect:/admin/utilisateurs/" + id;
        }
    }
}

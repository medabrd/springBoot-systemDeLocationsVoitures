/*
 * ============================================================================
 * AdminVoitureController - Gestion de la flotte de voitures cote admin
 * ============================================================================
 *
 * Role :
 *   CRUD complet sur les voitures cote admin :
 *   - Liste paginee avec filtres multi-criteres (memes que catalogue client
 *     mais SANS le filtre statut ACTIVE force et SANS le filtre dates)
 *   - Detail d'une voiture (vue admin enrichie)
 *   - Creation (formulaire avec upload photo)
 *   - Modification
 *   - Suppression (decouple les locations -> Location.voiture = null)
 *
 * Mapping :
 *   /admin/voitures (liste avec filtres)
 *   /admin/voitures/{id} (detail)
 *   /admin/voitures/nouveau (GET formulaire creation)
 *   POST /admin/voitures (creation)
 *   GET /admin/voitures/{id}/modifier (formulaire edition)
 *   POST /admin/voitures/{id} (mise a jour)
 *   POST /admin/voitures/{id}/supprimer
 *
 * Appelle :
 *   - VoitureService : filtrer, findById, creer, modifier, supprimer
 *   - MarqueService, CategorieService, EquipementService : findAll/findById
 *     pour resoudre les references du formulaire
 *
 * DTO :
 *   VoitureForm encapsule tous les champs du formulaire (y compris details
 *   techniques aplatis). toVoiture()/fromVoiture(v) font la conversion.
 *
 * Detail admin :
 *   Apres feedback "le bouton voir mene au catalogue/{id}", la fiche admin
 *   /admin/voitures/{id} affiche tous les details avec un bouton "Modifier"
 *   plutot que rediriger vers le catalogue client.
 * ============================================================================
 */
package com.example.locvoitures.controller.admin;

import com.example.locvoitures.dto.form.VoitureForm;
import com.example.locvoitures.entity.Voiture;
import com.example.locvoitures.enumeration.Carburant;
import com.example.locvoitures.enumeration.StatutVoiture;
import com.example.locvoitures.enumeration.Transmission;
import com.example.locvoitures.exception.BusinessException;
import com.example.locvoitures.service.auth.*;
import com.example.locvoitures.service.metier.*;
import com.example.locvoitures.service.utils.*;

import jakarta.validation.Valid;

import lombok.RequiredArgsConstructor;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.util.UriComponentsBuilder;

import java.math.BigDecimal;
import java.util.List;

@Controller
@RequestMapping("/admin/voitures")
@RequiredArgsConstructor
public class AdminVoitureController {

    private static final int PAGE_SIZE = 6;

    private final VoitureService voitureService;
    private final MarqueService marqueService;
    private final CategorieService categorieService;
    private final EquipementService equipementService;

    /**
     * Liste paginee avec filtres. Tous null par defaut -> pas de filtre.
     */
    @SuppressWarnings("DuplicatedCode") // pattern filtres voitures partage avec CatalogueController (variantes statut vs dates)
    @GetMapping
    public String liste(@RequestParam(required = false) String keyword,
                         @RequestParam(required = false) Long marqueId,
                         @RequestParam(required = false) Long categorieId,
                         @RequestParam(required = false) BigDecimal prixMin,
                         @RequestParam(required = false) BigDecimal prixMax,
                         @RequestParam(required = false) Transmission transmission,
                         @RequestParam(required = false) Carburant carburant,
                         @RequestParam(required = false) StatutVoiture statut,
                         @RequestParam(required = false) List<Long> equipementIds,
                         @RequestParam(defaultValue = "0") int page,
                         Model model) {
        // null, null pour dateDebut/dateFin : pas de filtre disponibilite cote admin
        Page<Voiture> voitures = voitureService.filtrer(
                keyword, marqueId, categorieId, prixMin, prixMax,
                transmission, carburant, statut,
                equipementIds,
                null, null,
                PageRequest.of(page, PAGE_SIZE, Sort.by("modele").ascending()));

        // Ajout des filtres au modele (pour re-cocher dans le formulaire)
        model.addAttribute("voitures", voitures);
        model.addAttribute("keyword", keyword);
        model.addAttribute("marqueId", marqueId);
        model.addAttribute("categorieId", categorieId);
        model.addAttribute("prixMin", prixMin);
        model.addAttribute("prixMax", prixMax);
        model.addAttribute("transmission", transmission);
        model.addAttribute("carburant", carburant);
        model.addAttribute("statut", statut);
        model.addAttribute("equipementIds", equipementIds == null ? List.of() : equipementIds);

        // Donnees pour les dropdowns
        model.addAttribute("marques", marqueService.findAll());
        model.addAttribute("categories", categorieService.findAll());
        model.addAttribute("equipements", equipementService.findAll());
        model.addAttribute("transmissions", Transmission.values());
        model.addAttribute("carburants", Carburant.values());
        model.addAttribute("statutsVoiture", StatutVoiture.values());

        // URL preservant les filtres pour pagination
        UriComponentsBuilder b = UriComponentsBuilder.fromPath("/admin/voitures");
        if (keyword != null && !keyword.isBlank()) b.queryParam("keyword", keyword);
        if (marqueId != null)    b.queryParam("marqueId", marqueId);
        if (categorieId != null) b.queryParam("categorieId", categorieId);
        if (prixMin != null)     b.queryParam("prixMin", prixMin);
        if (prixMax != null)     b.queryParam("prixMax", prixMax);
        if (transmission != null) b.queryParam("transmission", transmission);
        if (carburant != null)   b.queryParam("carburant", carburant);
        if (statut != null)      b.queryParam("statut", statut);
        if (equipementIds != null && !equipementIds.isEmpty()) {
            for (Long id : equipementIds) b.queryParam("equipementIds", id);
        }
        model.addAttribute("urlBase", b.toUriString());

        return "admin/voitures/liste";
    }

    /**
     * Fiche admin (lecture seule + bouton Modifier).
     */
    @GetMapping("/{id}")
    public String detail(@PathVariable Long id, Model model) {
        model.addAttribute("voiture", voitureService.findById(id));
        return "admin/voitures/detail";
    }

    /**
     * Affiche le formulaire de creation (DTO vide).
     */
    @GetMapping("/nouveau")
    public String nouveau(Model model) {
        if (!model.containsAttribute("voitureForm")) {
            model.addAttribute("voitureForm", new VoitureForm());
        }
        prepareFormModel(model);
        return "admin/voitures/form";
    }

    /**
     * Cree une nouvelle voiture. Resout marque/categorie via leur id.
     */
    @PostMapping
    public String creer(@Valid @ModelAttribute("voitureForm") VoitureForm form,
                         BindingResult bindingResult,
                         @RequestParam(value = "photo", required = false) MultipartFile photo,
                         RedirectAttributes redirectAttributes,
                         Model model) {
        if (bindingResult.hasErrors()) {
            prepareFormModel(model);
            return "admin/voitures/form";
        }
        try {
            // DTO -> Entite + resolution des references
            Voiture v = form.toVoiture();
            v.setMarque(marqueService.findById(form.getMarqueId()));
            v.setCategorie(categorieService.findById(form.getCategorieId()));
            voitureService.creer(v, form.getEquipementIds(), photo);
            redirectAttributes.addFlashAttribute("successMessage", "Voiture creee");
            return "redirect:/admin/voitures";
        } catch (BusinessException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
            return "redirect:/admin/voitures/nouveau";
        }
    }

    /**
     * Affiche le formulaire d'edition pre-rempli.
     */
    @GetMapping("/{id}/modifier")
    public String modifier(@PathVariable Long id, Model model) {
        Voiture v = voitureService.findById(id);
        if (!model.containsAttribute("voitureForm")) {
            model.addAttribute("voitureForm", VoitureForm.fromVoiture(v));
        }
        model.addAttribute("voiture", v);
        prepareFormModel(model);
        return "admin/voitures/form";
    }

    /**
     * Met a jour la voiture. Meme logique que creer mais via VoitureService.modifier.
     */
    @PostMapping("/{id}")
    public String mettreAJour(@PathVariable Long id,
                               @Valid @ModelAttribute("voitureForm") VoitureForm form,
                               BindingResult bindingResult,
                               @RequestParam(value = "photo", required = false) MultipartFile photo,
                               RedirectAttributes redirectAttributes,
                               Model model) {
        if (bindingResult.hasErrors()) {
            model.addAttribute("voiture", voitureService.findById(id));
            prepareFormModel(model);
            return "admin/voitures/form";
        }
        try {
            Voiture maj = form.toVoiture();
            maj.setMarque(marqueService.findById(form.getMarqueId()));
            maj.setCategorie(categorieService.findById(form.getCategorieId()));
            voitureService.modifier(id, maj, form.getEquipementIds(), photo);
            redirectAttributes.addFlashAttribute("successMessage", "Voiture modifiee");
            return "redirect:/admin/voitures";
        } catch (BusinessException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
            // {id} expansé par Spring depuis redirectAttributes (pas en query param)
            redirectAttributes.addAttribute("id", id);
            return "redirect:/admin/voitures/{id}/modifier";
        }
    }

    /**
     * Suppression. Detache les locations (FK nullable) pour preserver
     * l'historique.
     */
    @PostMapping("/{id}/supprimer")
    public String supprimer(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            voitureService.supprimer(id);
            redirectAttributes.addFlashAttribute("successMessage", "Voiture supprimee");
        } catch (BusinessException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/admin/voitures";
    }

    /**
     * Helper : prepare le modele avec les dropdowns du formulaire.
     * Factorise entre creer et modifier.
     */
    private void prepareFormModel(Model model) {
        model.addAttribute("marques", marqueService.findAll());
        model.addAttribute("categories", categorieService.findAll());
        model.addAttribute("equipements", equipementService.findAll());
        model.addAttribute("transmissions", com.example.locvoitures.enumeration.Transmission.values());
        model.addAttribute("carburants", com.example.locvoitures.enumeration.Carburant.values());
        model.addAttribute("statuts", com.example.locvoitures.enumeration.StatutVoiture.values());
    }
}

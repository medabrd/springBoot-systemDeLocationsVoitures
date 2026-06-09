/*
 * ============================================================================
 * ClientLocationController - Locations, avis et reclamations cote client
 * ============================================================================
 *
 * Role :
 *   Centralise les endpoints cote CLIENT pour le cycle de vie d'une
 *   location :
 *   - Liste paginee des locations du client
 *   - Detail d'une location
 *   - Creation d'une demande (avec second conducteur optionnel)
 *   - Depot d'un avis post-restitution
 *   - Depot d'une reclamation pendant location active
 *
 * Mapping :
 *   /client/locations* (reserve hasRole("CLIENT"))
 *
 * Appelle :
 *   - LocationService : findById, historiqueClient, soumettreDemande
 *   - VoitureService.findById : recuperer la voiture choisie
 *   - FileStorageService.storeImage : photos second conducteur, photo reclamation
 *   - AvisService.creerAvis
 *   - ReclamationService.soumettre
 *   - AuthUtil.getCurrentClient : scope les requetes au client connecte
 *
 * Securite :
 *   Verification systematique location.getClient().getId().equals(client.getId())
 *   pour empecher un client d'acceder a la location d'un autre (id devine).
 *   Defense-en-profondeur car le filtre cote service couvre deja la plupart
 *   des cas.
 *
 * Validation conditionnelle :
 *   Quand avecSecondConducteur=true, on ajoute des erreurs manuellement
 *   dans BindingResult car Bean Validation ne couvre pas le conditionnel.
 * ============================================================================
 */
package com.example.locvoitures.controller.client;

import com.example.locvoitures.controller.advice.AuthUtil;

import com.example.locvoitures.dto.form.AvisForm;
import com.example.locvoitures.dto.form.DemandeLocationForm;
import com.example.locvoitures.dto.form.ReclamationForm;
import com.example.locvoitures.entity.Client;
import com.example.locvoitures.entity.ConducteurSecondaire;
import com.example.locvoitures.entity.Location;
import com.example.locvoitures.entity.Permis;
import com.example.locvoitures.entity.Voiture;
import com.example.locvoitures.exception.BusinessException;
import com.example.locvoitures.service.metier.AvisService;
import com.example.locvoitures.service.utils.FileStorageService;
import com.example.locvoitures.service.metier.LocationService;
import com.example.locvoitures.service.metier.PermisService;
import com.example.locvoitures.service.metier.ReclamationService;
import com.example.locvoitures.service.metier.VoitureService;

import jakarta.validation.Valid;

import lombok.RequiredArgsConstructor;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;
import java.util.Arrays;

@Controller
@RequestMapping("/client/locations")
@RequiredArgsConstructor
public class ClientLocationController {

    /**
     * Taille de page pour l'historique client (5 lignes par page).
     */
    private static final int PAGE_SIZE = 5;

    private final LocationService locationService;
    private final VoitureService voitureService;
    private final FileStorageService fileStorage;
    private final AvisService avisService;
    private final ReclamationService reclamationService;
    private final PermisService permisService;
    private final AuthUtil authUtil;

    // ============================================================
    // Liste des locations du client
    // ============================================================

    /**
     * Liste paginee tri ANTI-chrono sur dateCreation (les plus recentes
     * en haut).
     */
    @GetMapping
    public String liste(@RequestParam(defaultValue = "0") int page, Model model) {
        Client client = authUtil.getCurrentClient();
        Page<Location> locations = locationService.historiqueClient(
                client,
                PageRequest.of(page, PAGE_SIZE, Sort.by("dateCreation").descending()));

        model.addAttribute("locations", locations);
        model.addAttribute("urlBase", "/client/locations");
        return "client/locations/liste";
    }

    // ============================================================
    // Detail d'une location
    // ============================================================

    /**
     * Affiche le detail d'une location. Verifie l'ownership (security).
     */
    @GetMapping("/{id}")
    public String detail(@PathVariable Long id, Model model) {
        Client client = authUtil.getCurrentClient();
        Location location = locationService.findById(id);
        if (!location.getClient().getId().equals(client.getId())) {
            throw new BusinessException("Cette location ne vous appartient pas");
        }
        model.addAttribute("location", location);
        return "client/locations/detail";
    }

    // ============================================================
    // Soumission d'une demande de location
    // ============================================================

    /**
     * Affiche le formulaire de nouvelle demande, pre-rempli avec voitureId
     * et eventuellement les dates passees depuis le catalogue.
     */
    @GetMapping("/nouveau")
    public String nouveau(@RequestParam Long voitureId,
                           @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateDebut,
                           @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFin,
                           Model model) {
        Voiture voiture = voitureService.findById(voitureId);
        if (!model.containsAttribute("demandeForm")) {
            DemandeLocationForm form = new DemandeLocationForm();
            form.setVoitureId(voitureId);
            form.setDateDebut(dateDebut);
            form.setDateFin(dateFin);
            model.addAttribute("demandeForm", form);
        }
        model.addAttribute("voiture", voiture);
        return "client/locations/nouveau";
    }

    /**
     * Traite la soumission. Si second conducteur coche, validation
     * conditionnelle des champs et photos. Sauvegarde des fichiers via
     * FileStorageService.
     */
    @PostMapping
    public String soumettre(@Valid @ModelAttribute("demandeForm") DemandeLocationForm form,
                             BindingResult bindingResult,
                             @RequestParam(value = "secondPhotoPermis", required = false) MultipartFile secondPhotoPermis,
                             @RequestParam(value = "secondPhotoCIN", required = false) MultipartFile secondPhotoCIN,
                             RedirectAttributes redirectAttributes,
                             Model model) {

        // === Validation conditionnelle sur le second conducteur ===
        // Bean Validation ne couvre pas le "if avecSecondConducteur then required",
        // on l'ajoute manuellement a BindingResult.
        if (form.isAvecSecondConducteur()) {
            if (form.getSecondNom() == null || form.getSecondNom().isBlank()) {
                bindingResult.rejectValue("secondNom", "required", "Le nom du second conducteur est obligatoire");
            }
            if (form.getSecondPrenom() == null || form.getSecondPrenom().isBlank()) {
                bindingResult.rejectValue("secondPrenom", "required", "Le prenom du second conducteur est obligatoire");
            }
            if (form.getSecondNumeroPermis() == null || form.getSecondNumeroPermis().isBlank()) {
                bindingResult.rejectValue("secondNumeroPermis", "required", "Le n. permis est obligatoire");
            }
            if (form.getSecondDateObtentionPermis() == null) {
                bindingResult.rejectValue("secondDateObtentionPermis", "required", "Date d'obtention du permis obligatoire");
            }
            if (form.getSecondDateExpirationPermis() == null) {
                bindingResult.rejectValue("secondDateExpirationPermis", "required", "Date d'expiration du permis obligatoire");
            }
            if (form.getSecondNumeroCIN() == null || form.getSecondNumeroCIN().isBlank()) {
                bindingResult.rejectValue("secondNumeroCIN", "required", "Le n. CIN est obligatoire");
            }
            if (secondPhotoPermis == null || secondPhotoPermis.isEmpty()) {
                bindingResult.rejectValue("secondNumeroPermis", "photo.required", "La photo du permis est obligatoire");
            }
            if (secondPhotoCIN == null || secondPhotoCIN.isEmpty()) {
                bindingResult.rejectValue("secondNumeroCIN", "photo.required", "La photo de la CIN est obligatoire");
            }
        }

        if (bindingResult.hasErrors()) {
            // Re-affiche le formulaire (pas de redirect) avec les erreurs
            Voiture voiture = voitureService.findById(form.getVoitureId());
            model.addAttribute("voiture", voiture);
            return "client/locations/nouveau";
        }

        try {
            Client client = authUtil.getCurrentClient();
            Voiture voiture = voitureService.findById(form.getVoitureId());

            // Construit le second conducteur si declare. PermisService cree
            // le Permis (ou reutilise si numero deja connu en base).
            ConducteurSecondaire second = null;
            if (form.isAvecSecondConducteur()) {
                Permis permisSecond = permisService.construirePourSecondConducteur(
                        form.getSecondNumeroPermis(),
                        form.getSecondDateObtentionPermis(),
                        form.getSecondDateExpirationPermis(),
                        secondPhotoPermis);
                second = ConducteurSecondaire.builder()
                        .nom(form.getSecondNom().trim())
                        .prenom(form.getSecondPrenom().trim())
                        .permis(permisSecond)
                        .numeroCIN(form.getSecondNumeroCIN().trim())
                        .photoCIN(fileStorage.storeImage(secondPhotoCIN, "conducteurs"))
                        .build();
            }

            Location location = locationService.soumettreDemande(
                    client, voiture, form.getDateDebut(), form.getDateFin(), second);

            redirectAttributes.addFlashAttribute("successMessage",
                    "Votre demande a ete soumise. Vous recevrez un email lorsqu'elle sera traitee.");
            return "redirect:/client/locations/" + location.getId();

        } catch (BusinessException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
            return "redirect:/client/locations/nouveau?voitureId=" + form.getVoitureId();
        }
    }

    // ============================================================
    // Avis (post-location)
    // ============================================================

    /**
     * Affiche le formulaire d'avis. Verifie l'ownership.
     */
    @GetMapping("/{id}/avis")
    public String avisForm(@PathVariable Long id, Model model) {
        Client client = authUtil.getCurrentClient();
        Location location = locationService.findById(id);
        if (!location.getClient().getId().equals(client.getId())) {
            throw new BusinessException("Cette location ne vous appartient pas");
        }
        model.addAttribute("location", location);
        if (!model.containsAttribute("avisForm")) {
            model.addAttribute("avisForm", new AvisForm());
        }
        return "client/locations/avis";
    }

    /**
     * Soumission de l'avis. Les gardes metier sont dans AvisService
     * (proprietaire, TERMINEE, pas d'avis existant).
     */
    @PostMapping("/{id}/avis")
    public String soumettreAvis(@PathVariable Long id,
                                 @Valid @ModelAttribute("avisForm") AvisForm form,
                                 BindingResult bindingResult,
                                 RedirectAttributes redirectAttributes,
                                 Model model) {
        Client client = authUtil.getCurrentClient();
        Location location = locationService.findById(id);

        if (bindingResult.hasErrors()) {
            model.addAttribute("location", location);
            return "client/locations/avis";
        }

        try {
            avisService.creerAvis(location, client, form.getNote(), form.getCommentaire());
            redirectAttributes.addFlashAttribute("successMessage", "Merci pour votre avis !");
            return "redirect:/client/locations/" + id;
        } catch (BusinessException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
            redirectAttributes.addAttribute("id", id);
            return "redirect:/client/locations/{id}/avis";
        }
    }

    // ============================================================
    // Reclamation (pendant location active)
    // ============================================================

    /**
     * Formulaire de depot reclamation. Categories proviennent de l'enum.
     */
    @GetMapping("/{id}/reclamation")
    public String reclamationForm(@PathVariable Long id, Model model) {
        Client client = authUtil.getCurrentClient();
        Location location = locationService.findById(id);
        if (!location.getClient().getId().equals(client.getId())) {
            throw new BusinessException("Cette location ne vous appartient pas");
        }
        model.addAttribute("location", location);
        model.addAttribute("categories", Arrays.asList(
                com.example.locvoitures.enumeration.CategorieReclamation.values()));
        if (!model.containsAttribute("reclamationForm")) {
            model.addAttribute("reclamationForm", new ReclamationForm());
        }
        return "client/locations/reclamation";
    }

    /**
     * Soumet la reclamation. Photo facultative.
     * Les gardes metier (proprietaire, statut EN_COURS) dans ReclamationService.
     */
    @PostMapping("/{id}/reclamation")
    public String soumettreReclamation(@PathVariable Long id,
                                        @Valid @ModelAttribute("reclamationForm") ReclamationForm form,
                                        BindingResult bindingResult,
                                        @RequestParam(value = "photo", required = false) MultipartFile photo,
                                        RedirectAttributes redirectAttributes,
                                        Model model) {
        Client client = authUtil.getCurrentClient();
        Location location = locationService.findById(id);

        if (bindingResult.hasErrors()) {
            model.addAttribute("location", location);
            model.addAttribute("categories", Arrays.asList(
                    com.example.locvoitures.enumeration.CategorieReclamation.values()));
            return "client/locations/reclamation";
        }

        try {
            reclamationService.soumettre(location, client, form.getCategorie(),
                    form.getDescription(), photo);
            redirectAttributes.addFlashAttribute("successMessage",
                    "Votre reclamation a ete enregistree. Notre equipe vous repondra rapidement.");
            return "redirect:/client/locations/" + id;
        } catch (BusinessException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
            redirectAttributes.addAttribute("id", id);
            return "redirect:/client/locations/{id}/reclamation";
        }
    }
}

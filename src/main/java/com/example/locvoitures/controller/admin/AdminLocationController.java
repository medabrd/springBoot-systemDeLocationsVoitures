/*
 * ============================================================================
 * AdminLocationController - Gestion des locations cote admin
 * ============================================================================
 *
 * Role :
 *   Coeur de l'administration des locations :
 *   - Liste paginee avec filtre par statut
 *   - Detail d'une location
 *   - Transitions de statut : accepter, refuser, valider paiement,
 *     demarrer (PAYEE -> EN_COURS), clore (EN_COURS -> TERMINEE)
 *   - Creation directe par l'admin (formulaire 2 etapes : dates -> details)
 *   - Suppression (decouple Avis et Reclamations)
 *
 * Mapping :
 *   /admin/locations[?statut=...]
 *   /admin/locations/{id}
 *   /admin/locations/{id}/{action} (POST) : accepter, refuser, valider-paiement,
 *     demarrer, clore, supprimer
 *   /admin/locations/nouveau (GET formulaire 2 etapes)
 *   POST /admin/locations (creation)
 *
 * Appelle :
 *   - LocationService : tout le cycle de vie
 *   - ClientService : findAll, findById (pour le dropdown clients)
 *   - VoitureService : filtrer/findById (pour proposer les voitures dispo
 *     sur la periode saisie)
 *
 * Formulaire 2 etapes (creerParAdmin) :
 *   Etape 1 : admin saisit les dates -> redirect avec dates en query.
 *   Etape 2 : on liste les voitures disponibles sur la periode et propose
 *   les statuts initiaux coherents avec la date :
 *     - debut dans le futur -> ACCEPTEE / PAYEE
 *     - debut <= aujourd'hui -> EN_COURS (cles deja remises)
 *
 * Transitions :
 *   Apres feedback "simplification du workflow", les transitions
 *   PAYEE->EN_COURS et EN_COURS->TERMINEE sont MANUELLES (admin clique
 *   au moment de la remise/restitution des cles physique), pas planifiees.
 * ============================================================================
 */
package com.example.locvoitures.controller.admin;

import com.example.locvoitures.entity.Client;
import com.example.locvoitures.entity.Location;
import com.example.locvoitures.entity.Voiture;
import com.example.locvoitures.enumeration.StatutLocation;
import com.example.locvoitures.enumeration.StatutVoiture;
import com.example.locvoitures.exception.BusinessException;
import com.example.locvoitures.service.metier.ClientService;
import com.example.locvoitures.service.metier.LocationService;
import com.example.locvoitures.service.metier.VoitureService;

import lombok.RequiredArgsConstructor;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.LocalDate;
import java.util.List;

@Controller
@RequestMapping("/admin/locations")
@RequiredArgsConstructor
public class AdminLocationController {

    private static final int PAGE_SIZE = 5;

    private final LocationService locationService;
    private final ClientService clientService;
    private final VoitureService voitureService;

    /**
     * Liste paginee avec filtre par statut optionnel.
     */
    @GetMapping
    public String liste(@RequestParam(required = false) StatutLocation statut,
                         @RequestParam(defaultValue = "0") int page,
                         Model model) {
        PageRequest pageable = PageRequest.of(page, PAGE_SIZE, Sort.by("dateCreation").descending());
        Page<Location> locations = (statut != null)
                ? locationService.rechercherParStatut(statut, pageable)
                : locationService.rechercherToutes(pageable);

        model.addAttribute("locations", locations);
        model.addAttribute("filtreStatut", statut);
        model.addAttribute("statuts", StatutLocation.values());
        model.addAttribute("urlBase",
                UriComponentsBuilder.fromPath("/admin/locations")
                        .queryParamIfPresent("statut", java.util.Optional.ofNullable(statut))
                        .toUriString());
        return "admin/locations/liste";
    }

    /**
     * Fiche detail. Toutes les actions sont des boutons dans le template.
     */
    @GetMapping("/{id}")
    public String detail(@PathVariable Long id, Model model) {
        model.addAttribute("location", locationService.findById(id));
        return "admin/locations/detail";
    }

    /**
     * Accepter une demande EN_ATTENTE -> ACCEPTEE.
     * Genere facture PDF + envoie mail au client (cote service).
     */
    @PostMapping("/{id}/accepter")
    public String accepter(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            locationService.accepterDemande(id);
            redirectAttributes.addFlashAttribute("successMessage",
                    "Demande acceptee. La facture a ete envoyee au client par email.");
        } catch (BusinessException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/admin/locations/" + id;
    }

    /**
     * Refuser EN_ATTENTE -> REFUSEE avec motif obligatoire.
     */
    @PostMapping("/{id}/refuser")
    public String refuser(@PathVariable Long id,
                           @RequestParam String motif,
                           RedirectAttributes redirectAttributes) {
        try {
            locationService.refuserDemande(id, motif);
            redirectAttributes.addFlashAttribute("successMessage",
                    "Demande refusee. Le client a ete notifie par email.");
        } catch (BusinessException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/admin/locations/" + id;
    }

    /**
     * Valider paiement ACCEPTEE -> PAYEE.
     */
    @PostMapping("/{id}/valider-paiement")
    public String validerPaiement(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            locationService.validerPaiement(id);
            redirectAttributes.addFlashAttribute("successMessage",
                    "Paiement valide. La location passe au statut PAYEE.");
        } catch (BusinessException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/admin/locations/" + id;
    }

    /**
     * Demarrer PAYEE -> EN_COURS (remise des cles au client).
     */
    @PostMapping("/{id}/demarrer")
    public String demarrer(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            locationService.demarrerLocation(id);
            redirectAttributes.addFlashAttribute("successMessage",
                    "Cles remises au client. La location passe en EN_COURS.");
        } catch (BusinessException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/admin/locations/" + id;
    }

    /**
     * Clore EN_COURS -> TERMINEE (recuperation des cles).
     */
    @PostMapping("/{id}/clore")
    public String clore(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            locationService.cloreLocation(id);
            redirectAttributes.addFlashAttribute("successMessage",
                    "Cles recuperees. La location passe en TERMINEE.");
        } catch (BusinessException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/admin/locations/" + id;
    }

    // ============================================================
    // Creation par l'admin (formulaire 2 etapes : dates puis details)
    // ============================================================

    /**
     * Etape 1 : saisie des dates (formulaire vide).
     * Etape 2 : si dateDebut et dateFin valides en query string -> on
     * liste les voitures disponibles sur la periode et les clients.
     */
    @GetMapping("/nouveau")
    public String nouveauForm(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateDebut,
                                @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFin,
                                Model model) {
        model.addAttribute("dateDebut", dateDebut);
        model.addAttribute("dateFin", dateFin);
        if (dateDebut != null && dateFin != null && dateFin.isAfter(dateDebut)) {
            // Etape 2 : on a des dates valides
            // Recherche des voitures ACTIVES disponibles sur la periode
            Page<Voiture> voituresPage = voitureService.filtrer(
                    null, null, null, null, null, null, null,
                    StatutVoiture.ACTIVE,
                    null,
                    dateDebut, dateFin,
                    PageRequest.of(0, 100, Sort.by("modele")));
            model.addAttribute("voituresDisponibles", voituresPage.getContent());
            model.addAttribute("clients", clientService.findAll());

            // Statuts proposes selon la periode (logique metier dans la UI) :
            // - dateDebut dans le futur : ACCEPTEE (pre-reservation) ou PAYEE
            // - dateDebut <= aujourd'hui : EN_COURS (cles deja remises)
            List<StatutLocation> statutsAutorises = LocalDate.now().isBefore(dateDebut)
                    ? List.of(StatutLocation.ACCEPTEE, StatutLocation.PAYEE)
                    : List.of(StatutLocation.EN_COURS);
            model.addAttribute("statutsAutorises", statutsAutorises);
        }
        return "admin/locations/nouveau";
    }

    /**
     * Cree la location. Toutes les validations metier dans creerParAdmin.
     */
    @PostMapping
    public String creer(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateDebut,
                         @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFin,
                         @RequestParam Long clientId,
                         @RequestParam Long voitureId,
                         @RequestParam StatutLocation statutInitial,
                         RedirectAttributes redirectAttributes) {
        try {
            Client client = clientService.findById(clientId);
            Voiture voiture = voitureService.findById(voitureId);
            Location l = locationService.creerParAdmin(client, voiture, dateDebut, dateFin, statutInitial);
            redirectAttributes.addFlashAttribute("successMessage",
                    "Location creee (#" + l.getId() + ", statut " + statutInitial + ")");
            return "redirect:/admin/locations/" + l.getId();
        } catch (BusinessException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
            // Redirige vers l'etape 2 du formulaire pour preserver les dates.
            // addAttribute -> Spring construit ?dateDebut=...&dateFin=... automatiquement
            redirectAttributes.addAttribute("dateDebut", dateDebut);
            redirectAttributes.addAttribute("dateFin", dateFin);
            return "redirect:/admin/locations/nouveau";
        }
    }

    // ============================================================
    // Suppression
    // ============================================================

    /**
     * Suppression d'une location. Avis et Reclamations conserves (FK passe a null).
     */
    @PostMapping("/{id}/supprimer")
    public String supprimer(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            locationService.supprimerParAdmin(id);
            redirectAttributes.addFlashAttribute("successMessage",
                    "Location #" + id + " supprimee. Avis et reclamations associes ont ete conserves.");
            return "redirect:/admin/locations";
        } catch (BusinessException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
            return "redirect:/admin/locations/" + id;
        }
    }
}

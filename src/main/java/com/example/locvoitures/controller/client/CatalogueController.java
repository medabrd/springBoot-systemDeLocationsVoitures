/*
 * ============================================================================
 * CatalogueController - Catalogue de voitures cote client
 * ============================================================================
 *
 * Role :
 *   Affiche le catalogue des voitures ACTIVES disponibles a la location
 *   avec pagination et filtres (mots-cles, marque, categorie, prix,
 *   transmission, carburant, equipements, plage de dates de location).
 *   Egalement la page detail d'une voiture avec calendrier de
 *   reservations passees/futures et formulaire de demande pre-rempli.
 *
 * Mapping :
 *   /catalogue (liste)
 *   /catalogue/{id} (detail)
 *   Protege par hasRole("CLIENT") -> seuls les clients connectes y accedent.
 *
 * Appelle :
 *   - VoitureService.filtrer / findById
 *   - MarqueService, CategorieService, EquipementService : findAll pour
 *     remplir les dropdowns de filtre
 *   - LocationService.findReservationsOccupant : periodes deja reservees
 *     pour affichage dans le calendrier du detail
 *
 * Carry-through des filtres :
 *   Les dates saisies dans /catalogue sont propagees a /catalogue/{id}
 *   pour pre-remplir le formulaire de demande, evitant a l'utilisateur
 *   de re-saisir les dates.
 *
 * UriComponentsBuilder :
 *   Construit l'URL de pagination avec tous les query params actuels
 *   conserves (sinon le clic "page suivante" perdrait les filtres).
 * ============================================================================
 */
package com.example.locvoitures.controller.client;

import com.example.locvoitures.dto.form.DemandeLocationForm;
import com.example.locvoitures.entity.Location;
import com.example.locvoitures.entity.Voiture;
import com.example.locvoitures.enumeration.Carburant;
import com.example.locvoitures.enumeration.StatutVoiture;
import com.example.locvoitures.enumeration.Transmission;
import com.example.locvoitures.service.metier.CategorieService;
import com.example.locvoitures.service.metier.EquipementService;
import com.example.locvoitures.service.metier.LocationService;
import com.example.locvoitures.service.metier.MarqueService;
import com.example.locvoitures.service.metier.VoitureService;

import lombok.RequiredArgsConstructor;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
// @DateTimeFormat : indique a Spring comment parser la string -> LocalDate
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
// Helper pour construire des URLs avec query params
import org.springframework.web.util.UriComponentsBuilder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Controller
@RequestMapping("/catalogue")
@RequiredArgsConstructor
public class CatalogueController {

    /**
     * Taille de page du catalogue (6 voitures par page = grille 2x3 ou 3x2).
     */
    private static final int PAGE_SIZE = 6;

    private final VoitureService voitureService;
    private final MarqueService marqueService;
    private final CategorieService categorieService;
    private final EquipementService equipementService;
    private final LocationService locationService;

    /**
     * Liste paginee avec filtres multi-criteres.
     * Tous les RequestParam sont optionnels (required=false).
     * {@code @DateTimeFormat(ISO.DATE)} : Spring parse "2026-05-10" en LocalDate.
     */
    @SuppressWarnings("DuplicatedCode") // pattern filtres voitures partage avec AdminVoitureController (variantes statut vs dates)
    @GetMapping
    public String liste(@RequestParam(required = false) String keyword,
                         @RequestParam(required = false) Long marqueId,
                         @RequestParam(required = false) Long categorieId,
                         @RequestParam(required = false) BigDecimal prixMin,
                         @RequestParam(required = false) BigDecimal prixMax,
                         @RequestParam(required = false) Transmission transmission,
                         @RequestParam(required = false) Carburant carburant,
                         @RequestParam(required = false) List<Long> equipementIds,
                         @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateDebut,
                         @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFin,
                         @RequestParam(defaultValue = "0") int page,
                         Model model) {

        // Filtre de disponibilite : applique UNIQUEMENT si les deux dates
        // sont saisies et coherentes (fin > debut). Sinon on passe null
        // pour neutraliser le critere.
        LocalDate dispoDebut = (dateDebut != null && dateFin != null && dateFin.isAfter(dateDebut)) ? dateDebut : null;
        LocalDate dispoFin   = (dateDebut != null && dateFin != null && dateFin.isAfter(dateDebut)) ? dateFin   : null;

        // Appel au service avec tous les filtres + tri alphabetique par modele
        Page<Voiture> voitures = voitureService.filtrer(
                keyword, marqueId, categorieId, prixMin, prixMax,
                transmission, carburant,
                StatutVoiture.ACTIVE,            // filtre fixe pour le catalogue client
                equipementIds,
                dispoDebut, dispoFin,
                PageRequest.of(page, PAGE_SIZE, Sort.by("modele").ascending()));

        // Ajout au modele : resultats + valeurs des filtres (pour re-cocher
        // les checkboxes au rechargement)
        model.addAttribute("voitures", voitures);
        model.addAttribute("keyword", keyword);
        model.addAttribute("marqueId", marqueId);
        model.addAttribute("categorieId", categorieId);
        model.addAttribute("prixMin", prixMin);
        model.addAttribute("prixMax", prixMax);
        model.addAttribute("transmission", transmission);
        model.addAttribute("carburant", carburant);
        model.addAttribute("equipementIds", equipementIds == null ? List.of() : equipementIds);
        model.addAttribute("dateDebut", dateDebut);
        model.addAttribute("dateFin", dateFin);

        // Donnees pour les dropdowns
        model.addAttribute("marques", marqueService.findAll());
        model.addAttribute("categories", categorieService.findAll());
        model.addAttribute("equipements", equipementService.findAll());
        model.addAttribute("transmissions", Transmission.values());
        model.addAttribute("carburants", Carburant.values());

        // Construction de l'URL de pagination en preservant tous les filtres.
        // UriComponentsBuilder : helper pour ajouter conditionnellement des
        // query params sans concatenation manuelle.
        UriComponentsBuilder b = UriComponentsBuilder.fromPath("/catalogue");
        if (keyword != null && !keyword.isBlank()) b.queryParam("keyword", keyword);
        if (marqueId != null)    b.queryParam("marqueId", marqueId);
        if (categorieId != null) b.queryParam("categorieId", categorieId);
        if (prixMin != null)     b.queryParam("prixMin", prixMin);
        if (prixMax != null)     b.queryParam("prixMax", prixMax);
        if (transmission != null) b.queryParam("transmission", transmission);
        if (carburant != null)   b.queryParam("carburant", carburant);
        if (dateDebut != null)   b.queryParam("dateDebut", dateDebut);
        if (dateFin != null)     b.queryParam("dateFin", dateFin);
        if (equipementIds != null && !equipementIds.isEmpty()) {
            for (Long id : equipementIds) b.queryParam("equipementIds", id);
        }
        model.addAttribute("urlBase", b.toUriString());

        return "catalogue/liste";
    }

    /**
     * Page detail d'une voiture avec calendrier des reservations et
     * formulaire de demande pre-rempli avec les dates passees en parametre.
     */
    @GetMapping("/{id}")
    public String detail(@PathVariable Long id,
                          @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateDebut,
                          @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFin,
                          Model model) {
        Voiture voiture = voitureService.findById(id);
        model.addAttribute("voiture", voiture);
        model.addAttribute("dateDebut", dateDebut);
        model.addAttribute("dateFin", dateFin);

        // Periodes deja reservees sur la prochaine annee (pour afficher dans
        // le calendrier Flatpickr cote client : marqueur visuel sur dates
        // bloquees).
        List<Location> reservations = locationService.findReservationsOccupant(
                voiture, LocalDate.now(), LocalDate.now().plusYears(1));
        model.addAttribute("reservations", reservations);

        // Pre-remplit le formulaire avec l'id voiture (et eventuellement les
        // dates si passees en query string depuis /catalogue).
        if (!model.containsAttribute("demandeForm")) {
            DemandeLocationForm form = new DemandeLocationForm();
            form.setVoitureId(id);
            model.addAttribute("demandeForm", form);
        }
        return "catalogue/detail";
    }
}

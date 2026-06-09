/*
 * ============================================================================
 * VoitureDisponibiliteController - API JSON pour le calendrier de dispo
 * ============================================================================
 *
 * Role :
 *   Expose en JSON les periodes deja reservees pour une voiture donnee.
 *   Consomme par le calendrier Flatpickr cote client : permet de griser
 *   les dates indisponibles en temps reel sans recharger la page.
 *
 * Mapping :
 *   GET /api/voitures/{id}/disponibilite?from=YYYY-MM-DD&to=YYYY-MM-DD
 *   Protege par hasRole("CLIENT") dans SecurityConfig.
 *
 * Appelle :
 *   - VoitureService.findById : verification existence
 *   - LocationRepository.findChevauchements : recupere les locations
 *     bloquantes (ACCEPTEE/PAYEE/EN_COURS) sur la plage
 *
 * Format de reponse :
 *   [{"from":"2026-05-01","to":"2026-05-05","statut":"PAYEE"}, ...]
 *
 * Difference avec un Controller MVC :
 *   @RestController = @Controller + @ResponseBody implicite. Les retours
 *   des methodes sont serialises en JSON par Jackson au lieu de chercher
 *   un template.
 *
 * Defaults :
 *   from = aujourd'hui, to = aujourd'hui + 1 an. Permet d'appeler sans
 *   parametres pour avoir une plage par defaut raisonnable.
 * ============================================================================
 */
package com.example.locvoitures.controller.client;

import com.example.locvoitures.entity.Location;
import com.example.locvoitures.entity.Voiture;
import com.example.locvoitures.service.metier.LocationService;
import com.example.locvoitures.service.metier.VoitureService;

import lombok.RequiredArgsConstructor;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
// @RestController : Spring serialise le retour en JSON automatiquement
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
public class VoitureDisponibiliteController {

    private final VoitureService voitureService;
    private final LocationService locationService;

    /**
     * Endpoint JSON.
     * Retour List<Map<String,String>> : facon pragmatique de produire du
     * JSON sans creer un DTO dedie (acceptable car format simple).
     */
    @GetMapping("/api/voitures/{id}/disponibilite")
    public List<Map<String, String>> disponibilite(
            @PathVariable Long id,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {

        Voiture voiture = voitureService.findById(id);
        // Defaults : aujourd'hui -> +1 an
        LocalDate debut = from != null ? from : LocalDate.now();
        LocalDate fin   = to   != null ? to   : LocalDate.now().plusYears(1);

        List<Location> reservations = locationService.findReservationsOccupant(voiture, debut, fin);

        // Map vers une representation JSON plate {from, to, statut}
        return reservations.stream()
                .map(l -> Map.of(
                        "from", l.getDateDebut().toString(),
                        "to",   l.getDateFin().toString(),
                        "statut", l.getStatut().name()
                ))
                .toList();
    }
}

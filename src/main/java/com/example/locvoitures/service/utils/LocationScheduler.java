/*
 * ============================================================================
 * LocationScheduler - Taches planifiees (cron jobs) automatiques
 * ============================================================================
 *
 * Role :
 *   Execute en arriere-plan les transitions automatiques qui ne peuvent pas
 *   etre declenchees par une action utilisateur :
 *   - Expiration des pre-reservations non payees dans le delai
 *   - Envoi des rappels d'avis aux clients dont la location est terminee
 *
 * Activation :
 *   @EnableScheduling sur la classe Application (LocvoituresApplication).
 *   Sans cette annotation globale, @Scheduled est ignore.
 *
 * Cron expressions Spring (6 champs : seconde, minute, heure, jour, mois, jour-semaine) :
 *   - "0 *\/15 * * * *"  : toutes les 15 minutes (a 0s)
 *   - "0 0 9 * * *"     : tous les jours a 09:00:00
 *
 * Note historique :
 *   Le projet a connu un premier design ou les transitions PAYEE -> EN_COURS
 *   et EN_COURS -> TERMINEE etaient automatisees ici. Apres feedback, ces
 *   transitions sont desormais MANUELLES (admin clique "Demarrer" / "Terminer"
 *   au moment de la remise/restitution des cles). Le scheduler ne fait plus
 *   que les operations qui doivent VRAIMENT etre automatisees (expiration
 *   par delai depasse, rappels temporels).
 *
 * Appelle :
 *   - LocationService : findReservationsExpirees, expirerReservation,
 *     findRelancesAvisAEnvoyer, marquerAvisRappele
 *   - EmailService : envoyerRappelAvis
 *
 * Transactionnel :
 *   @Transactional sur chaque methode @Scheduled. Spring ouvre une nouvelle
 *   transaction par execution (independante des autres).
 * ============================================================================
 */
package com.example.locvoitures.service.utils;

import com.example.locvoitures.service.metier.LocationService;

import com.example.locvoitures.entity.Location;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

// @Scheduled : Spring fait tourner la methode selon le cron
import org.springframework.scheduling.annotation.Scheduled;
// @Component plutot que @Service : c'est un orchestrateur de cron, pas
// un service metier au sens strict
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class LocationScheduler {

    private final LocationService locationService;
    private final EmailService emailService;

    /**
     * Expire les pre-reservations ACCEPTEES non payees dans le delai imparti.

     * Cron "0 *\/15 * * * *" : a chaque quart d'heure (xx:00, xx:15, xx:30, xx:45)
     * a 0 seconde.

     * Strategie : si aucune ne doit etre expiree, sortie immediate pour
     * eviter la latence log + commit transactionnel inutile.
     */
    @Scheduled(cron = "0 */15 * * * *")
    @Transactional
    public void expirerReservations() {
        List<Location> expirees = locationService.findReservationsExpirees();
        if (expirees.isEmpty()) return;
        log.info("Scheduler : {} pre-reservation(s) a expirer", expirees.size());
        // forEach + reference de methode (concis)
        expirees.forEach(locationService::expirerReservation);
    }

    /**
     * Envoie le mail de rappel d'avis aux clients dont la location est
     * terminee sans avis et sans rappel deja envoye.

     * Cron "0 0 9 * * *" : chaque jour a 09:00:00 (heure serveur, fixee
     * a Europe/Paris dans application.properties).

     * Strategie : pour chaque relance candidate, envoi du mail puis flag
     * avisRappelEnvoye=true pour ne plus l'envoyer.
     */
    @Scheduled(cron = "0 0 9 * * *")
    @Transactional
    public void envoyerRappelsAvis() {
        List<Location> relances = locationService.findRelancesAvisAEnvoyer();
        if (relances.isEmpty()) return;
        log.info("Scheduler : {} rappel(s) d'avis a envoyer", relances.size());
        for (Location l : relances) {
            emailService.envoyerRappelAvis(l);
            // Pose le flag dans la meme transaction pour eviter double-envoi.
            locationService.marquerAvisRappele(l);
        }
    }
}

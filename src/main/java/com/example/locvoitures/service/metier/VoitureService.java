/*
 * ============================================================================
 * VoitureService - Service metier autour de Voiture (flotte)
 * ============================================================================
 *
 * Role :
 *   Centralise les operations CRUD sur Voiture et la recherche paginee
 *   multi-criteres pour le catalogue (client) et la flotte (admin).
 *   Gere aussi l'upload de la photo et l'association des equipements.
 *
 * Appele depuis :
 *   - controller/AdminVoitureController : CRUD admin
 *   - controller/CatalogueController : filtrer (catalogue client)
 *   - controller/VoitureDisponibiliteController : verifier disponibilite
 *     par dates pour AJAX
 *   - service/LocationService : findById indirect via le controller, pas
 *     d'appel direct
 *
 * Appelle :
 *   - VoitureRepository : CRUD, filtrer
 *   - EquipementService.findAllByIds : mapper les ids selectionnes en entites
 *   - FileStorageService : storeImage / delete pour la photo
 *
 * Gardes metier :
 *   - Immatriculation unique en base
 *   - Garde de suppression : detache les locations (FK nullable) pour
 *     preserver l'historique sans casser les contraintes
 *
 * Particularite "filtrer" :
 *   Le service translate equipementIds=[] en List.of(-1L) avec nbRequis=0L
 *   pour neutraliser le critere cote JPQL (eviter NPE sur IN avec liste vide).
 * ============================================================================
 */
package com.example.locvoitures.service.metier;

import com.example.locvoitures.service.utils.FileStorageService;

import com.example.locvoitures.entity.Equipement;
import com.example.locvoitures.entity.Voiture;
import com.example.locvoitures.enumeration.Carburant;
import com.example.locvoitures.enumeration.StatutVoiture;
import com.example.locvoitures.enumeration.Transmission;
import com.example.locvoitures.exception.BusinessException;
import com.example.locvoitures.exception.ResourceNotFoundException;
import com.example.locvoitures.repository.VoitureRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class VoitureService {

    private final VoitureRepository repo;
    private final EquipementService equipementService;
    private final FileStorageService fileStorage;

    @Transactional(readOnly = true)
    public Voiture findById(Long id) {
        return repo.findById(id).orElseThrow(() -> new ResourceNotFoundException("Voiture", id));
    }

    /**
     * Recherche multi-criteres avec filtres optionnels.
     * Tout parametre null est ignore (la JPQL gere via "IS NULL OR ...").

     * Logique AND sur equipements : la voiture doit posseder TOUS les
     * equipements selectionnes (calcul via sous-requete COUNT).

     * Astuce : si aucun equipement filtre, on passe nbRequis=0L et une
     * liste de -1L pour neutraliser le critere sans planter le SQL.
     */
    @Transactional(readOnly = true)
    public Page<Voiture> filtrer(String keyword, Long marqueId, Long categorieId,
                                  BigDecimal prixMin, BigDecimal prixMax,
                                  Transmission transmission, Carburant carburant,
                                  StatutVoiture statut,
                                  List<Long> equipementIds,
                                  LocalDate dateDebut, LocalDate dateFin,
                                  Pageable pageable) {
        // Normalise un keyword vide en null pour que la JPQL court-circuite
        String kw = (keyword == null || keyword.isBlank()) ? null : keyword.trim();

        // Calcul du nombre d'equipements requis et neutralisation si aucun
        long nbRequis = (equipementIds == null) ? 0L : equipementIds.size();
        List<Long> ids = (nbRequis == 0L) ? List.of(-1L) : equipementIds;

        return repo.filtrer(kw, marqueId, categorieId, prixMin, prixMax,
                            transmission, carburant, statut,
                            ids, nbRequis,
                            dateDebut, dateFin,
                            pageable);
    }

    /**
     * Creation d'une voiture avec equipements et photo (optionnelle).
     */
    public Voiture creer(Voiture voiture, List<Long> equipementIds, MultipartFile photo) {
        if (repo.existsByImmatriculation(voiture.getImmatriculation())) {
            throw new BusinessException("Une voiture avec cette immatriculation existe deja");
        }
        appliquerEquipements(voiture, equipementIds);
        if (photo != null && !photo.isEmpty()) {
            voiture.setPhoto(fileStorage.storeImage(photo, "voitures"));
        }
        return repo.save(voiture);
    }

    /**
     * Modification : met a jour tous les champs + relations. Si la photo
     * est remplacee, l'ancienne est supprimee apres la nouvelle (defense).
     */
    public Voiture modifier(Long id, Voiture maj, List<Long> equipementIds, MultipartFile photo) {
        Voiture v = findById(id);

        // Garde unicite immatriculation (sauf si on garde la meme).
        if (!v.getImmatriculation().equalsIgnoreCase(maj.getImmatriculation())
                && repo.existsByImmatriculation(maj.getImmatriculation())) {
            throw new BusinessException("Une voiture avec cette immatriculation existe deja");
        }

        // Mise a jour des champs scalaires
        v.setModele(maj.getModele());
        v.setAnnee(maj.getAnnee());
        v.setCouleur(maj.getCouleur());
        v.setImmatriculation(maj.getImmatriculation());
        v.setTarifJournalier(maj.getTarifJournalier());
        v.setDescription(maj.getDescription());
        v.setStatutGeneral(maj.getStatutGeneral());
        v.setMarque(maj.getMarque());
        v.setCategorie(maj.getCategorie());

        // Details techniques (cascadee depuis Voiture). On met a jour in-place
        // pour conserver l'id et la cascade.
        if (maj.getDetails() != null) {
            if (v.getDetails() == null) {
                v.setDetails(maj.getDetails());
            } else {
                v.getDetails().setTransmission(maj.getDetails().getTransmission());
                v.getDetails().setTypeCarburant(maj.getDetails().getTypeCarburant());
                v.getDetails().setConsommation(maj.getDetails().getConsommation());
                v.getDetails().setPuissance(maj.getDetails().getPuissance());
                v.getDetails().setNombrePlaces(maj.getDetails().getNombrePlaces());
                v.getDetails().setNombrePortes(maj.getDetails().getNombrePortes());
                v.getDetails().setVolumeCoffre(maj.getDetails().getVolumeCoffre());
            }
        }

        appliquerEquipements(v, equipementIds);

        // Photo : nouvelle photo si fournie, on remplace
        if (photo != null && !photo.isEmpty()) {
            fileStorage.delete(v.getPhoto());
            v.setPhoto(fileStorage.storeImage(photo, "voitures"));
        }

        return v;
    }

    /**
     * Suppression d'une voiture par l'admin.
     * Decouplage : Location.voiture passe a null pour preserver l'historique.
     * Equipements detaches (clear de la collection) pour ne pas casser le
     * JoinTable. Photo physique supprimee.
     */
    public void supprimer(Long id) {
        Voiture v = findById(id);
        if (v.getLocations() != null) {
            int n = v.getLocations().size();
            for (com.example.locvoitures.entity.Location l : v.getLocations()) {
                l.setVoiture(null);
            }
            v.getLocations().clear();
            if (n > 0) {
                log.info("Voiture {} : {} location(s) detachee(s) avant suppression",
                        v.getImmatriculation(), n);
            }
        }
        v.getEquipements().clear();
        fileStorage.delete(v.getPhoto());
        repo.delete(v);
    }

    /**
     * Helper : applique la liste d'equipements selectionnes en remplacant
     * l'existant (pas de merge incremental).
     */
    private void appliquerEquipements(Voiture v, List<Long> equipementIds) {
        if (equipementIds == null || equipementIds.isEmpty()) {
            v.setEquipements(new HashSet<>());
            return;
        }
        Set<Equipement> equipements = new HashSet<>(equipementService.findAllByIds(equipementIds));
        v.setEquipements(equipements);
    }
}

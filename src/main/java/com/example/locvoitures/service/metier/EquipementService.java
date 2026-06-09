/*
 * ============================================================================
 * EquipementService - Service metier des equipements (options vehicule)
 * ============================================================================
 *
 * Role :
 *   CRUD des equipements proposes sur les vehicules (GPS, climatisation,
 *   toit ouvrant, etc.). Garde d'unicite sur le nom et garde de suppression
 *   si l'equipement est associe a au moins une voiture.
 *
 * Appele depuis :
 *   - controller/AdminEquipementController : CRUD admin
 *   - controller/CatalogueController : findAll() pour le filtre multi-select
 *   - service/VoitureService : findAllByIds pour associer les equipements
 *     selectionnes a une voiture
 *
 * Appelle :
 *   - EquipementRepository : CRUD + existsByNomIgnoreCase + findAllById
 *
 * Pattern identique a CategorieService : meme structure CRUD avec gardes
 * d'unicite et de suppression.
 * ============================================================================
 */
package com.example.locvoitures.service.metier;

import com.example.locvoitures.entity.Equipement;
import com.example.locvoitures.exception.BusinessException;
import com.example.locvoitures.exception.ResourceNotFoundException;
import com.example.locvoitures.repository.EquipementRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class EquipementService {

    private final EquipementRepository repo;

    /**
     * Liste complete (utilisee comme filtre dropdown et formulaire).
     */
    @Transactional(readOnly = true)
    public List<Equipement> findAll() {
        return repo.findAll();
    }

    /**
     * Recherche par id (404 si introuvable).
     */
    @Transactional(readOnly = true)
    public Equipement findById(Long id) {
        return repo.findById(id).orElseThrow(() -> new ResourceNotFoundException("Equipement", id));
    }

    /**
     * Recherche multiple par ids. Utilise pour mapper les checkboxes
     * d'equipements en entites avant association a une voiture.
     */
    @Transactional(readOnly = true)
    public List<Equipement> findAllByIds(List<Long> ids) {
        return repo.findAllById(ids);
    }

    /**
     * Creation avec garde d'unicite.
     */
    public Equipement creer(Equipement e) {
        if (repo.existsByNomIgnoreCase(e.getNom())) {
            throw new BusinessException("Un equipement avec ce nom existe deja");
        }
        return repo.save(e);
    }

    /**
     * Modification avec garde d'unicite (sauf si meme entite).
     */
    public Equipement modifier(Long id, Equipement maj) {
        Equipement e = findById(id);
        if (!e.getNom().equalsIgnoreCase(maj.getNom()) && repo.existsByNomIgnoreCase(maj.getNom())) {
            throw new BusinessException("Un equipement avec ce nom existe deja");
        }
        e.setNom(maj.getNom());
        e.setDescription(maj.getDescription());
        return e;
    }

    /**
     * Suppression refusee si l'equipement est utilise.
     */
    public void supprimer(Long id) {
        Equipement e = findById(id);
        if (!e.getVoitures().isEmpty()) {
            throw new BusinessException("Impossible de supprimer : des voitures utilisent cet equipement");
        }
        repo.delete(e);
    }
}

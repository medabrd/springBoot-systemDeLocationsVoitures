/*
 * ============================================================================
 * CategorieService - Service metier autour des categories de voitures
 * ============================================================================
 *
 * Role :
 *   Couche metier autour de Categorie. CRUD avec garde d'unicite sur le
 *   nom et garde de suppression si la categorie est referencee par des
 *   voitures.
 *
 * Appele depuis :
 *   - controller/AdminCategorieController : CRUD admin
 *   - controller/CatalogueController : findAll() pour generer la liste
 *     de filtre dans la recherche
 *   - service/VoitureService : findById pour associer a une voiture
 *
 * Appelle :
 *   - CategorieRepository : CRUD JPA
 *
 * Gardes metier :
 *   - creer : refuse si le nom existe deja (case-insensitive)
 *   - modifier : refuse si le nouveau nom existe deja (sauf si meme entite)
 *   - supprimer : refuse si la categorie est utilisee par >= 1 voiture
 *
 * Note :
 *   Pas de log explicite ici (operations CRUD simples). Le @Transactional
 *   au niveau classe assure l'atomicite de chaque methode.
 * ============================================================================
 */
package com.example.locvoitures.service.metier;

import com.example.locvoitures.entity.Categorie;
import com.example.locvoitures.exception.BusinessException;
import com.example.locvoitures.exception.ResourceNotFoundException;
import com.example.locvoitures.repository.CategorieRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class CategorieService {

    private final CategorieRepository repo;

    /**
     * Liste complete (utilisee pour les dropdowns de filtre / formulaire).
     */
    @Transactional(readOnly = true)
    public List<Categorie> findAll() {
        return repo.findAll();
    }

    /**
     * Recherche par id avec exception 404 si introuvable.
     */
    @Transactional(readOnly = true)
    public Categorie findById(Long id) {
        return repo.findById(id).orElseThrow(() -> new ResourceNotFoundException("Categorie", id));
    }

    /**
     * Creation avec garde d'unicite sur le nom (case-insensitive).
     */
    public Categorie creer(Categorie c) {
        if (repo.existsByNomIgnoreCase(c.getNom())) {
            throw new BusinessException("Une categorie avec ce nom existe deja");
        }
        return repo.save(c);
    }

    /**
     * Modification : met a jour nom et description. Garde l'unicite du nom
     * mais autorise le meme nom inchange (cas d'une edition sans renommer).

     * Note : pas de save() explicite ici car l'entite chargee est managee
     * par la transaction -> Hibernate detecte les changements et flush au
     * commit (dirty checking).
     */
    public Categorie modifier(Long id, Categorie maj) {
        Categorie c = findById(id);
        if (!c.getNom().equalsIgnoreCase(maj.getNom()) && repo.existsByNomIgnoreCase(maj.getNom())) {
            throw new BusinessException("Une categorie avec ce nom existe deja");
        }
        c.setNom(maj.getNom());
        c.setDescription(maj.getDescription());
        return c;
    }

    /**
     * Suppression avec garde : refuse si la categorie a >= 1 voiture associee.
     * Note : c.getVoitures() declenche la lazy collection (OK car @Transactional
     * en cours).
     */
    public void supprimer(Long id) {
        Categorie c = findById(id);
        if (!c.getVoitures().isEmpty()) {
            throw new BusinessException("Impossible de supprimer : des voitures utilisent cette categorie");
        }
        repo.delete(c);
    }
}

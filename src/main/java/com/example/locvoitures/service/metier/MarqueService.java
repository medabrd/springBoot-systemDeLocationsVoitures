/*
 * ============================================================================
 * MarqueService - Service metier des marques de vehicules
 * ============================================================================
 *
 * Role :
 *   CRUD des marques. Particularite : gere l'upload du logo (image) via
 *   FileStorageService -> creation/modification prennent un MultipartFile
 *   en parametre.
 *
 * Appele depuis :
 *   - controller/AdminMarqueController : CRUD admin
 *   - controller/CatalogueController : findAll() pour le filtre marque
 *   - service/VoitureService : findById pour associer
 *
 * Appelle :
 *   - MarqueRepository : CRUD
 *   - FileStorageService : storeImage / delete pour les logos
 *
 * Gardes metier :
 *   - Unicite du nom (case-insensitive)
 *   - Suppression refusee si la marque est utilisee par >= 1 voiture
 *
 * Cycle de vie du logo :
 *   - creer : si logoFile present, stocke et associe ; sinon marque sans logo
 *   - modifier : si nouveau logoFile, supprime l'ancien apres copie
 *   - supprimer : supprime le fichier logo associe avant le delete entite
 * ============================================================================
 */
package com.example.locvoitures.service.metier;

import com.example.locvoitures.service.utils.FileStorageService;

import com.example.locvoitures.entity.Marque;
import com.example.locvoitures.exception.BusinessException;
import com.example.locvoitures.exception.ResourceNotFoundException;
import com.example.locvoitures.repository.MarqueRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class MarqueService {

    private final MarqueRepository repo;
    private final FileStorageService fileStorage;

    /**
     * Liste complete pour dropdowns.
     */
    @Transactional(readOnly = true)
    public List<Marque> findAll() {
        return repo.findAll();
    }

    /**
     * Recherche par id (404 si absent).
     */
    @Transactional(readOnly = true)
    public Marque findById(Long id) {
        return repo.findById(id).orElseThrow(() -> new ResourceNotFoundException("Marque", id));
    }

    /**
     * Creation avec upload optionnel du logo.
     */
    public Marque creer(String nom, MultipartFile logoFile) {
        if (repo.existsByNomIgnoreCase(nom)) {
            throw new BusinessException("Une marque avec ce nom existe deja");
        }
        Marque marque = Marque.builder().nom(nom).build();
        // Si l'admin a uploade un fichier, on le stocke et on associe le chemin
        if (logoFile != null && !logoFile.isEmpty()) {
            marque.setLogo(fileStorage.storeImage(logoFile, "marques"));
        }
        return repo.save(marque);
    }

    /**
     * Modification : nom et/ou logo. Si nouveau logo, l'ancien est supprime
     * physiquement APRES la copie reussie (defense : si copie echoue,
     * l'ancien fichier reste valide).
     */
    public Marque modifier(Long id, String nom, MultipartFile logoFile) {
        Marque m = findById(id);
        if (!m.getNom().equalsIgnoreCase(nom) && repo.existsByNomIgnoreCase(nom)) {
            throw new BusinessException("Une marque avec ce nom existe deja");
        }
        m.setNom(nom);
        if (logoFile != null && !logoFile.isEmpty()) {
            String ancien = m.getLogo();
            // Store d'abord (peut echouer), puis delete de l'ancien
            m.setLogo(fileStorage.storeImage(logoFile, "marques"));
            fileStorage.delete(ancien);
        }
        return m;
    }

    /**
     * Suppression : refuse si la marque est utilisee, sinon delete fichier + entite.
     */
    public void supprimer(Long id) {
        Marque m = findById(id);
        if (!m.getVoitures().isEmpty()) {
            throw new BusinessException("Impossible de supprimer : des voitures utilisent cette marque");
        }
        // Cleanup fichier logo (idempotent)
        fileStorage.delete(m.getLogo());
        repo.delete(m);
    }
}

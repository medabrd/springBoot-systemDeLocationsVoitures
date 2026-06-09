/*
 * ============================================================================
 * AvisService - Logique metier autour des avis clients
 * ============================================================================
 *
 * Role :
 *   Couche service entre les controllers et le repository. Encapsule toutes
 *   les regles metier liees aux avis : creation conditionnee a une location
 *   TERMINEE non encore notee, moderation par l'admin, lectures paginees.
 *
 * Appele depuis :
 *   - ClientLocationController.soumettreAvis() : POST /client/locations/{id}/avis
 *     apres soumission du formulaire AvisForm. Le controller valide le DTO
 *     puis delegue le metier ici.
 *   - AdminAvisController.liste() : GET /admin/avis (moderation, lecture
 *     paginee via findAll())
 *   - AdminAvisController.supprimer() : POST /admin/avis/{id}/supprimer
 *
 * Appelle :
 *   - AvisRepository (injecte par constructeur Lombok @RequiredArgsConstructor) :
 *     existsByLocation (anti-doublon), save (persist), findById (lookup),
 *     findAll (moderation), delete
 *   - Pas d'appel a d'autres services pour rester ciblee. La verification
 *     metier "client proprietaire de la location" se fait sur les entites
 *     deja chargees (location.getClient()).
 *
 * Gardes metier appliquees dans creerAvis() :
 *   1. Le client qui depose doit etre proprietaire de la location
 *   2. La location doit etre en statut TERMINEE
 *   3. Aucun avis ne doit deja exister pour cette location (unicite OneToOne)
 *   4. La note doit etre entre 1 et 5
 *   5. Le commentaire ne doit pas etre vide
 *   Violation -> BusinessException interceptee par GlobalExceptionHandler
 *   ou le controller (try/catch) pour message d'erreur dans la vue.
 *
 * Note transactionnelle :
 *   @Transactional au niveau classe -> toutes les methodes write sont
 *   transactionnelles. Les methodes purement de lecture sont annotees
 *   readOnly=true pour optimiser (pas de flush, pas de dirty checking).
 * ============================================================================
 */
package com.example.locvoitures.service.metier;

// Entites manipulees
import com.example.locvoitures.entity.Avis;
import com.example.locvoitures.entity.Client;
import com.example.locvoitures.entity.Location;

// Enum pour verification de statut
import com.example.locvoitures.enumeration.StatutLocation;

// Exceptions métier (BusinessException = 400-like, ResourceNotFound = 404-like)
import com.example.locvoitures.exception.BusinessException;
import com.example.locvoitures.exception.ResourceNotFoundException;

// Repository injecte
import com.example.locvoitures.repository.AvisRepository;

// Lombok : injection par constructeur (sur les champs final) + logger
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

// Pagination Spring Data
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

// Annotations Spring : @Service marque le bean, @Transactional gere les transactions
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service               declare le bean Spring (specialise de @Component
 *                        avec semantique "logique metier")
 * RequiredArgsConstructor Lombok : genere un constructeur acceptant tous
 *                        les champs final, ce qui declenche l'injection
 *                        Spring par constructeur (preferable a @Autowired
 *                        sur champ car immuable et testable)
 * Transactional         applique une transaction sur chaque methode
 *                        publique. Rollback automatique en cas de
 *                        RuntimeException (BusinessException herite de
 *                        RuntimeException)
 * Slf4j                 Lombok : injecte un Logger "log" prive statique
 */
@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class AvisService {

    /**
     * Injecte via @RequiredArgsConstructor (champ final).
     * Pas d'@Autowired explicite : depuis Spring 4.3, un constructeur unique
     * est automatiquement utilise pour l'injection.
     */
    private final AvisRepository avisRepo;

    /**
     * Recherche par ID. readOnly=true optimise la transaction (Hibernate ne
     * fait pas de dirty checking au commit).
     * orElseThrow : si findById renvoie Optional.empty(), on leve une
     * ResourceNotFoundException (interceptee par GlobalExceptionHandler
     * pour renvoyer une 404).
     */
    @Transactional(readOnly = true)
    public Avis findById(Long id) {
        return avisRepo.findById(id).orElseThrow(() -> new ResourceNotFoundException("Avis", id));
    }

    /**
     * Lecture paginee de tous les avis (cote admin).
     */
    @Transactional(readOnly = true)
    public Page<Avis> findAll(Pageable pageable) {
        return avisRepo.findAll(pageable);
    }

    /**
     * Cree l'avis pour une location donnee.
     * Pre-conditions : location TERMINEE, pas d'avis existant, client proprietaire.

     * Note : les parametres location et client sont deja des entites JPA
     * (chargees en amont par le controller). On evite ainsi un re-find ici.
     */
    public void creerAvis(Location location, Client client, int note, String commentaire) {
        // Garde 1 : proprietaire de la location ?
        // Comparaison via getId() car les entites peuvent etre des proxies
        // (lazy loading) qui ne sont pas == au sens reference.
        if (!location.getClient().getId().equals(client.getId())) {
            throw new BusinessException("Vous ne pouvez laisser un avis que sur vos propres locations");
        }
        // Garde 2 : statut TERMINEE obligatoire (avis post-restitution)
        if (location.getStatut() != StatutLocation.TERMINEE) {
            throw new BusinessException("Vous ne pouvez laisser un avis qu'une fois la location terminee");
        }
        // Garde 3 : un seul avis par location (existsBy est plus rapide qu'un findBy)
        if (avisRepo.existsByLocation(location)) {
            throw new BusinessException("Un avis a deja ete depose pour cette location");
        }
        // Garde 4 : validation note (doublon de @Min/@Max cote DTO mais
        // protection defense-en-profondeur si appelant bypass le DTO)
        if (note < 1 || note > 5) {
            throw new BusinessException("La note doit etre comprise entre 1 et 5");
        }
        // Garde 5 : commentaire non vide
        if (commentaire == null || commentaire.isBlank()) {
            throw new BusinessException("Le commentaire est obligatoire");
        }

        // Build via le pattern Builder genere par Lombok @Builder
        Avis avis = Avis.builder()
                .location(location)
                .note(note)
                .commentaire(commentaire.trim())  // trim defense-en-profondeur
                .build();
        // save() de JpaRepository : INSERT si transient, UPDATE si detache.
        // Ici toujours INSERT car nouvelle entite. Le @PrePersist de Avis
        // initialise dateCreation = now.
        avisRepo.save(avis);
        log.info("Avis cree pour location {} : note={}", location.getId(), note);
    }

    /**
     * Suppression d'un avis (moderation par l'admin).
     * Pas de garde metier specifique : on fait confiance au controller
     * qui n'expose cette action qu'aux ROLE_ADMIN via Spring Security.
     */
    public void supprimerAvis(Long id) {
        Avis a = findById(id);  // 404 si introuvable
        avisRepo.delete(a);
        log.info("Avis {} supprime par admin", id);
    }
}

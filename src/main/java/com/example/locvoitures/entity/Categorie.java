/*
 * ============================================================================
 * Categorie - Categorie commerciale d'une voiture
 * ============================================================================
 *
 * Role :
 *   Regroupement marketing/tarifaire des vehicules (ex: "Citadine",
 *   "SUV", "Berline", "Sportive"). Permet le filtrage du catalogue cote
 *   client et la segmentation du parc cote admin.
 *
 * Utilise par :
 *   - entity/Voiture.java : ManyToOne categorie (FK categorie_id)
 *   - repository/CategorieRepository : CRUD + lookups
 *   - service/CategorieService : couche metier
 *   - controller/AdminCategorieController : CRUD admin (/admin/categories)
 *   - controller/CatalogueController : filtre dropdown dans la recherche
 *   - dto/VoitureForm.java : selection lors de la creation/modif d'une voiture
 *   - templates/admin/categories/* et templates/catalogue/liste.html
 *
 * Persistence :
 *   Table "categorie" (cree par Hibernate via ddl-auto=update). Nom unique
 *   pour eviter les doublons (contrainte UNIQUE en base + verifiee cote
 *   service avant insertion pour message d'erreur explicite).
 *
 * Suppression :
 *   Pas de cascade. Si l'admin tente de supprimer une categorie utilisee
 *   par des voitures, la FK leve une ConstraintViolationException attrapee
 *   en service -> BusinessException explicite.
 * ============================================================================
 */
package com.example.locvoitures.entity;

// JPA core : @Entity, @Table, @Column, @Id, @GeneratedValue, @OneToMany, @JoinColumn
// Le wildcard "*" importe toutes les annotations du package jakarta.persistence
import jakarta.persistence.*;

// Bean Validation : contraintes sur les champs evaluees lors de la validation Spring MVC
// (par @Valid sur un DTO ou par Hibernate Validator avant flush)
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// Lombok : @Getter/@Setter/@NoArgsConstructor/@AllArgsConstructor/@Builder/@ToString
// generes a la compilation -> reduit le boilerplate
import lombok.*;

// Set + HashSet pour la collection OneToMany (Set evite les doublons de Voiture)
import java.util.HashSet;
import java.util.Set;

/**
 * Entity                marque la classe comme entite JPA (mappee a une table)
 * Table(name="categorie") nom de table explicite (sinon Hibernate utiliserait
 *                          "categorie" en lowercase par defaut, donc redondant
 *                          mais explicite)
 * Getter @Setter        generent get/set pour chaque champ
 * NoArgsConstructor     requis par JPA (proxy Hibernate)
 * AllArgsConstructor    requis par @Builder
 * Builder               pattern builder pour construction lisible
 * ToString(exclude=...) exclut la collection "voitures" du toString pour
 *                        eviter une boucle infinie / lazy init exception
 */
@Entity
@Table(name = "categorie")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString(exclude = "voitures")
public class Categorie {

    /**
     * Cle primaire auto-incrementee par MySQL (IDENTITY).
     * Long plutot que long pour pouvoir distinguer "pas encore persistee" (null)
     * de "persistee avec id=0".
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Nom affiche dans les dropdowns et listes. Unique pour eviter "SUV" en double.
     * NotBlank : non null + non vide apres trim (different de @NotNull)
     * Size(max=50) : limite cote validation
     * Column(nullable=false, unique=true, length=50) : meme contrainte cote DDL
     */
    @NotBlank(message = "Le nom de la categorie est obligatoire")
    @Size(max = 50)
    @Column(nullable = false, unique = true, length = 50)
    private String nom;

    /**
     * Description optionnelle (affichee dans la fiche admin). Pas de @NotBlank
     * car facultatif.
     */
    @Size(max = 255)
    private String description;

    /**
     * Cote inverse de la relation Voiture -> Categorie.
     * mappedBy="categorie" pointe sur le champ "categorie" de Voiture (cote owning).
     * fetch=LAZY : on ne charge pas la liste des voitures a chaque lecture de
     * Categorie (sauf si on appelle explicitement getVoitures() dans une
     * transaction ouverte).
     * Builder.Default : initialise a un Set vide quand on utilise le builder
     * (sinon le builder mettrait null par defaut).
     */
    @OneToMany(mappedBy = "categorie", fetch = FetchType.LAZY)
    @Builder.Default
    private Set<Voiture> voitures = new HashSet<>();
}

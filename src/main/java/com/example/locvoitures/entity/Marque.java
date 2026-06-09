/*
 * ============================================================================
 * Marque - Marque (constructeur) d'un vehicule
 * ============================================================================
 *
 * Role :
 *   Identifie le constructeur (Renault, Peugeot, BMW...) avec son logo.
 *   Permet de filtrer le catalogue par marque et d'afficher le logo dans
 *   les fiches voiture.
 *
 * Utilise par :
 *   - entity/Voiture.java : ManyToOne marque (FK marque_id)
 *   - repository/MarqueRepository : CRUD + lookups
 *   - service/MarqueService : creer/modifier (gere l'upload du logo via
 *     FileStorageService)
 *   - controller/AdminMarqueController : /admin/marques (CRUD admin)
 *   - controller/CatalogueController : filtre dropdown
 *   - dto/MarqueForm.java : DTO de formulaire avec MultipartFile pour le logo
 *   - templates/admin/marques/* et templates/catalogue/liste.html
 *
 * Historique :
 *   Champ "logo" etait initialement une URL externe. Refactore pour devenir
 *   un chemin relatif vers un fichier uploade (uploads/marques/), gere par
 *   FileStorageService.
 *
 * Persistence :
 *   Nom unique en base. La suppression refuse si la marque est utilisee
 *   par au moins une voiture (ConstraintViolation cote DB rattrapee en
 *   service).
 * ============================================================================
 */
package com.example.locvoitures.entity;

// JPA core
import jakarta.persistence.*;

// Validation Bean
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// Lombok
import lombok.*;

// Collection pour la relation OneToMany inverse
import java.util.HashSet;
import java.util.Set;

/**
 * Entity / @Table : table "marque"
 */
@Entity
@Table(name = "marque")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString(exclude = "voitures")
public class Marque {

    /**
     * Cle primaire auto-incrementee.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Nom de la marque. Unique (pas de doublons "Renault" / "renault").
     */
    @NotBlank
    @Size(max = 50)
    @Column(nullable = false, unique = true, length = 50)
    private String nom;

    /**
     * Chemin relatif vers le fichier logo dans uploads/marques/.
     * Optionnel : si null, la UI affiche un placeholder.
     */
    @Size(max = 255)
    private String logo;

    /**
     * Cote inverse de Voiture.marque. Navigation depuis Marque vers ses
     * voitures (utile en admin pour le compteur "X voitures dans la marque Y").
     */
    @OneToMany(mappedBy = "marque", fetch = FetchType.LAZY)
    @Builder.Default
    private Set<Voiture> voitures = new HashSet<>();
}

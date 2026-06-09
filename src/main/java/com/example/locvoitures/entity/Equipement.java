/*
 * ============================================================================
 * Equipement - Equipement / option disponible sur une voiture
 * ============================================================================
 *
 * Role :
 *   Catalogue d'options proposees sur les vehicules (Climatisation, GPS,
 *   Toit ouvrant, Siege bebe...). Relation ManyToMany avec Voiture : un
 *   meme equipement peut equiper plusieurs voitures, une voiture peut
 *   cumuler plusieurs equipements.
 *
 * Utilise par :
 *   - entity/Voiture.java : ManyToMany equipements (owning side, table de
 *     jointure "voiture_equipement")
 *   - repository/EquipementRepository : CRUD + lookup
 *   - service/EquipementService : couche metier
 *   - controller/AdminEquipementController : /admin/equipements (CRUD admin)
 *   - controller/CatalogueController : filtre multi-equipements sur le catalogue
 *   - dto/VoitureForm.java : selection multiple cote formulaire admin
 *   - templates/admin/equipements/* : CRUD
 *   - templates/catalogue/details.html : affichage des equipements presents
 *
 * Historique :
 *   Initialement avait un champ "icone" (nom Font Awesome) qui couplait
 *   l'entite a la presentation. Refactore vers "description" pour decoupler
 *   le modele metier de la UI.
 *
 * Suppression :
 *   Pas de cascade. Si on supprime un equipement, Hibernate retire d'abord
 *   les lignes de la table de jointure (cote Voiture.equipements).
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

// Set pour la collection inverse
import java.util.HashSet;
import java.util.Set;

/**
 * Entity / @Table : table "equipement"
 * ToString(exclude="voitures") : evite de declencher la lazy collection
 *                                 et eviter une boucle bidirectionnelle.
 */
@Entity
@Table(name = "equipement")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString(exclude = "voitures")
public class Equipement {

    /**
     * Cle primaire auto-incrementee.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Nom de l'equipement. Unique pour eviter "GPS" en double.
     */
    @NotBlank(message = "Le nom de l'equipement est obligatoire")
    @Size(max = 50)
    @Column(nullable = false, unique = true, length = 50)
    private String nom;

    /**
     * Description courte facultative (visible dans la fiche admin).
     */
    @Size(max = 255)
    private String description;

    /**
     * Cote inverse de la ManyToMany. Le owning side est Voiture.equipements
     * (qui porte le @JoinTable). mappedBy="equipements" pointe sur ce champ.
     * fetch=LAZY : on ne charge pas la liste des voitures equipees a chaque
     * lecture d'un Equipement.
     */
    @ManyToMany(mappedBy = "equipements", fetch = FetchType.LAZY)
    @Builder.Default
    private Set<Voiture> voitures = new HashSet<>();
}

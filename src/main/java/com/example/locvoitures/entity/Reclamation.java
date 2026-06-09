/*
 * ============================================================================
 * Reclamation - Reclamation deposee par un client sur une location
 * ============================================================================
 *
 * Role :
 *   Permet au client de signaler un probleme rencontre pendant ou apres
 *   une location (panne, accident, equipement defaillant...). L'admin
 *   traite la reclamation et peut envoyer une reponse (mail) en la cloturant.
 *
 * Utilise par :
 *   - entity/Location.java : OneToMany inverse (Reclamation.location porte la FK)
 *   - repository/ReclamationRepository : CRUD + recherche par client/statut
 *   - service/ReclamationService : creer (par client), cloturer (par admin)
 *   - service/EmailService.envoyerReponseReclamation() : envoie la reponse
 *   - controller/ClientLocationController.creerReclamation : POST client
 *   - controller/AdminReclamationController : /admin/reclamations (liste,
 *     details, cloturer)
 *   - templates/client/reclamations/* et templates/admin/reclamations/*
 *
 * Workflow :
 *   EN_TRAITEMENT (a la creation, defaut au @PrePersist)
 *   -> CLOTUREE (apres action admin, reponseAdmin facultative)
 *
 * Persistence :
 *   FK location_id nullable : preserve la reclamation si la location est
 *   supprimee par l'admin (audit).
 * ============================================================================
 */
package com.example.locvoitures.entity;

// Enums metier
import com.example.locvoitures.enumeration.CategorieReclamation;
import com.example.locvoitures.enumeration.StatutReclamation;

// JPA core
import jakarta.persistence.*;

// Validation Bean (wildcard)
import jakarta.validation.constraints.*;

// Lombok
import lombok.*;

// LocalDateTime pour les timestamps
import java.time.LocalDateTime;

/**
 * Entity / @Table : table "reclamation"
 * ToString(exclude="location") : evite la lazy init de la location
 */
@Entity
@Table(name = "reclamation")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString(exclude = "location")
public class Reclamation {

    /**
     * Cle primaire auto-incrementee.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Categorie metier (PANNE, ACCIDENT, ...). Choisie par le client au
     * depot via un dropdown.
     */
    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CategorieReclamation categorie;

    /**
     * Description detaillee du probleme. Max 1000 caracteres pour eviter
     * les pavés excessifs.
     */
    @NotBlank
    @Size(max = 1000)
    @Column(nullable = false, length = 1000)
    private String description;

    /**
     * Chemin relatif vers une photo justificative (optionnel).
     * Stockee dans uploads/reclamations/.
     */
    @Size(max = 255)
    private String photo;

    /**
     * Statut courant. @Builder.Default = EN_TRAITEMENT a la creation via builder.
     */
    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private StatutReclamation statut = StatutReclamation.EN_TRAITEMENT;

    /**
     * Message de reponse saisi par l'admin a la cloture. Facultatif :
     * l'admin peut cloturer sans envoyer de reponse.
     */
    @Size(max = 1000)
    @Column(length = 1000)
    private String reponseAdmin;

    /**
     * Timestamp de creation. updatable=false : immuable.
     */
    @Column(nullable = false, updatable = false)
    private LocalDateTime dateCreation;

    /**
     * Timestamp de cloture (= moment ou l'admin a passe le statut a CLOTUREE).
     */
    private LocalDateTime dateReponse;

    /**
     * PrePersist : initialise dateCreation a now() avant INSERT.
     */
    @PrePersist
    protected void onCreate() {
        if (this.dateCreation == null) {
            this.dateCreation = LocalDateTime.now();
        }
    }

    /**
     * Location concernee.
     * Nullable car les reclamations sont conservees apres suppression de la
     * location par l'admin (templates affichent "Location supprimee" si null).
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "location_id")
    private Location location;
}

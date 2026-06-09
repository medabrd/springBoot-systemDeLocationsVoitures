/*
 * ============================================================================
 * Utilisateur - Classe mere abstraite : compte d'authentification
 * ============================================================================
 *
 * Role :
 *   Porte les donnees communes a tout compte utilisateur :
 *     - identite (email, nom, prenom)
 *     - authentification (hash mot de passe, actif, tokens)
 *     - audit (dateInscription)
 *
 *   N'EST PAS instanciable directement. Les comptes concrets sont
 *   representes par les sous-classes :
 *     - Client : utilisateur final loueur (avec documents et locations)
 *     - Admin  : gestionnaire de l'agence (avec poste, dateEmbauche,
 *                bannissements prononces)
 *
 *   Le polymorphisme JPA repose sur la strategie JOINED :
 *     - une table "utilisateur" contient les champs communs
 *     - une table "client" partage la PK avec utilisateur et porte les
 *       champs specifiques au client
 *     - une table "admin" partage la PK avec utilisateur et porte les
 *       champs specifiques a l'admin
 *
 *   A chaque lecture polymorphe, Hibernate fait un JOIN sur les 3 tables
 *   (ou la sous-classe ciblee) pour reconstruire l'instance concrete.
 *
 * Utilise par :
 *   - entity/Client, entity/Admin : sous-classes concretes
 *   - service/CustomUserDetailsService : findByEmail + instanceof pour
 *     determiner le role Spring Security
 *   - repository/UtilisateurRepository : findByEmail (polymorphe)
 *   - controller/AuthUtil : recupere l'instance concrete via SecurityContext
 *
 * Securite :
 *   motDePasse contient un hash BCrypt, jamais la valeur en clair.
 *   tokenActivation et tokenReinitialisation sont des UUID a courte duree
 *   de vie, effaces apres consommation (single-use).
 * ============================================================================
 */
package com.example.locvoitures.entity;

// JPA core - les annotations d'heritage sont ici
import jakarta.persistence.*;

// Validation Bean
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// Lombok
import lombok.*;
import lombok.experimental.SuperBuilder;

// LocalDateTime pour les timestamps techniques
import java.time.LocalDateTime;

/**
 * Entity                  marque la classe comme entite JPA (table dediee)
 * Inheritance(JOINED)     strategie d'heritage : une table par classe,
 *                          partageant la PK
 * Table(name="utilisateur") table de la classe mere
 * Getter / @Setter        Lombok genere les accesseurs
 * NoArgsConstructor       requis par JPA (proxy Hibernate)
 * SuperBuilder            comme @Builder mais propage aux sous-classes
 *                          (sinon les sous-classes ne peuvent pas utiliser
 *                          un builder qui inclut les champs de la mere)
 * ToString(exclude=...)   evite la lazy init et les boucles bidirectionnelles
 */
@Entity
@Inheritance(strategy = InheritanceType.JOINED)
@Table(name = "utilisateur")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@ToString
public abstract class Utilisateur {

    /**
     * Cle primaire auto-incrementee. Les sous-classes (Client, Admin)
     * partagent la meme valeur d'id via @PrimaryKeyJoinColumn implicite
     * dans la strategie JOINED.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Email = login. Unique en base + index naturel.
     * La validation visible cote utilisateur vient des DTOs.
     */
    @NotBlank
    @Email
    @Size(max = 100)
    @Column(nullable = false, unique = true, length = 100)
    private String email;

    /**
     * Hash BCrypt du mot de passe.
     */
    @Column(nullable = false, length = 100)
    private String motDePasse;

    /**
     * Nom de famille.
     */
    @NotBlank
    @Size(max = 50)
    @Column(nullable = false, length = 50)
    private String nom;

    /**
     * Prenom.
     */
    @NotBlank
    @Size(max = 50)
    @Column(nullable = false, length = 50)
    private String prenom;

    /**
     * actif=false a la creation tant que l'email n'est pas verifie.
     * Pour Admin, actif=true des la creation (voir UtilisateurService.creerAdmin).
     */
    @Column(nullable = false)
    @Builder.Default
    private boolean actif = false;

    /**
     * Token UUID pose a l'inscription, vide une fois le compte active.
     */
    @Column(length = 100)
    private String tokenActivation;

    /**
     * Token UUID pose lors d'une demande de reinitialisation de mdp.
     */
    @Column(length = 100)
    private String tokenReinitialisation;

    /**
     * Expiration du tokenReinitialisation (typiquement now + 1h).
     */
    private LocalDateTime dateExpirationToken;

    /**
     * Date d'inscription. updatable=false : immuable.
     */
    @Column(nullable = false, updatable = false)
    private LocalDateTime dateInscription;

    /**
     * PrePersist : initialise dateInscription a now() avant INSERT.
     */
    @PrePersist
    protected void onCreate() {
        if (this.dateInscription == null) {
            this.dateInscription = LocalDateTime.now();
        }
    }
}

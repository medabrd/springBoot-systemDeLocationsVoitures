/*
 * ============================================================================
 * ConducteurSecondaire - Personne autorisee a conduire un vehicule loue
 * ============================================================================
 *
 * Role :
 *   Optionnellement, un client peut declarer une autre personne autorisee a
 *   conduire le vehicule loue. Cette entite stocke les infos du second
 *   conducteur (nom, prenom, CIN+photo) et reference le permis via une
 *   relation @OneToOne vers l'entite Permis (coherent avec Client.permis).
 *
 * Relation Permis :
 *   @OneToOne sans contrainte d'unicite : un meme Permis (par numero unique)
 *   peut etre reference par plusieurs ConducteurSecondaire (le meme cousin
 *   declare sur 2 locations). PermisService gere la reutilisation.
 *
 *   Cascade PERSIST+MERGE : si le Permis est neuf, il est insere via la
 *   sauvegarde du ConducteurSecondaire. Pas de REMOVE : si la location est
 *   supprimee, le ConducteurSecondaire l'est aussi (cascade depuis Location)
 *   mais le Permis SURVIT (preserve son eventuel ban + reutilisable).
 *
 * Utilise par :
 *   - entity/Location.java : OneToOne conducteurSecondaire
 *   - service/LocationService.soumettreDemande() : creation transactionnelle
 *   - service/PermisService : construirePourSecondConducteur (reutilise un
 *     Permis existant ou en cree un nouveau)
 *   - controller/ClientLocationController : recoit les champs via DTO
 * ============================================================================
 */
package com.example.locvoitures.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import lombok.*;

@Entity
@Table(name = "conducteur_secondaire")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString(exclude = "permis")
public class ConducteurSecondaire {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    @Size(max = 50)
    @Column(nullable = false, length = 50)
    private String nom;

    @NotBlank
    @Size(max = 50)
    @Column(nullable = false, length = 50)
    private String prenom;

    /**
     * Permis du second conducteur. ManyToOne (pas OneToOne) : un meme
     * Permis peut etre reference par plusieurs ConducteurSecondaire (le
     * meme cousin declare sur plusieurs locations).
     * Cascade PERSIST+MERGE : insertion auto si Permis neuf.
     */
    @NotNull
    @ManyToOne(cascade = {CascadeType.PERSIST, CascadeType.MERGE}, fetch = FetchType.LAZY)
    @JoinColumn(name = "permis_id", nullable = false)
    private Permis permis;

    @NotBlank
    @Size(max = 30)
    @Column(nullable = false, length = 30)
    private String numeroCIN;

    @NotBlank
    @Size(max = 255)
    @Column(nullable = false)
    private String photoCIN;
}

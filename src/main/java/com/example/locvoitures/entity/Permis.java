/*
 * ============================================================================
 * Permis - Permis de conduire (avec bannissement integre)
 * ============================================================================
 *
 * Role :
 *   Modelise un permis de conduire connu de l'agence. Sert a la fois :
 *   - Au profil du Client (qui en possede 0 ou 1) : numero, dates, photo
 *   - A la gestion de la liste noire : flag banni + motif + date + admin
 *
 *   Le concept de "permis banni" est porte directement par cette entite
 *   (banni=true) plutot que par une table PermisBanni separee. Ainsi :
 *   - Un Permis avec banni=true est interdit a la location
 *   - Le ban survit a la suppression du compte Client (le Permis n'est pas
 *     supprime en cascade -- voir Client.permis)
 *   - Un Permis peut exister "orphelin" (sans Client) si l'admin bannit
 *     un numero pour lequel aucun compte n'est cree
 *
 * Relation Client :
 *   Client <-OneToOne-> Permis. Client porte la FK (permis_id), Permis
 *   ne connait pas son Client (lookup via ClientRepository.findByPermisNumero).
 *   Cascade Client.permis = PERSIST+MERGE (pas REMOVE) pour preserver le
 *   permis et son ban.
 *
 * Utilise par :
 *   - entity/Client : @OneToOne permis
 *   - service/BannissementService : bannir/debannir via permis.banni
 *   - service/LocationService : verification estValide() et estBanni()
 *   - controller/AdminPermisBanniController : ecran CRUD
 *
 * Validite :
 *   estValide() = dateExpiration > aujourd'hui ET non banni.
 *   estPretALouer() = numero + dates + photo + non banni + expiration future.
 * ============================================================================
 */
package com.example.locvoitures.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "permis")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString(exclude = "adminBannisseur")
public class Permis {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Numero officiel du permis (unique cross-clients).
     */
    @NotBlank
    @Size(max = 30)
    @Column(nullable = false, unique = true, length = 30)
    private String numero;

    /**
     * Date d'obtention du permis. Nullable en base : permet une saisie
     * progressive (l'admin enregistre le numero au bureau, le client
     * complete plus tard, ou un Permis orphelin cree pour ban seul).
     */
    private LocalDate dateObtention;

    /**
     * Date d'expiration. Nullable en base (saisie progressive).
     */
    private LocalDate dateExpiration;

    /**
     * Chemin relatif vers la photo (uploads/permis/). Nullable en base.
     */
    @Size(max = 255)
    private String photo;

    // ============================================================
    // Bannissement (fusionne depuis l'ancienne entite PermisBanni)
    // ============================================================

    /**
     * true = ce permis est sur la liste noire (location interdite).
     */
    @Column(nullable = false)
    @Builder.Default
    private boolean banni = false;

    /**
     * Motif du bannissement. Renseigne uniquement si banni=true.
     */
    @Size(max = 500)
    @Column(length = 500)
    private String motifBan;

    /**
     * Date du bannissement. Renseignee uniquement si banni=true.
     */
    private LocalDateTime dateBan;

    /**
     * Admin ayant prononce le ban. Audit. Renseigne uniquement si banni=true.
     * ManyToOne lazy : pas de chargement systematique lors de la lecture
     * d'un Permis.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "admin_bannisseur_id")
    private Admin adminBannisseur;

    /**
     * true si dateExpiration > aujourd'hui (permis non perime).
     */
    @Transient
    public boolean expirationValide() {
        return dateExpiration != null && dateExpiration.isAfter(LocalDate.now());
    }
}

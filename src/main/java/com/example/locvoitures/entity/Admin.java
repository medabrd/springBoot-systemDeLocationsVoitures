/*
 * ============================================================================
 * Admin - Compte administrateur, sous-classe concrete de Utilisateur
 * ============================================================================
 *
 * Role :
 *   Specialise un Utilisateur en gestionnaire interne de l'agence. Ajoute
 *   les attributs metier propres au personnel :
 *     - posteOccupe : poste interne (DIRECTION, ACCUEIL, ...)
 *     - dateEmbauche : audit / anciennete
 *
 *   Heritage JPA JOINED : la table "admin" partage sa PK avec "utilisateur".
 *
 * Utilise par :
 *   - repository/AdminRepository : CRUD specifique admin
 *   - service/UtilisateurService.creerAdmin : factory au boot
 *   - service/BannissementService.bannir : trace l'admin qui prononce le ban
 *     (cote proprietaire de la FK sur Permis.adminBannisseur)
 *   - service/ReclamationService : notification de tous les admins
 *
 * Cycle de vie :
 *   Cree par DataInitializer au boot (admin par defaut). Pas d'inscription
 *   publique : seul code applicatif peut creer un Admin.
 * ============================================================================
 */
package com.example.locvoitures.entity;

import com.example.locvoitures.enumeration.PosteAdmin;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;

import lombok.*;
import lombok.experimental.SuperBuilder;

import java.time.LocalDate;

@Entity
@Table(name = "admin")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@ToString(callSuper = true)
public class Admin extends Utilisateur {

    /**
     * Poste interne occupe. Stocke en VARCHAR via EnumType.STRING.
     */
    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private PosteAdmin posteOccupe;

    /**
     * Date d'embauche (audit RH / anciennete).
     */
    @NotNull
    @Column(nullable = false)
    private LocalDate dateEmbauche;
}

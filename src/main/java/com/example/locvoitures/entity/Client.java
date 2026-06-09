/*
 * ============================================================================
 * Client - Compte client (loueur), sous-classe concrete de Utilisateur
 * ============================================================================
 *
 * Role :
 *   Specialise un Utilisateur en client final capable de louer un vehicule.
 *   Ajoute les attributs metier propres a la relation commerciale :
 *     - telephone
 *     - documents : numero de permis + photo, numero CIN + photo
 *     - photo de profil
 *     - collection des locations passees et en cours
 *
 *   Heritage JPA JOINED : la table "client" partage sa PK avec "utilisateur"
 *   (@PrimaryKeyJoinColumn). Hibernate joint automatiquement les deux tables
 *   lors de la lecture d'un Client.
 *
 * Utilise par :
 *   - entity/Location.java : ManyToOne client (FK client_id)
 *   - repository/ClientRepository : CRUD specifique client
 *   - service/ClientService : MAJ profil, upload documents
 *   - service/LocationService.soumettreDemande() : verifie
 *     estProfilCompletPourLocation()
 *   - controller/ClientController : /client/profil
 *   - controller/AuthUtil.getCurrentClient() : retourne l'instance Client
 *     courante via cast depuis SecurityContext
 *
 * Documents :
 *   photoProfile, photoPermis, photoCIN sont des chemins relatifs vers des
 *   fichiers stockes dans uploads/ (gere par FileStorageService).
 *
 * Suppression :
 *   Suppression cote admin -> /admin/utilisateurs/{id}/supprimer.
 *   Pour preserver l'historique, Location.client est nullable et detache.
 * ============================================================================
 */
package com.example.locvoitures.entity;

// JPA core
import jakarta.persistence.*;

// Validation Bean
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

// Lombok
import lombok.*;
import lombok.experimental.SuperBuilder;

// Collections pour la liste des locations
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Entity / @Table(name="client") : table dediee aux champs specifiques client.
 * PrimaryKeyJoinColumn (implicite dans JOINED) : la colonne id de "client"
 *                       est a la fois PK locale et FK vers utilisateur.id.

 * ToString(callSuper=true, exclude="locations") : inclut les champs de
 *                       Utilisateur dans le toString et evite la lazy init
 *                       de la collection locations.
 */
@Entity
@Table(name = "client")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@ToString(callSuper = true, exclude = "locations")
public class Client extends Utilisateur {

    /**
     * Telephone : 8 a 20 caracteres compris dans [+0-9 ].
     */
    @Pattern(regexp = "^[+0-9 ]{8,20}$")
    @Column(length = 20)
    private String telephone;

    /**
     * Chemin relatif vers la photo de profil dans uploads/profils/.
     */
    @Size(max = 255)
    private String photoProfile;

    /**
     * Permis de conduire (numero, dates, photo, ban). Entite dediee.

     * - cascade PERSIST+MERGE : save(client) insere/MAJ le Permis attache.
     * - PAS de REMOVE ni orphanRemoval : si on supprime le Client, le
     *   Permis SURVIT (avec son ban eventuel). Idem si on detache : on
     *   garde la trace en base.
     * - FK permis_id portee par la table client (cote proprietaire).
     */
    @OneToOne(cascade = {CascadeType.PERSIST, CascadeType.MERGE}, fetch = FetchType.LAZY)
    @JoinColumn(name = "permis_id", unique = true)
    private Permis permis;

    /**
     * Numero de carte d'identite nationale. Unique en base.
     */
    @Size(max = 30)
    @Column(unique = true, length = 30)
    private String numeroCIN;

    /**
     * Chemin relatif vers la photo de la CIN (uploads/cin/).
     */
    @Size(max = 255)
    private String photoCIN;

    /**
     * Locations effectuees par ce client. Cote inverse de Location.client.
     */
    @OneToMany(mappedBy = "client", fetch = FetchType.LAZY)
    @Builder.Default
    private Set<Location> locations = new HashSet<>();

    /**
     * Helper : numero de permis (ou null si pas de permis). Evite les
     * client.getPermis().getNumero() dispatched everywhere.
     */
    //data base excluded , memory only , field visible via rest controller in json spring par defaut persiste les mthode qui commencent par get ou is
    @Transient
    public String getNumeroPermis() {
        return permis != null ? permis.getNumero() : null;
    }

    /**
     * Profil considere comme complet si tous les documents requis pour
     * louer sont presents et valides. Utilise par LocationService.
     */
    @Transient
    public boolean estProfilCompletPourLocation() {
        return getElementsManquants().isEmpty();
    }

    /**
     * Liste des champs manquants (UX : afficher exactement ce qu'il
     * reste a completer).
     */
    @Transient
    public List<String> getElementsManquants() {
        List<String> manquants = new ArrayList<>();
        if (telephone == null || telephone.isBlank())     manquants.add("Numero de telephone");
        if (permis == null) {
            manquants.add("Permis de conduire (numero, dates et photo)");
        } else {
            if (permis.isBanni())
                manquants.add("Permis banni : location interdite");
            if (permis.getNumero() == null || permis.getNumero().isBlank())
                manquants.add("Numero du permis de conduire");
            if (permis.getDateObtention() == null)
                manquants.add("Date d'obtention du permis");
            if (permis.getDateExpiration() == null)
                manquants.add("Date d'expiration du permis");
            else if (!permis.expirationValide())
                manquants.add("Permis expire (renouveler la date d'expiration)");
            if (permis.getPhoto() == null || permis.getPhoto().isBlank())
                manquants.add("Photo du permis de conduire");
        }
        if (numeroCIN == null || numeroCIN.isBlank())     manquants.add("Numero de CIN");
        if (photoCIN == null || photoCIN.isBlank())       manquants.add("Photo de la CIN");
        return manquants;
    }
}

/*
 * ============================================================================
 * PermisService - Service metier autour du Permis (document + bannissement)
 * ============================================================================
 *
 * Role :
 *   Couche metier UNIQUE pour le permis de conduire :
 *   - Creation / mise a jour des donnees du permis d'un Client (numero,
 *     dates, photo) avec controles de coherence et anti-doublon.
 *   - Bannissement / debannissement (flag porte par l'entite Permis depuis
 *     la fusion de l'ancien PermisBanni).
 *   - Verifications utilitaires (estPermisBanni, lookup) pour les flux de
 *     location.
 *
 * Appele depuis :
 *   - service/ClientService.mettreAJourProfil : delegue la branche permis
 *   - service/ClientService.creerParAdmin : delegue la construction du Permis
 *   - controller/AdminPermisBanniController : ecran liste/ajout/retrait ban
 *   - controller/AdminUtilisateurController : bannir/debannir depuis la fiche
 *   - service/LocationService : estPermisBanni avant insertion
 *
 * Appelle :
 *   - PermisRepository : CRUD
 *   - ClientRepository : retrouver le compte par numero pour notif email
 *   - FileStorageService : upload de la photo (uploads/permis/)
 *   - EmailService : notification bannissement / debannissement
 *
 * Concept metier :
 *   Le ban est porte directement par Permis.banni. Comme Client.permis est
 *   sans cascade REMOVE, supprimer un Client ne supprime pas son Permis ni
 *   son ban. Bannir un numero inconnu cree un Permis "orphelin" (sans Client).
 * ============================================================================
 */
package com.example.locvoitures.service.metier;

import com.example.locvoitures.service.utils.EmailService;
import com.example.locvoitures.service.utils.FileStorageService;

import com.example.locvoitures.entity.Admin;
import com.example.locvoitures.entity.Client;
import com.example.locvoitures.entity.Permis;
import com.example.locvoitures.exception.BusinessException;
import com.example.locvoitures.repository.ClientRepository;
import com.example.locvoitures.repository.PermisRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class PermisService {

    private final PermisRepository permisRepo;
    /**
     * Exception au principe "un service = son repo" : on garde une
     * dependance directe a ClientRepository pour les notifications email
     * post-bannissement (retrouver le Client associe a un numero de permis).
     * Deleguer a ClientService creerait un cycle ClientService<->PermisService
     * (ClientService.mettreAJourProfil appelle deja PermisService).
     * Lookup uniquement, pas d'ecriture.
     */
    private final ClientRepository clientRepo;
    private final FileStorageService fileStorage;
    private final EmailService emailService;

    // ============================================================
    // Gestion du document permis du Client
    // ============================================================

    /**
     * Met a jour (ou cree) le Permis attache a un Client a partir des champs
     * du formulaire profil. Les champs fournis ecrasent l'existant, les
     * champs null/blank sont ignores.

     * Si client.permis est null et qu'au moins un champ est fourni, on cree
     * un nouveau Permis et on l'attache au Client (cascade PERSIST le sauvera
     * avec le Client).

     * Verifications :
     *   - anti-doublon sur le numero (si modifie)
     *   - dateObtention dans le passe
     *   - dateExpiration > dateObtention si les deux sont posees
     */
    public void mettreAJourPourClient(Client client,
                                       String numero,
                                       LocalDate dateObtention,
                                       LocalDate dateExpiration,
                                       MultipartFile photo) {

        boolean rien = (numero == null || numero.isBlank())
                && dateObtention == null
                && dateExpiration == null
                && (photo == null || photo.isEmpty());
        if (rien) {
            return;
        }

        Permis permis = client.getPermis();

        // Le numero doit etre traite EN PREMIER : la verif anti-doublon via
        // findByNumero declenche un auto-flush Hibernate. Si on attache un
        // new Permis() au Client AVANT d'avoir set le numero, l'auto-flush
        // tente d'INSERT le Permis transient sans numero -> violation @NotBlank.
        // Ordre : lookup -> attach+set.
        if (numero != null && !numero.isBlank()) {
            String n = numero.trim();
            Long permisCourantId = permis != null ? permis.getId() : null;
            permisRepo.findByNumero(n).ifPresent(existing -> {
                if (!existing.getId().equals(permisCourantId)) {
                    throw new BusinessException("Ce numero de permis est deja utilise");
                }
            });
            if (permis == null) {
                permis = new Permis();
                client.setPermis(permis);
            }
            permis.setNumero(n);
        } else if (permis == null) {
            throw new BusinessException("Numero de permis requis avant de renseigner ses dates ou sa photo");
        }

        if (dateObtention != null) {
            if (dateObtention.isAfter(LocalDate.now())) {
                throw new BusinessException("La date d'obtention du permis ne peut pas etre dans le futur");
            }
            permis.setDateObtention(dateObtention);
        }
        if (dateExpiration != null) {
            permis.setDateExpiration(dateExpiration);
        }

        // Coherence si les deux dates sont posees
        if (permis.getDateObtention() != null && permis.getDateExpiration() != null
                && !permis.getDateExpiration().isAfter(permis.getDateObtention())) {
            throw new BusinessException("La date d'expiration doit etre posterieure a la date d'obtention");
        }

        if (photo != null && !photo.isEmpty()) {
            fileStorage.delete(permis.getPhoto());
            permis.setPhoto(fileStorage.storeImage(photo, "permis"));
        }
    }

    /**
     * Construit un Permis a partir des donnees recoltees a l'accueil par
     * l'admin (creerClientParAdmin). Retourne null si aucun champ n'est
     * fourni. Verifications : numero unique, dates coherentes.
     * Pas d'upload photo a cette etape (le client uploadera depuis son
     * profil).
     */
    public Permis construirePourNouveauClient(String numero,
                                                LocalDate dateObtention,
                                                LocalDate dateExpiration) {
        if (numero == null || numero.isBlank()) {
            return null;
        }
        String n = numero.trim();
        if (permisRepo.existsByNumero(n)) {
            throw new BusinessException("Ce numero de permis est deja utilise");
        }
        if (dateObtention != null && dateObtention.isAfter(LocalDate.now())) {
            throw new BusinessException("La date d'obtention du permis ne peut pas etre dans le futur");
        }
        if (dateObtention != null && dateExpiration != null
                && !dateExpiration.isAfter(dateObtention)) {
            throw new BusinessException("La date d'expiration doit etre posterieure a la date d'obtention");
        }
        return Permis.builder()
                .numero(n)
                .dateObtention(dateObtention)
                .dateExpiration(dateExpiration)
                .build();
    }

    /**
     * Construit (ou reutilise) un Permis pour un ConducteurSecondaire.

     * Comportement :
     *   - Si un Permis avec ce numero existe deja en base :
     *       * banni      -> BusinessException (location interdite)
     *       * sinon      -> on reutilise tel quel (les dates/photo fournies
     *                       au formulaire sont ignorees au profit de la
     *                       verite stockee). La photo eventuelle uploadee
     *                       est jetee pour eviter une fuite de fichier.
     *   - Sinon : on cree un nouveau Permis avec les infos fournies (photo
     *     obligatoire pour un second conducteur, dates aussi).

     * Justification : le numero de permis identifie un document reel unique.
     * Deux ConducteurSecondaire avec le meme numero pointent forcement vers
     * le meme document : on partage l'entite Permis.
     */
    public Permis construirePourSecondConducteur(String numero,
                                                   LocalDate dateObtention,
                                                   LocalDate dateExpiration,
                                                   MultipartFile photo) {
        if (numero == null || numero.isBlank()) {
            throw new BusinessException("Numero de permis du second conducteur requis");
        }
        String n = numero.trim();

        Optional<Permis> existing = permisRepo.findByNumero(n);
        if (existing.isPresent()) {
            Permis p = existing.get();
            if (p.isBanni()) {
                throw new BusinessException("Le permis du second conducteur est banni");
            }
            return p;
        }

        if (dateObtention == null || dateExpiration == null) {
            throw new BusinessException("Dates d'obtention et d'expiration du permis requises");
        }
        if (dateObtention.isAfter(LocalDate.now())) {
            throw new BusinessException("La date d'obtention du permis ne peut pas etre dans le futur");
        }
        if (!dateExpiration.isAfter(dateObtention)) {
            throw new BusinessException("La date d'expiration doit etre posterieure a la date d'obtention");
        }
        if (!dateExpiration.isAfter(LocalDate.now())) {
            throw new BusinessException("Le permis du second conducteur est expire");
        }
        if (photo == null || photo.isEmpty()) {
            throw new BusinessException("Photo du permis du second conducteur requise");
        }

        String photoPath = fileStorage.storeImage(photo, "permis");
        return Permis.builder()
                .numero(n)
                .dateObtention(dateObtention)
                .dateExpiration(dateExpiration)
                .photo(photoPath)
                .build();
    }

    // ============================================================
    // Bannissement (flag porte par Permis)
    // ============================================================

    /**
     * Bannit un permis (cree un Permis orphelin si numero inconnu en base).
     * Notifie le client par email si on retrouve son compte.
     */
    public Permis bannir(String numeroPermis, String motif, Admin admin) {
        if (numeroPermis == null || numeroPermis.isBlank()) {
            throw new BusinessException("Numero de permis requis");
        }
        if (motif == null || motif.isBlank()) {
            throw new BusinessException("Motif de bannissement requis");
        }
        String numero = numeroPermis.trim();

        Permis permis = permisRepo.findByNumero(numero).orElseGet(() ->
                Permis.builder().numero(numero).build());

        if (permis.isBanni()) {
            throw new BusinessException("Ce permis est deja banni");
        }

        permis.setBanni(true);
        permis.setMotifBan(motif.trim());
        permis.setDateBan(LocalDateTime.now());
        permis.setAdminBannisseur(admin);

        Permis saved = permisRepo.save(permis);
        log.info("Bannissement permis {} par admin {}", numero, admin.getEmail());

        clientRepo.findByPermisNumero(numero).ifPresent(c ->
                emailService.envoyerBannissement(c, motif));
        return saved;
    }

    /**
     * Leve le bannissement. Conserve l'entite Permis (numero, dates, photo,
     * Client eventuel) : seul le flag et les champs de ban sont effaces.
     */
    public void debannir(String numeroPermis) {
        Permis permis = permisRepo.findByNumero(numeroPermis)
                .orElseThrow(() -> new BusinessException("Permis inconnu : " + numeroPermis));
        if (!permis.isBanni()) {
            throw new BusinessException("Ce permis n'est pas banni");
        }
        permis.setBanni(false);
        permis.setMotifBan(null);
        permis.setDateBan(null);
        permis.setAdminBannisseur(null);
        log.info("Debannissement permis {}", numeroPermis);

        clientRepo.findByPermisNumero(numeroPermis).ifPresent(emailService::envoyerDebannissement);
    }

    /**
     * Test booleen rapide pour les flux de location.
     */
    @Transactional(readOnly = true)
    public boolean estPermisBanni(String numeroPermis) {
        if (numeroPermis == null || numeroPermis.isBlank()) return false;
        return permisRepo.existsByNumeroAndBanniTrue(numeroPermis);
    }

    /**
     * Recherche le detail d'un permis banni (motif, date, admin).
     * Empty si le permis existe mais n'est pas banni.
     */
    @Transactional(readOnly = true)
    public Optional<Permis> findBanniByNumero(String numeroPermis) {
        return permisRepo.findByNumero(numeroPermis).filter(Permis::isBanni);
    }

    /**
     * Liste paginee des permis bannis.
     */
    @Transactional(readOnly = true)
    public Page<Permis> findAllBannis(Pageable pageable) {
        return permisRepo.findByBanniTrue(pageable);
    }

    /**
     * Set des numeros bannis pour pre-filtrer cote UI (badges).
     */
    @Transactional(readOnly = true)
    public java.util.Set<String> findAllNumerosBannis() {
        return permisRepo.findByBanniTrue(Pageable.unpaged())
                .stream()
                .map(Permis::getNumero)
                .collect(java.util.stream.Collectors.toSet());
    }
}

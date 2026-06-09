/*
 * ============================================================================
 * ClientService - Cycle de vie et profil du Client
 * ============================================================================
 *
 * Role :
 *   Couche metier specifique au Client (sous-classe de Utilisateur) :
 *   - Lookup / liste / recherche paginee
 *   - Creation d'un Client par l'admin a l'accueil (mot de passe genere
 *     et envoye par mail, compte actif=true)
 *   - Suppression d'un Client (decouplage des locations pour preserver
 *     l'historique)
 *   - Mise a jour du profil (telephone, CIN, photo profil, photo CIN)
 *   - Verification du profil complet pour autoriser une location
 *
 *   La gestion du permis (numero, dates, photo, ban) est deleguee a
 *   PermisService.
 *
 * Appele depuis :
 *   - controller/ClientController : /client/profil
 *   - controller/AdminUtilisateurController : liste, fiche, creation,
 *     suppression
 *   - service/LocationService : verifierProfilCompletPourLocation
 *
 * Appelle :
 *   - ClientRepository : CRUD client
 *   - PermisService : gestion du permis
 *   - FileStorageService : upload photos profil et CIN
 *   - PasswordEncoder + EmailService : creation client par admin
 * ============================================================================
 */
package com.example.locvoitures.service.metier;

import com.example.locvoitures.service.auth.UtilisateurService;
import com.example.locvoitures.service.utils.EmailService;
import com.example.locvoitures.service.utils.FileStorageService;

import com.example.locvoitures.entity.Client;
import com.example.locvoitures.entity.Location;
import com.example.locvoitures.entity.Permis;
import com.example.locvoitures.entity.Utilisateur;
import com.example.locvoitures.exception.BusinessException;
import com.example.locvoitures.exception.ResourceNotFoundException;
import com.example.locvoitures.repository.ClientRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.security.SecureRandom;
import java.time.LocalDate;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class ClientService {

    private final ClientRepository clientRepo;
    private final UtilisateurService utilisateurService;
    private final PermisService permisService;
    private final FileStorageService fileStorage;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;

    // ============================================================
    // Lookup / recherche
    // ============================================================

    @Transactional(readOnly = true)
    public Client findById(Long id) {
        return clientRepo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Client", id));
    }

    @Transactional(readOnly = true)
    public java.util.List<Client> findAll() {
        return clientRepo.findAll();
    }

    /**
     * Liste paginee + recherche full-text (email/nom/prenom/permis) pour
     * l'ecran admin.
     */
    @Transactional(readOnly = true)
    public Page<Client> rechercher(String keyword, Pageable pageable) {
        if (keyword == null || keyword.isBlank()) {
            return clientRepo.findAll(pageable);
        }
        return clientRepo.rechercher(keyword.trim(), pageable);
    }

    // ============================================================
    // Mise a jour du profil par le client lui-meme
    // ============================================================

    /**
     * Mise a jour partielle du profil :
     *   - telephone, numeroCIN, photo profil, photo CIN -> gere ici
     *   - permis (numero, dates, photo) -> delegue a PermisService
     */
    public void mettreAJourProfil(Client client,
                                   String telephone,
                                   String numeroPermis,
                                   LocalDate dateObtentionPermis,
                                   LocalDate dateExpirationPermis,
                                   String numeroCIN,
                                   MultipartFile photoProfile,
                                   MultipartFile photoPermis,
                                   MultipartFile photoCIN) {

        if (telephone != null && !telephone.isBlank()) {
            client.setTelephone(telephone.trim());
        }

        if (numeroCIN != null && !numeroCIN.isBlank()) {
            clientRepo.findByNumeroCIN(numeroCIN).ifPresent(c -> {
                if (!c.getId().equals(client.getId())) {
                    throw new BusinessException("Ce numero de CIN est deja utilise");
                }
            });
            client.setNumeroCIN(numeroCIN.trim());
        }
        if (photoCIN != null && !photoCIN.isEmpty()) {
            fileStorage.delete(client.getPhotoCIN());
            client.setPhotoCIN(fileStorage.storeImage(photoCIN, "cin"));
        }

        if (photoProfile != null && !photoProfile.isEmpty()) {
            fileStorage.delete(client.getPhotoProfile());
            client.setPhotoProfile(fileStorage.storeImage(photoProfile, "profils"));
        }

        permisService.mettreAJourPourClient(client, numeroPermis,
                dateObtentionPermis, dateExpirationPermis, photoPermis);
    }

    /**
     * Verifie que le profil est complet pour autoriser la soumission d'une
     * demande de location. Sinon BusinessException avec message guidant
     * vers le formulaire de profil.
     */
    public void verifierProfilCompletPourLocation(Client client) {
        if (!client.estProfilCompletPourLocation()) {
            throw new BusinessException(
                "Vous devez completer votre profil (telephone, permis valide et CIN) avant de soumettre une demande");
        }
    }

    // ============================================================
    // Inscription publique (auto-inscription par le futur client)
    // ============================================================

    /**
     * Inscription d'un nouveau Client (formulaire public /auth/inscription).
     * actif=false a la creation, le mail d'activation est envoye juste apres.
     */
    public void inscrireClient(String email, String motDePasseClair, String nom, String prenom) {
        if (utilisateurService.emailExiste(email)) {
            throw new BusinessException("Un compte existe deja avec cet email");
        }
        Client client = Client.builder()
                .email(email)
                .motDePasse(passwordEncoder.encode(motDePasseClair))
                .nom(nom)
                .prenom(prenom)
                .actif(false)
                .tokenActivation(UUID.randomUUID().toString())
                .build();
        Client saved = clientRepo.save(client);
        log.info("Inscription : nouveau client {} (id={})", email, saved.getId());
        emailService.envoyerActivation(saved);
    }

    // ============================================================
    // Operations cote admin : creation, suppression
    // ============================================================

    /**
     * Cree un compte Client par l'admin (cas du client qui se presente au
     * bureau). Mot de passe genere aleatoirement et envoye par email.
     * Compte actif=true direct (pas de mail d'activation : identite verifiee
     * physiquement).

     * Champs profil optionnels : si le client a apporte ses papiers l'admin
     * peut deja renseigner telephone / permis / CIN. Sinon le client complete
     * lui-meme via /client/profil.
     */
    public Client creerParAdmin(String email, String nom, String prenom,
                                 String telephone,
                                 String numeroPermis,
                                 LocalDate dateObtentionPermis,
                                 LocalDate dateExpirationPermis,
                                 String numeroCIN) {
        if (email == null || email.isBlank()) {
            throw new BusinessException("L'email est obligatoire");
        }
        String emailNormalise = email.trim().toLowerCase();
        if (utilisateurService.emailExiste(emailNormalise)) {
            throw new BusinessException("Un compte existe deja avec cet email");
        }

        Permis permis = permisService.construirePourNouveauClient(
                numeroPermis, dateObtentionPermis, dateExpirationPermis);

        String motDePasseClair = genererMotDePasseAleatoire();

        Client client = Client.builder()
                .email(emailNormalise)
                .motDePasse(passwordEncoder.encode(motDePasseClair))
                .nom(nom.trim())
                .prenom(prenom.trim())
                .actif(true)
                .telephone(blankToNull(telephone))
                .permis(permis)
                .numeroCIN(blankToNull(numeroCIN))
                .build();

        Client saved = clientRepo.save(client);
        log.info("Client cree par admin : {} (id={})", emailNormalise, saved.getId());

        emailService.envoyerIdentifiantsCompteCree(saved, motDePasseClair);
        return saved;
    }

    /**
     * Suppression d'un Client par l'admin. Decouplage : Location.client passe
     * a null pour preserver l'historique.
     */
    public void supprimer(Long id) {
        Client client = findById(id);
        if (client.getLocations() != null) {
            int n = client.getLocations().size();
            for (Location l : client.getLocations()) {
                l.setClient(null);
            }
            client.getLocations().clear();
            if (n > 0) {
                log.info("Compte {} : {} location(s) detachee(s) avant suppression",
                        client.getEmail(), n);
            }
        }
        clientRepo.delete(client);
        log.info("Compte client {} supprime par admin", client.getEmail());
    }

    /**
     * Variante "tolerante" pour le controller : refuse si l'id pointe vers
     * un Admin (Utilisateur polymorphe). Lookup polymorphe delegue a
     * UtilisateurService.
     */
    public void supprimerParAdmin(Long utilisateurId) {
        Utilisateur user = utilisateurService.findById(utilisateurId);
        if (!(user instanceof Client)) {
            throw new BusinessException("Impossible de supprimer un compte administrateur");
        }
        supprimer(user.getId());
    }

    // ============================================================
    // Helpers prives
    // ============================================================

    private static final int LONGUEUR_MOT_DE_PASSE_GENERE = 10;
    private static final String CHARS_MOT_DE_PASSE =
            "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz23456789";

    private String genererMotDePasseAleatoire() {
        // Caracteres alphanumeriques sans ambiguite (pas de O/0/l/1)
        SecureRandom random = new SecureRandom();
        StringBuilder sb = new StringBuilder(LONGUEUR_MOT_DE_PASSE_GENERE);
        for (int i = 0; i < LONGUEUR_MOT_DE_PASSE_GENERE; i++) {
            sb.append(CHARS_MOT_DE_PASSE.charAt(random.nextInt(CHARS_MOT_DE_PASSE.length())));
        }
        return sb.toString();
    }

    private String blankToNull(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }
}

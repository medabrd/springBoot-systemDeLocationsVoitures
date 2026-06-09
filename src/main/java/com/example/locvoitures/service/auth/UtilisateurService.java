/*
 * ============================================================================
 * UtilisateurService - Operations communes a tout compte (auth + identite)
 * ============================================================================
 *
 * Role :
 *   Couche metier pour les operations qui s'appliquent indifferemment aux
 *   Clients et aux Admins :
 *   - Verification d'unicite d'email (cross-classes)
 *   - Lookup polymorphe par id
 *   - Activation de compte via token
 *   - Reinitialisation de mot de passe (demande + execution)
 *   - Mise a jour des champs Utilisateur (nom, prenom, email)
 *   - Changement de mot de passe par l'utilisateur lui-meme
 *
 *   Les operations specifiques au Client (inscription, creation par admin,
 *   suppression, profil) sont dans ClientService.
 *   Les operations specifiques a l'Admin (creation, listing) sont dans
 *   AdminService.
 *   Les operations permis / bannissement sont dans PermisService.
 *
 * Appele depuis :
 *   - controller/AuthController : reset, activation
 *   - controller/ClientController : modification compte / mot de passe
 *   - service/metier/ClientService : verification email (inscription)
 *   - service/metier/AdminService : verification email (creation)
 *
 * Securite :
 *   - Mot de passe stocke en hash BCrypt, jamais en clair
 *   - Token UUID avec expiration courte (1h pour reset)
 *   - Anti-enumeration : demanderReinitialisation est silencieuse
 * ============================================================================
 */
package com.example.locvoitures.service.auth;

import com.example.locvoitures.service.utils.EmailService;

import com.example.locvoitures.entity.Utilisateur;
import com.example.locvoitures.exception.BusinessException;
import com.example.locvoitures.exception.ResourceNotFoundException;
import com.example.locvoitures.repository.UtilisateurRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class UtilisateurService {

    private final UtilisateurRepository utilisateurRepo;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;

    /**
     * Lookup polymorphe par id (404 si introuvable). Retourne Client ou Admin
     * selon la sous-classe materialisee par Hibernate.
     */
    @Transactional(readOnly = true)
    public Utilisateur findById(Long id) {
        return utilisateurRepo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Utilisateur", id));
    }

    /**
     * Existe-t-il un compte (Client OU Admin) avec cet email ?
     * Expose la verification d'unicite cote autres services sans qu'ils
     * aient a injecter directement UtilisateurRepository.
     */
    @Transactional(readOnly = true)
    public boolean emailExiste(String email) {
        if (email == null || email.isBlank()) return false;
        return utilisateurRepo.existsByEmail(email.trim().toLowerCase());
    }

    /**
     * Recherche polymorphe par email. Utilise par les helpers d'auth pour
     * resoudre l'utilisateur courant depuis le SecurityContext.
     */
    @Transactional(readOnly = true)
    public Optional<Utilisateur> findByEmail(String email) {
        if (email == null || email.isBlank()) return Optional.empty();
        return utilisateurRepo.findByEmail(email);
    }

    /**
     * Demande de reinitialisation. Silencieuse si l'email n'existe pas
     * (anti-enumeration).
     */
    public void demanderReinitialisation(String email) {
        if (email == null || email.isBlank()) return;
        utilisateurRepo.findByEmail(email.trim().toLowerCase())
                .filter(Utilisateur::isActif)
                .ifPresent(user -> {
                    user.setTokenReinitialisation(UUID.randomUUID().toString());
                    user.setDateExpirationToken(LocalDateTime.now().plusHours(1));
                    emailService.envoyerLienReinitialisation(user);
                    log.info("Demande de reinitialisation envoyee a {}", user.getEmail());
                });
    }

    /**
     * Reinitialisation effective du mot de passe via token.
     */
    public void reinitialiserMotDePasse(String token, String nouveauMdp, String confirmation) {
        if (token == null || token.isBlank()) {
            throw new BusinessException("Lien de reinitialisation invalide");
        }
        Utilisateur user = utilisateurRepo.findByTokenReinitialisation(token)
                .orElseThrow(() -> new BusinessException("Lien de reinitialisation invalide ou deja utilise"));
        if (user.getDateExpirationToken() == null
                || user.getDateExpirationToken().isBefore(LocalDateTime.now())) {
            user.setTokenReinitialisation(null);
            user.setDateExpirationToken(null);
            throw new BusinessException("Lien de reinitialisation expire. Veuillez refaire une demande.");
        }
        if (nouveauMdp == null || nouveauMdp.length() < 6) {
            throw new BusinessException("Le mot de passe doit contenir au moins 6 caracteres");
        }
        if (!nouveauMdp.equals(confirmation)) {
            throw new BusinessException("La confirmation ne correspond pas");
        }
        user.setMotDePasse(passwordEncoder.encode(nouveauMdp));
        user.setTokenReinitialisation(null);
        user.setDateExpirationToken(null);
        log.info("Mot de passe reinitialise pour {}", user.getEmail());
    }

    /**
     * Active le compte via le token recu par mail.
     */
    public void activerCompte(String token) {
        Utilisateur user = utilisateurRepo.findByTokenActivation(token)
                .orElseThrow(() -> new BusinessException("Lien d'activation invalide ou expire"));
        if (user.isActif()) {
            throw new BusinessException("Ce compte est deja active");
        }
        user.setActif(true);
        user.setTokenActivation(null);
        log.info("Compte active : {}", user.getEmail());
    }

    /**
     * Mise a jour des champs Utilisateur (nom, prenom, email). Commun a
     * Client et Admin.
     */
    public void mettreAJourCompte(Utilisateur user, String nom, String prenom, String email) {
        if (nom == null || nom.isBlank()) {
            throw new BusinessException("Le nom est obligatoire");
        }
        if (prenom == null || prenom.isBlank()) {
            throw new BusinessException("Le prenom est obligatoire");
        }
        if (email == null || email.isBlank()) {
            throw new BusinessException("L'email est obligatoire");
        }
        String emailNormalise = email.trim().toLowerCase();
        if (!emailNormalise.equalsIgnoreCase(user.getEmail())) {
            if (utilisateurRepo.existsByEmail(emailNormalise)) {
                throw new BusinessException("Cet email est deja utilise");
            }
            user.setEmail(emailNormalise);
        }
        user.setNom(nom.trim());
        user.setPrenom(prenom.trim());
        log.info("Compte {} mis a jour", user.getEmail());
    }

    /**
     * Changement de mot de passe par l'utilisateur lui-meme (ancien mdp
     * requis pour defense-en-profondeur).
     */
    public void changerMotDePasse(Utilisateur user, String ancienMdp, String nouveauMdp, String confirmation) {
        if (ancienMdp == null || !passwordEncoder.matches(ancienMdp, user.getMotDePasse())) {
            throw new BusinessException("Mot de passe actuel incorrect");
        }
        if (nouveauMdp == null || nouveauMdp.length() < 6) {
            throw new BusinessException("Le nouveau mot de passe doit contenir au moins 6 caracteres");
        }
        if (!nouveauMdp.equals(confirmation)) {
            throw new BusinessException("La confirmation ne correspond pas");
        }
        user.setMotDePasse(passwordEncoder.encode(nouveauMdp));
        log.info("Mot de passe change pour {}", user.getEmail());
    }
}

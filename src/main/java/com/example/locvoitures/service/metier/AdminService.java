/*
 * ============================================================================
 * AdminService - Cycle de vie de l'Admin
 * ============================================================================
 *
 * Role :
 *   Couche metier specifique a l'Admin (sous-classe de Utilisateur) :
 *   - Liste des admins (utilise par ReclamationService pour notification)
 *   - Creation d'un Admin (utilise par DataInitializer au boot)
 *
 *   Les operations communes Client/Admin (auth, reset, mise a jour profil
 *   commun) sont dans UtilisateurService.
 *
 * Appele depuis :
 *   - config/DataInitializer : creerAdmin au boot
 *   - service/ReclamationService : findAll pour notifier tous les admins
 *
 * Appelle :
 *   - AdminRepository : CRUD admin
 *   - UtilisateurService : verification d'unicite email (cross-package)
 *   - PasswordEncoder : hash BCrypt du mot de passe
 * ============================================================================
 */
package com.example.locvoitures.service.metier;

import com.example.locvoitures.service.auth.UtilisateurService;

import com.example.locvoitures.entity.Admin;
import com.example.locvoitures.enumeration.PosteAdmin;
import com.example.locvoitures.exception.BusinessException;
import com.example.locvoitures.repository.AdminRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class AdminService {

    private final AdminRepository adminRepo;
    private final UtilisateurService utilisateurService;
    private final PasswordEncoder passwordEncoder;

    /**
     * Liste tous les Admins. Utilise par ReclamationService pour la
     * notification massive a la soumission d'une reclamation.
     */
    @Transactional(readOnly = true)
    public List<Admin> findAll() {
        return adminRepo.findAll();
    }

    /**
     * Cree un compte Admin (DataInitializer au boot). actif=true direct,
     * pas de mail d'activation.
     */
    public void creerAdmin(String email, String motDePasseClair, String nom, String prenom,
                            PosteAdmin posteOccupe) {
        if (utilisateurService.emailExiste(email)) {
            throw new BusinessException("Un compte existe deja avec cet email");
        }
        Admin admin = Admin.builder()
                .email(email)
                .motDePasse(passwordEncoder.encode(motDePasseClair))
                .nom(nom)
                .prenom(prenom)
                .actif(true)
                .posteOccupe(posteOccupe)
                .dateEmbauche(LocalDate.now())
                .build();
        adminRepo.save(admin);
        log.info("Admin cree : {}", email);
    }
}

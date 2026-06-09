/*
 * ============================================================================
 * ClientController - Pages "Mon compte / profil" cote client
 * ============================================================================
 *
 * Role :
 *   Gere l'espace personnel du client :
 *   - /client/accueil : page d'arrivee apres login
 *   - /client/profil : consultation du profil
 *   - /client/profil/modifier : formulaire d'edition
 *   - POST /client/profil : mise a jour profil + compte
 *   - POST /client/profil/mot-de-passe : changement mdp
 *
 * Mapping :
 *   /client/* (reserve hasRole("CLIENT") dans SecurityConfig)
 *
 * Appelle :
 *   - AuthUtil : recupere user/client courant depuis SecurityContext
 *   - ClientService : mise a jour profil (telephone, permis, CIN, photos)
 *   - UtilisateurService : mise a jour compte (nom, prenom, email, mdp)
 *
 * Pattern :
 *   PRG (Post/Redirect/Get) avec flash messages succes/erreur.
 *   Pas de DTO ici car les champs sont simples et la validation cote service.
 * ============================================================================
 */
package com.example.locvoitures.controller.client;

import com.example.locvoitures.controller.advice.AuthUtil;

import com.example.locvoitures.entity.Client;
import com.example.locvoitures.entity.Utilisateur;
import com.example.locvoitures.exception.BusinessException;
import com.example.locvoitures.service.metier.ClientService;
import com.example.locvoitures.service.auth.UtilisateurService;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
// MultipartFile pour les uploads de photos
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/client")
@RequiredArgsConstructor
public class ClientController {

    private final ClientService clientService;
    private final UtilisateurService utilisateurService;
    private final AuthUtil authUtil;

    /**
     * Accueil client : redirige vers le catalogue ou affiche un raccourci
     * profil si incomplet. Indique au template si le profil est complet
     * pour afficher un CTA "Completer mon profil".
     */
    @GetMapping("/accueil")
    public String accueil(Model model) {
        Client client = authUtil.getCurrentClient();
        model.addAttribute("profilComplet", client.estProfilCompletPourLocation());
        return "client/accueil";
    }

    /**
     * Affichage du profil en lecture seule.
     */
    @GetMapping("/profil")
    public String profil(Model model) {
        Client client = authUtil.getCurrentClient();
        model.addAttribute("client", client);
        model.addAttribute("profilComplet", client.estProfilCompletPourLocation());
        return "client/profil";
    }

    /**
     * Affichage du formulaire d'edition.
     */
    @GetMapping("/profil/modifier")
    public String modifierProfil(Model model) {
        Client client = authUtil.getCurrentClient();
        model.addAttribute("client", client);
        model.addAttribute("profilComplet", client.estProfilCompletPourLocation());
        return "client/profil-edition";
    }

    /**
     * Traite la soumission du formulaire de profil.
     * Champs et fichiers tous optionnels (mise a jour partielle).
     * Si BusinessException : flash message d'erreur + redirect (pas de
     * binding result ici car pas de DTO).
     */
    @PostMapping("/profil")
    public String mettreAJourProfil(
            @RequestParam(required = false) String nom,
            @RequestParam(required = false) String prenom,
            @RequestParam(required = false) String email,
            @RequestParam(required = false) String telephone,
            @RequestParam(required = false) String numeroPermis,
            @RequestParam(value = "dateObtentionPermis", required = false)
                @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE)
                java.time.LocalDate dateObtentionPermis,
            @RequestParam(value = "dateExpirationPermis", required = false)
                @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE)
                java.time.LocalDate dateExpirationPermis,
            @RequestParam(required = false) String numeroCIN,
            @RequestParam(value = "photoProfile", required = false) MultipartFile photoProfile,
            @RequestParam(value = "photoPermis", required = false) MultipartFile photoPermis,
            @RequestParam(value = "photoCIN", required = false) MultipartFile photoCIN,
            RedirectAttributes redirectAttributes) {

        // Depuis le refactor inheritance : Client extends Utilisateur, donc
        // un seul appel a getCurrentClient() suffit (le user "est" le client).
        Client client = authUtil.getCurrentClient();
        try {
            // 1) Champs Utilisateur (nom/prenom/email)
            utilisateurService.mettreAJourCompte(client, nom, prenom, email);
            // 2) Champs Client (telephone/permis/CIN + uploads optionnels)
            clientService.mettreAJourProfil(client, telephone,
                    numeroPermis, dateObtentionPermis, dateExpirationPermis,
                    numeroCIN, photoProfile, photoPermis, photoCIN);
            redirectAttributes.addFlashAttribute("successMessage", "Profil mis a jour avec succes.");
        } catch (BusinessException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/client/profil";
    }

    /**
     * Changement de mot de passe par l'utilisateur lui-meme.
     * Demande l'ancien mdp pour defense-en-profondeur.
     */
    @PostMapping("/profil/mot-de-passe")
    public String changerMotDePasse(
            @RequestParam String ancienMotDePasse,
            @RequestParam String nouveauMotDePasse,
            @RequestParam String confirmation,
            RedirectAttributes redirectAttributes) {

        Utilisateur user = authUtil.getCurrentUser();
        try {
            utilisateurService.changerMotDePasse(user, ancienMotDePasse, nouveauMotDePasse, confirmation);
            redirectAttributes.addFlashAttribute("successMessage", "Mot de passe modifie avec succes.");
        } catch (BusinessException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/client/profil";
    }
}

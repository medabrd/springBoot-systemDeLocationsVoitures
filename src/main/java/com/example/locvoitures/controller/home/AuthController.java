/*
 * ============================================================================
 * AuthController - Endpoints d'authentification (inscription, reset, activation)
 * ============================================================================
 *
 * Role :
 *   Gere les flux d'authentification publics : page de login, inscription,
 *   activation par lien email, mot de passe oublie, reinitialisation.
 *   Le POST de login lui-meme est gere par Spring Security (filter chain),
 *   pas par ce controller.
 *
 * Mapping :
 *   /auth/* (public via SecurityConfig.permitAll())
 *
 * Appelle :
 *   - UtilisateurService : inscrireClient, activerCompte,
 *     demanderReinitialisation, reinitialiserMotDePasse
 *
 * Pattern PRG (Post/Redirect/Get) :
 *   Apres un POST reussi, on REDIRECT vers une page GET + flash attribute.
 *   Empeche le re-submit accidentel sur F5 et garde l'URL propre.
 *
 * Anti-enumeration :
 *   Sur "mot de passe oublie", on affiche le meme message "si l'email
 *   existe..." quel que soit le resultat reel. Un attaquant ne peut pas
 *   distinguer un email enregistre d'un email inconnu.
 *
 * Validation :
 *   @Valid declenche Bean Validation sur le DTO. Erreurs collectees dans
 *   BindingResult. Si erreurs : on renvoie le template avec les erreurs
 *   affichees (NE PAS rediriger : on perdrait BindingResult).
 * ============================================================================
 */
package com.example.locvoitures.controller.home;

import com.example.locvoitures.dto.form.InscriptionForm;
import com.example.locvoitures.dto.form.MotDePasseOublieForm;
import com.example.locvoitures.dto.form.ReinitialisationForm;
import com.example.locvoitures.exception.BusinessException;
import com.example.locvoitures.service.auth.UtilisateurService;
import com.example.locvoitures.service.metier.ClientService;

// @Valid declenche la validation Bean sur un parametre annote
import jakarta.validation.Valid;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
// BindingResult : conteneur des erreurs de validation (suit immediatement
// le parametre @Valid dans la signature)
import org.springframework.validation.BindingResult;
// Wildcard : @GetMapping, @PostMapping, @RequestMapping, @RequestParam,
// @ModelAttribute, @PathVariable
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Controller : marque comme controller MVC (vs @RestController qui renvoie JSON).
 * RequestMapping("/auth") : prefixe commun a toutes les routes du controller.
 */
@Controller
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UtilisateurService utilisateurService;
    private final ClientService clientService;

    // ============================================================
    // Login (page seulement, le POST est gere par Spring Security)
    // ============================================================

    /**
     * Affiche la page de login. error=true et logout=true sont positionnes
     * par les redirections de Spring Security pour afficher des messages.
     */
    @GetMapping("/login")
    public String login(@RequestParam(required = false) String error,
                         @RequestParam(required = false) String logout,
                         Model model) {
        if (error != null) {
            model.addAttribute("errorMessage",
                    "Email ou mot de passe incorrect, ou compte non active");
        }
        if (logout != null) {
            model.addAttribute("successMessage", "Vous avez ete deconnecte");
        }
        return "auth/login";
    }

    // ============================================================
    // Inscription
    // ============================================================

    /**
     * Affiche le formulaire d'inscription. Pre-rempli avec un DTO vide
     * (sauf si re-submit avec erreurs : Spring re-injecte le DTO via flash).
     */
    @GetMapping("/inscription")
    public String inscriptionForm(Model model) {
        if (!model.containsAttribute("inscriptionForm")) {
            model.addAttribute("inscriptionForm", new InscriptionForm());
        }
        return "auth/inscription";
    }

    /**
     * Traite la soumission du formulaire d'inscription.
     * BindingResult DOIT suivre immediatement le parametre @Valid sinon
     * Spring leve une MethodArgumentNotValidException (= 400) au lieu de
     * peupler bindingResult.
     */
    @PostMapping("/inscription")
    public String inscrire(@Valid @ModelAttribute("inscriptionForm") InscriptionForm form,
                            BindingResult bindingResult,
                            RedirectAttributes redirectAttributes) {
        // Validation cross-field : mots de passe identiques
        if (form.getMotDePasse() != null && !form.motsDePasseConcordent()) {
            bindingResult.rejectValue("confirmationMotDePasse", "passwords.mismatch",
                    "Les mots de passe ne correspondent pas");
        }

        if (bindingResult.hasErrors()) {
            // Re-affiche le formulaire avec les erreurs (pas de redirect)
            return "auth/inscription";
        }

        try {
            // Normalise email : trim + lowercase pour eviter doublons casse
            clientService.inscrireClient(
                    form.getEmail().trim().toLowerCase(),
                    form.getMotDePasse(),
                    form.getNom().trim(),
                    form.getPrenom().trim());
        } catch (BusinessException e) {
            // Email deja utilise -> erreur ciblee sur le field email
            bindingResult.rejectValue("email", "email.exists", e.getMessage());
            return "auth/inscription";
        }

        // PRG : succes -> redirect avec flash message
        redirectAttributes.addFlashAttribute("successMessage",
                "Inscription reussie. Un email d'activation a ete envoye a " + form.getEmail()
                        + ". Cliquez sur le lien dans le mail pour activer votre compte.");
        return "redirect:/auth/login";
    }

    // ============================================================
    // Activation par lien email
    // ============================================================

    /**
     * Lien clique depuis le mail : /auth/activer?token=xxx.
     */
    @GetMapping("/activer")
    public String activer(@RequestParam String token, RedirectAttributes redirectAttributes) {
        try {
            utilisateurService.activerCompte(token);
            redirectAttributes.addFlashAttribute("successMessage",
                    "Votre compte a ete active avec succes. Vous pouvez maintenant vous connecter.");
        } catch (BusinessException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/auth/login";
    }

    // ============================================================
    // Mot de passe oublie
    // ============================================================

    @GetMapping("/mot-de-passe-oublie")
    public String motDePasseOublieForm(Model model) {
        if (!model.containsAttribute("motDePasseOublieForm")) {
            model.addAttribute("motDePasseOublieForm", new MotDePasseOublieForm());
        }
        return "auth/mot-de-passe-oublie";
    }

    /**
     * Demande de reset : silencieux cote service (anti-enumeration), mais
     * affiche toujours le meme message succes cote UI.
     */
    @PostMapping("/mot-de-passe-oublie")
    public String demanderReinitialisation(@Valid @ModelAttribute("motDePasseOublieForm") MotDePasseOublieForm form,
                                            BindingResult bindingResult,
                                            RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            return "auth/mot-de-passe-oublie";
        }
        utilisateurService.demanderReinitialisation(form.getEmail());
        //  RedirectAttributes.addFlashAttribute(...) stocke l'attribut dans la session HTTP sous un nom spécial, puis Spring le
        //  récupère + supprime automatiquement à la requête suivante. C'est la mécanique "flash message" : visible une seule
        //  fois.
        redirectAttributes.addFlashAttribute("successMessage",
                "Si un compte existe pour " + form.getEmail()
                        + ", un email contenant un lien de reinitialisation vient d'etre envoye. "
                        + "Le lien est valide 1 heure.");
        return "redirect:/auth/login";
    }

    // ============================================================
    // Reinitialisation effective (apres clic sur le lien recu par mail)
    // ============================================================

    /**
     * Affiche le formulaire avec le token en hidden field.
     */
    @GetMapping("/reinitialiser-mot-de-passe")
    public String reinitialiserForm(@RequestParam String token, Model model) {
        ReinitialisationForm form = new ReinitialisationForm();
        form.setToken(token);
        if (!model.containsAttribute("reinitialisationForm")) {
            model.addAttribute("reinitialisationForm", form);
        }
        return "auth/reinitialiser-mot-de-passe";
    }

    /**
     * Applique le nouveau mot de passe. Erreurs metier (token expire,
     * confirmation non match) affichees sur le formulaire.
     */
    @PostMapping("/reinitialiser-mot-de-passe")
    public String reinitialiser(@Valid @ModelAttribute("reinitialisationForm") ReinitialisationForm form,
                                 BindingResult bindingResult,
                                 RedirectAttributes redirectAttributes,
                                 Model model) {
        if (bindingResult.hasErrors()) {
            return "auth/reinitialiser-mot-de-passe";
        }
        try {
            utilisateurService.reinitialiserMotDePasse(form.getToken(),
                    form.getMotDePasse(), form.getConfirmationMotDePasse());
            redirectAttributes.addFlashAttribute("successMessage",
                    "Mot de passe reinitialise avec succes. Vous pouvez vous connecter.");
            return "redirect:/auth/login";
        } catch (BusinessException e) {
            // On RESTE sur la page de reset pour permettre une nouvelle tentative
            // (sauf si token totalement invalide, mais l'utilisateur peut juste
            // redemander un lien).
            model.addAttribute("errorMessage", e.getMessage());
            return "auth/reinitialiser-mot-de-passe";
        }
    }
}

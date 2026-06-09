/*
 * ============================================================================
 * GlobalExceptionHandler - Mappe les exceptions non gerees en reponses HTTP
 * ============================================================================
 *
 * Role :
 *   Filet de securite global : toute exception non capturee par un
 *   controller est interceptee ici et transformee en :
 *   - BusinessException : flash message + redirect vers Referer (UX souple)
 *   - ResourceNotFoundException : page 404 avec message
 *   - AccessDeniedException : page 403
 *   - Exception (generique) : page 500 avec detail technique
 *
 * Mecanisme Spring :
 *   @ControllerAdvice + @ExceptionHandler : Spring detecte ces methodes
 *   et les appelle quand une exception du type indique remonte d'un
 *   controller. Le @ResponseStatus pose le code HTTP correspondant.
 *
 * Pattern UX BusinessException :
 *   On ne renvoie pas une page d'erreur mais on revient en arriere avec
 *   un message flash (toast). L'utilisateur reste dans son contexte au
 *   lieu de devoir re-naviguer.
 *
 * Logging :
 *   - BusinessException : warn (deja prevu metier, pas une vraie erreur)
 *   - ResourceNotFoundException : warn (souvent une saisie incorrecte)
 *   - Exception : error avec stack trace complete (probleme technique)
 * ============================================================================
 */
package com.example.locvoitures.controller.advice;

import com.example.locvoitures.exception.BusinessException;
import com.example.locvoitures.exception.ResourceNotFoundException;

import jakarta.servlet.http.HttpServletRequest;

import lombok.extern.slf4j.Slf4j;

import org.springframework.http.HttpStatus;
// AccessDeniedException : levee par Spring Security si role manquant
import org.springframework.security.access.AccessDeniedException;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
// @ExceptionHandler : marque une methode comme handler d'un type d'exception
import org.springframework.web.bind.annotation.ExceptionHandler;
// @ResponseStatus : pose le code HTTP de la reponse
import org.springframework.web.bind.annotation.ResponseStatus;
// RedirectAttributes : conteneur pour les attributs "flash" (survivent au redirect)
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@ControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    /**
     * BusinessException : violation de regle metier.
     * Strategie UX : flash message d'erreur + redirect vers Referer.
     * L'utilisateur reste dans son flux courant avec le message visible.
     */
    @ExceptionHandler(BusinessException.class)
    public String handleBusiness(BusinessException e,
                                  HttpServletRequest request,
                                  RedirectAttributes redirectAttributes) {
        log.warn("BusinessException : {}", e.getMessage());
        // Flash attribute : survit au redirect (stocke en session puis efface)
        redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        // Referer : la page d'ou vient la requete. Fallback "/" si absent.
        String referer = request.getHeader("Referer");
        return "redirect:" + (referer != null ? referer : "/");
    }

    /**
     * Ressource introuvable -> 404 + page erreur/404.html.
     */
    @ExceptionHandler(ResourceNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public String handleNotFound(ResourceNotFoundException e, Model model) {
        log.warn("ResourceNotFound : {}", e.getMessage());
        model.addAttribute("message", e.getMessage());
        return "erreur/404";
    }

    /**
     * AccessDenied (role manquant) -> 403.
     * Cas redondant avec SecurityConfig.accessDeniedPage mais defense
     * pour les exceptions levees a la main par le code.
     */
    @ExceptionHandler(AccessDeniedException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public String handleAccessDenied(AccessDeniedException e, Model model) {
        log.warn("AccessDenied : {}", e.getMessage());
        model.addAttribute("message", "Acces refuse");
        return "erreur/403";
    }

    /**
     * Catch-all generique. Stack trace dans les logs, message+detail dans
     * la vue pour aider le diagnostic en developpement.
     */
    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public String handleGeneric(Exception e, Model model) {
        // .error(..., e) : log avec la stack trace complete
        log.error("Erreur non geree", e);
        model.addAttribute("message", "Une erreur inattendue s'est produite");
        model.addAttribute("detail", e.getMessage());
        return "erreur/500";
    }
}

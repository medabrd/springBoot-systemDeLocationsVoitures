/*
 * ============================================================================
 * BusinessException - Exception metier (regle violee)
 * ============================================================================
 *
 * Role :
 *   Marqueur pour distinguer les violations de regles metier des erreurs
 *   techniques (DB down, IO, ...). Levee depuis les services quand une
 *   garde metier n'est pas satisfaite : chevauchement de dates, profil
 *   incomplet, client banni, statut incompatible, etc.
 *
 * Utilise par :
 *   - service/* : levee depuis les gardes metier
 *   - controller/GlobalExceptionHandler : intercepte et mappe vers un
 *     message d'erreur "flash" affiche dans la vue
 *   - controllers individuels : certains catch explicite pour rendre
 *     l'erreur a cote du formulaire (avec BindingResult)
 *
 * Heritage :
 *   extends RuntimeException : declenche le rollback automatique des
 *   transactions @Transactional (par defaut Spring ne rollback que sur
 *   RuntimeException et Error, pas sur Exception checked).
 *
 * Convention :
 *   Le message passe au constructeur est destine a l'utilisateur final
 *   (lisible, en francais, sans details techniques).
 * ============================================================================
 */
package com.example.locvoitures.exception;

import java.io.Serial;

public class BusinessException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    public BusinessException(String message) {
        super(message);
    }

    /**
     * Variante avec cause : preserve la stack trace de l'exception
     * sous-jacente (IOException, etc.) pour le diagnostic.
     */
    public BusinessException(String message, Throwable cause) {
        super(message, cause);
    }
}

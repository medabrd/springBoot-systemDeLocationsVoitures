/*
 * ============================================================================
 * ResourceNotFoundException - Exception "ressource introuvable" (404)
 * ============================================================================
 *
 * Role :
 *   Levee quand un findById retourne Optional.empty (entite introuvable).
 *   Permet au GlobalExceptionHandler de renvoyer une page d'erreur 404
 *   avec un message lisible.
 *
 * Utilise par :
 *   - service/* : findById(...).orElseThrow(() -> new ResourceNotFoundException(...))
 *   - controller/GlobalExceptionHandler : mappe vers status 404 + page erreur
 *
 * Heritage :
 *   extends RuntimeException (non-checked) -> rollback transactionnel et
 *   pas besoin de declarer dans les signatures.
 * ============================================================================
 */
package com.example.locvoitures.exception;

import java.io.Serial;

public class ResourceNotFoundException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * Helper pour generer un message standard "<resource> introuvable (id=<id>)".
     * Exemple : new ResourceNotFoundException("Voiture", 42)
     *          -> "Voiture introuvable (id=42)"
     */
    public ResourceNotFoundException(String resource, Object id) {
        super(resource + " introuvable (id=" + id + ")");
    }
}

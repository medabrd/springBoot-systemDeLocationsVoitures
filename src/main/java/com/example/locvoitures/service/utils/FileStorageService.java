/*
 * ============================================================================
 * FileStorageService - Gestion du stockage local des fichiers uploades
 * ============================================================================
 *
 * Role :
 *   Centralise l'upload, le stockage et la suppression des fichiers images
 *   du projet (photos de voitures, profils, permis, CIN, reclamations,
 *   conducteurs secondaires, logos de marques). Tout est stocke sur le
 *   disque local sous app.upload.dir, organise en sous-dossiers.
 *
 * Appele depuis :
 *   - service/VoitureService : photo de voiture
 *   - service/MarqueService : logo de marque
 *   - service/ClientService : photoProfile, photoPermis, photoCIN
 *   - service/UtilisateurService : photo de profil a l'inscription
 *   - service/LocationService : photoPermis et photoCIN du conducteur
 *     secondaire
 *   - service/ReclamationService : photo justificative
 *   - controller/TestVoituresInitializer : storeBytes() pour des images
 *     telechargees au boot
 *   - config/WebConfig : mappe /uploads/** vers le repertoire racine
 *
 * Configuration externe :
 *   - app.upload.dir : repertoire racine (ex: ./uploads)
 *
 * Securite :
 *   - Verification de l'extension (jpg/jpeg/png/webp seulement)
 *   - Verification de la taille (max 5 Mo)
 *   - Verification du chemin normalise reste sous racine
 *     (defense path traversal "../etc/passwd")
 *   - Nom de fichier UUID : evite les collisions et l'enumeration
 *
 * Initialisation :
 *   @PostConstruct cree le repertoire racine et tous les sous-dossiers
 *   au demarrage de l'application.
 * ============================================================================
 */
package com.example.locvoitures.service.utils;

import com.example.locvoitures.exception.BusinessException;

// @PostConstruct : callback Jakarta EE invoque apres injection du bean
import jakarta.annotation.PostConstruct;

// Lombok logger
import lombok.extern.slf4j.Slf4j;

// @Value : injection depuis application.properties
import org.springframework.beans.factory.annotation.Value;

import org.springframework.stereotype.Service;
// MultipartFile : abstraction Spring pour les fichiers uploades
import org.springframework.web.multipart.MultipartFile;

// API NIO.2 : Files / Path pour manipulation de fichiers moderne
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Set;
// UUID pour generer des noms de fichiers uniques et non enumerable
import java.util.UUID;

@Service
@Slf4j
public class FileStorageService {

    /**
     * Extensions autorisees pour les images. Set pour lookup O(1).
     */
    private static final Set<String> ALLOWED_IMAGE_EXT = Set.of("jpg", "jpeg", "png", "webp");

    /**
     * Taille maximale d'un upload (5 Mo).
     * L = literal long pour eviter overflow int * int.
     */
    private static final long MAX_SIZE = 5L * 1024 * 1024;

    /**
     * Repertoire racine configure dans application.properties.
     */
    @Value("${app.upload.dir}")
    private String uploadDir;

    /**
     * Chemin absolu calcule au @PostConstruct.
     */
    private Path racine;

    /**
     * PostConstruct : appele par le conteneur Spring apres l'injection.
     * Cree le repertoire racine et tous les sous-dossiers necessaires.
     */
    @PostConstruct
    public void init() {
        try {
            // toAbsolutePath() : transforme un chemin relatif en absolu
            // normalize() : resoud les "../", "./" pour eviter les surprises
            this.racine = Paths.get(uploadDir).toAbsolutePath().normalize();
            Files.createDirectories(racine);
            // Cree chaque sous-dossier metier au boot
            for (String sub : new String[]{"voitures", "profils", "permis", "cin", "reclamations", "conducteurs", "marques"}) {
                Files.createDirectories(racine.resolve(sub));
            }
            log.info("Repertoire d'upload initialise : {}", racine);
        } catch (IOException e) {
            // Echec critique au boot : on rate-fail explicitement
            throw new IllegalStateException("Impossible de creer le repertoire d'upload : " + uploadDir, e);
        }
    }

    /**
     * Sauvegarde une image en respectant les contraintes de type et taille.
     * Retourne un chemin relatif a stocker dans l'entite (ex: "voitures/abc-123.jpg").
     */
    public String storeImage(MultipartFile file, String sousDossier) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("Fichier vide");
        }
        if (file.getSize() > MAX_SIZE) {
            throw new BusinessException("Fichier trop volumineux (max 5 Mo)");
        }
        String ext = extraireExtension(file.getOriginalFilename());
        if (!ALLOWED_IMAGE_EXT.contains(ext.toLowerCase())) {
            throw new BusinessException("Format non autorise (jpg, jpeg, png, webp uniquement)");
        }

        // UUID + extension : nom non-devinable, evite l'enumeration et les conflits
        String nomFichier = UUID.randomUUID() + "." + ext.toLowerCase();
        Path cible = racine.resolve(sousDossier).resolve(nomFichier).normalize();

        // Defense path traversal : verifier que la cible normalisee reste sous racine
        if (!cible.startsWith(racine)) {
            throw new BusinessException("Chemin de fichier invalide");
        }

        try {
            // REPLACE_EXISTING : on ecrase si UUID collision (tres improbable)
            Files.copy(file.getInputStream(), cible, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new BusinessException("Erreur lors de l'enregistrement du fichier : " + e.getMessage(), e);
        }
        return sousDossier + "/" + nomFichier;
    }

    /**
     * Sauvegarde des bytes bruts (utilise par l'initialiseur de donnees de
     * test pour stocker des images telechargees depuis Internet via HTTP client).
     */
    public String storeBytes(byte[] data, String extension, String sousDossier) {
        if (data == null || data.length == 0) {
            throw new BusinessException("Donnees vides");
        }
        // Si extension absente ou non autorisee, on retombe sur jpg
        String ext = extension == null ? "jpg" : extension.toLowerCase();
        if (!ALLOWED_IMAGE_EXT.contains(ext)) {
            ext = "jpg";
        }
        String nomFichier = UUID.randomUUID() + "." + ext;
        Path cible = racine.resolve(sousDossier).resolve(nomFichier).normalize();
        if (!cible.startsWith(racine)) {
            throw new BusinessException("Chemin invalide");
        }
        try {
            // Files.write : ecrit directement le byte[] (mode WRITE par defaut)
            Files.write(cible, data);
        } catch (IOException e) {
            throw new BusinessException("Erreur d'enregistrement : " + e.getMessage(), e);
        }
        return sousDossier + "/" + nomFichier;
    }

    /**
     * Suppression idempotente : pas d'exception si le fichier n'existe pas.
     * Utile car on appelle delete() lors d'un remplacement avant de stocker
     * le nouveau (et l'ancien peut etre absent).
     */
    public void delete(String cheminRelatif) {
        if (cheminRelatif == null || cheminRelatif.isBlank()) return;
        try {
            Path cible = racine.resolve(cheminRelatif).normalize();
            // Verification path traversal (defense-en-profondeur)
            if (cible.startsWith(racine)) {
                Files.deleteIfExists(cible);
            }
        } catch (IOException e) {
            // Log warning et continue : pas d'exception car cycle de vie metier
            // continue meme si la suppression physique echoue.
            log.warn("Echec de suppression du fichier {} : {}", cheminRelatif, e.getMessage());
        }
    }

    /**
     * Helper : extrait l'extension d'un nom de fichier ("photo.jpg" -> "jpg").
     */
    private String extraireExtension(String nom) {
        if (nom == null) return "";
        int i = nom.lastIndexOf('.');
        return (i >= 0 && i < nom.length() - 1) ? nom.substring(i + 1) : "";
    }
}

/*
 * ============================================================================
 * WebConfig - Configuration MVC (mapping des ressources uploadees)
 * ============================================================================
 *
 * Role :
 *   Expose le repertoire d'upload via /uploads/** afin que les images
 *   televersees (voitures, profils, permis, CIN, reclamations, marques)
 *   soient accessibles depuis les pages Thymeleaf via
 *   <img src="/uploads/voitures/xxx.jpg">.
 *
 * Sans ce mapping, Spring ne servirait que les ressources sous
 * classpath:/static/. Notre dossier uploads/ est hors classpath et serait
 * inaccessible.
 *
 * Utilise par :
 *   - templates/* (toutes les pages) : referencent les images via /uploads/
 *   - service/FileStorageService : produit les chemins compatibles
 *
 * Configuration externe :
 *   app.upload.dir dans application.properties (defaut ./uploads).
 *
 * Securite :
 *   /uploads/** est en permitAll dans SecurityConfig, donc public. Les
 *   images sont devinables uniquement si on connait le chemin UUID (cf
 *   FileStorageService qui genere des UUID v4 quasi-impossible a deviner).
 * ============================================================================
 */
package com.example.locvoitures.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
// Registre des handlers de ressources statiques
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
// Interface a implementer pour customiser la config Web MVC
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Path;
import java.nio.file.Paths;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    /**
     * Repertoire d'upload, lu depuis application.properties.
     */
    @Value("${app.upload.dir}") //valeur lu depuis application.properties
    private String uploadDir;

    /**
     * Override : implementation de WebMvcConfigurer.
     * Ajoute un handler /uploads/** qui pointe vers le repertoire physique.

     * "file:" prefix : indique a Spring que ce n'est pas une ressource du
     * classpath mais un chemin filesystem.
     * Trailing slash important : sans, Spring chercherait un fichier au
     * lieu d'un dossier.
     */
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {

        // Création d’un chemin (Path) vers le dossier des uploads
        Path racine = Paths.get(uploadDir)

                .toAbsolutePath()

                // Nettoie le chemin pour éviter les ".." o"C:/project/../project/uploads"
                // "C:/project/uploads"
                .normalize();
        /*ResourceHandlerRegistry registry

            registry est un objet fourni par Spring permettant :
            d’enregistrer des chemins UR et de les associer à des dossiers réels*/

        // Déclare un handler de ressources statiques
        //Toutes les URLs qui commencent par /uploads/ doivent être traitées comme des ressources statiques.
        registry.addResourceHandler("/uploads/**")

                // Indique à Spring où se trouvent physiquement les fichiers
                // "file:" signifie que les ressources sont dans le système de fichiers
                // Ainsi :
                // URL : http://localhost:8080/uploads/photo.png  ->// C:/Users/med/project/uploads/photo.png
                .addResourceLocations("file:" + racine + "/");
    }
}

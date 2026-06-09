/*
 * ============================================================================
 * DataInitializer - Initialisation des donnees de reference au demarrage
 * ============================================================================
 *
 * Role :
 *   Insere au premier demarrage :
 *   - Le compte admin par defaut (si aucun n'existe deja avec cet email)
 *   - La liste des marques de reference
 *   - La liste des categories de reference
 *   - La liste des equipements de reference
 *   Idempotent : ne re-cree rien si les donnees existent deja.
 *
 * Mecanisme Spring :
 *   - Declare un bean CommandLineRunner via @Bean : Spring l'execute apres
 *     le boot de tous les autres beans, juste avant que l'application ne
 *     soit "ready".
 *
 * Utilise :
 *   - UtilisateurService.creerAdmin : creation admin (hash mdp inclus)
 *   - MarqueRepository / CategorieRepository / EquipementRepository : count
 *     pour test idempotence + saveAll pour insertion en bloc
 *
 * Appele par :
 *   - Spring Boot au demarrage (cf @SpringBootApplication.main)
 *
 * Note :
 *   ADMIN_EMAIL et ADMIN_PASSWORD sont en dur ici (acceptable pour un projet
 *   pedagogique). En production : externaliser via variables d'environnement
 *   ou Vault.
 *
 * Lien avec TestVoituresInitializer :
 *   Cet initialiseur cree marques/categories/equipements en premier.
 *   TestVoituresInitializer s'execute APRES (sur ApplicationReadyEvent) et
 *   y trouve les references.
 * ============================================================================
 */
package com.example.locvoitures.config;

import com.example.locvoitures.entity.Categorie;
import com.example.locvoitures.entity.Equipement;
import com.example.locvoitures.entity.Marque;
import com.example.locvoitures.enumeration.PosteAdmin;
import com.example.locvoitures.repository.CategorieRepository;
import com.example.locvoitures.repository.EquipementRepository;
import com.example.locvoitures.repository.MarqueRepository;
import com.example.locvoitures.repository.UtilisateurRepository;
import com.example.locvoitures.service.metier.AdminService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

// CommandLineRunner : interface Spring Boot dont .run() est appele au boot
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
@RequiredArgsConstructor
@Slf4j
public class DataInitializer {

    /**
     * Email de l'admin par defaut (a changer en production).
     */
    private static final String ADMIN_EMAIL = "mohamed.abroud@polytechnicien.tn";
    private static final String ADMIN_PASSWORD = "admin123";

    // Dependances injectees (constructeur Lombok).
    private final AdminService adminService;
    private final UtilisateurRepository utilisateurRepo;
    private final MarqueRepository marqueRepo;
    private final CategorieRepository categorieRepo;
    private final EquipementRepository equipementRepo;

    /**
     * Bean CommandLineRunner : Spring va executer le lambda au demarrage.
     * Lambda : args -> { ... } correspond a la signature run(String... args).
     */
    @Bean
    public CommandLineRunner initData() {
        return args -> {
            initAdmin();
            initMarques();
            initCategories();
            initEquipements();
        };
    }

    /**
     * Cree l'admin si absent. Idempotent.
     */
    private void initAdmin() {
        if (utilisateurRepo.existsByEmail(ADMIN_EMAIL)) {
            log.info("Admin par defaut deja present ({})", ADMIN_EMAIL);
            return;
        }
        // Super-admin par defaut, poste DIRECTION, embauche aujourd'hui.
        adminService.creerAdmin(ADMIN_EMAIL, ADMIN_PASSWORD, "Admin", "Systeme",
                PosteAdmin.DIRECTION);
        log.info(">>> Admin cree : {} / mot de passe : {} (a changer en production)",
                ADMIN_EMAIL, ADMIN_PASSWORD);
    }

    /**
     * Cree les marques si la table est vide (test global, pas par marque).
     */
    private void initMarques() {
        if (marqueRepo.count() > 0) return;
        List<String> marques = List.of(
                "Renault", "Peugeot", "Citroen", "Volkswagen",
                "BMW", "Mercedes-Benz", "Audi", "Toyota",
                "Hyundai", "Kia", "Ford", "Fiat"
        );
        // forEach + reference de methode pour construire et sauver
        marques.forEach(nom -> marqueRepo.save(Marque.builder().nom(nom).build()));
        log.info("Marques initialisees : {}", marques.size());
    }

    /**
     * Cree les categories avec descriptions metier.
     */
    private void initCategories() {
        if (categorieRepo.count() > 0) return;
        List<Categorie> categories = List.of(
                Categorie.builder().nom("Citadine").description("Petite voiture pour la ville").build(),
                Categorie.builder().nom("Berline").description("Confort et elegance pour les longs trajets").build(),
                Categorie.builder().nom("SUV").description("Vehicule polyvalent et spacieux").build(),
                Categorie.builder().nom("Utilitaire").description("Vehicule pour transport de marchandises").build(),
                Categorie.builder().nom("Coupe").description("Vehicule sportif a deux portes").build(),
                Categorie.builder().nom("Monospace").description("Grand espace pour les familles").build(),
                Categorie.builder().nom("Cabriolet").description("Decapotable").build()
        );
        // saveAll : batch insert plus efficace que N save()
        categorieRepo.saveAll(categories);
        log.info("Categories initialisees : {}", categories.size());
    }

    /**
     * Cree les equipements avec descriptions.
     */
    private void initEquipements() {
        if (equipementRepo.count() > 0) return;
        List<Equipement> equipements = List.of(
                Equipement.builder().nom("Climatisation").description("Climatisation manuelle ou automatique").build(),
                Equipement.builder().nom("GPS").description("Systeme de navigation par satellite integre").build(),
                Equipement.builder().nom("Bluetooth").description("Connexion sans fil pour smartphones et appels mains libres").build(),
                Equipement.builder().nom("Camera de recul").description("Aide visuelle au stationnement").build(),
                Equipement.builder().nom("Toit ouvrant").description("Toit ouvrant electrique panoramique ou classique").build(),
                Equipement.builder().nom("Sieges chauffants").description("Chauffage des sieges avant").build(),
                Equipement.builder().nom("Apple CarPlay").description("Integration de l'iPhone sur l'ecran central").build(),
                Equipement.builder().nom("Android Auto").description("Integration des smartphones Android sur l'ecran central").build(),
                Equipement.builder().nom("Regulateur de vitesse").description("Maintien automatique de la vitesse de croisiere").build(),
                Equipement.builder().nom("ABS").description("Systeme antiblocage des roues au freinage").build(),
                Equipement.builder().nom("Phares LED").description("Eclairage avant a diodes electroluminescentes").build(),
                Equipement.builder().nom("Vitres electriques").description("Commande electrique des vitres avant et arriere").build()
        );
        equipementRepo.saveAll(equipements);
        log.info("Equipements initialises : {}", equipements.size());
    }
}

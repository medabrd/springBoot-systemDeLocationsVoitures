/*
 * ============================================================================
 * TestVoituresInitializer - Donnees de demo : flotte de voitures
 * ============================================================================
 *
 * Role :
 *   Insere 10 voitures de demonstration au premier demarrage pour que le
 *   catalogue ne soit pas vide a la livraison. Telecharge les photos depuis
 *   loremflickr (best-effort : si echec, la voiture est creee sans photo).
 *
 * Mecanisme Spring :
 *   - @EventListener(ApplicationReadyEvent.class) : invoque APRES toutes les
 *     CommandLineRunner (donc apres DataInitializer qui cree les marques/
 *     categories/equipements de reference)
 *   - @Transactional : englobe toute la boucle d'insertion dans une seule TX
 *
 * Idempotence :
 *   if (voitureRepo.count() > 0) return : on n'insere que si la table est
 *   vide. Permet de relancer l'application sans dupliquer.
 *
 * Telechargement images :
 *   HttpClient Java 11+ avec timeouts. Si echec (timeout, 404, image trop
 *   petite), la voiture est creee sans photo plutot que de bloquer le boot.
 *
 * Appelle :
 *   - MarqueRepository / CategorieRepository / EquipementRepository :
 *     findByNomIgnoreCase pour resoudre les references
 *   - VoitureRepository : save
 *   - FileStorageService.storeBytes : sauvegarde l'image telechargee
 *
 * Note :
 *   Le record interne VoitureTest (Java 14+) sert de DTO local immuable
 *   pour parametrer la creation d'une voiture sans constructeur a 18 args.
 * ============================================================================
 */
package com.example.locvoitures.config;

// Wildcard pour toutes les entites manipulees
import com.example.locvoitures.entity.*;
import com.example.locvoitures.enumeration.Carburant;
import com.example.locvoitures.enumeration.StatutVoiture;
import com.example.locvoitures.enumeration.Transmission;
import com.example.locvoitures.repository.CategorieRepository;
import com.example.locvoitures.repository.EquipementRepository;
import com.example.locvoitures.repository.MarqueRepository;
import com.example.locvoitures.repository.VoitureRepository;
import com.example.locvoitures.service.utils.FileStorageService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

// Evenement Spring : application boot complet
import org.springframework.boot.context.event.ApplicationReadyEvent;
// @EventListener : enregistre la methode comme listener d'un type d'evenement
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
// HttpClient Java 11+ : remplace HttpURLConnection (vieillot)
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Component
@RequiredArgsConstructor
@Slf4j//pour creer un objet log
public class TestVoituresInitializer {

    private final VoitureRepository voitureRepo;
    private final MarqueRepository marqueRepo;
    private final CategorieRepository categorieRepo;
    private final EquipementRepository equipementRepo;
    private final FileStorageService fileStorage;

    /**
     * Methode invoquee par Spring quand l'application est prete.
     * Skip si la table est deja peuplee (idempotent).
     */
    @EventListener(ApplicationReadyEvent.class) //execution apres commandLiner
    @Transactional
    public void initTestVoitures() {
        if (voitureRepo.count() > 0) {
            log.info("Voitures de test deja presentes ({}) - skip", voitureRepo.count());
            return;
        }
        if (marqueRepo.count() == 0 || categorieRepo.count() == 0) {
            log.warn("Marques ou categories non initialisees - skip voitures de test");
            return;
        }

        log.info(">>> Insertion des voitures de test...");

        // Liste de 10 voitures variees pour avoir un catalogue de demo riche.
        List<VoitureTest> donnees = List.of(
                new VoitureTest("Renault", "Clio V", 2022, "Rouge", "1234TUN5678",
                        new BigDecimal("80.00"), "Citadine economique et fiable, parfaite pour la ville",
                        "Citadine", Transmission.MANUELLE, Carburant.ESSENCE,
                        5.5, 90, 5, 5, 391,
                        List.of("Climatisation", "Bluetooth", "ABS"),
                        "https://loremflickr.com/640/480/car?lock=1"),

                new VoitureTest("Peugeot", "208 GT", 2023, "Bleu", "2345TUN6789",
                        new BigDecimal("90.00"), "Style sportif, motorisation efficiente",
                        "Citadine", Transmission.MANUELLE, Carburant.DIESEL,
                        4.8, 100, 5, 5, 311,
                        List.of("Climatisation", "Bluetooth", "Camera de recul", "Apple CarPlay"),
                        "https://loremflickr.com/640/480/car?lock=2"),

                new VoitureTest("Citroen", "C3", 2022, "Blanc", "3456TUN7890",
                        new BigDecimal("75.00"), "Confort et personnalite, ideale famille",
                        "Citadine", Transmission.AUTOMATIQUE, Carburant.ESSENCE,
                        5.8, 83, 5, 5, 300,
                        List.of("Climatisation", "Bluetooth", "Regulateur de vitesse"),
                        "https://loremflickr.com/640/480/car?lock=3"),

                new VoitureTest("Renault", "Megane", 2023, "Gris", "4567TUN8901",
                        new BigDecimal("120.00"), "Berline elegante, technologie avancee",
                        "Berline", Transmission.MANUELLE, Carburant.DIESEL,
                        4.5, 130, 5, 5, 384,
                        List.of("Climatisation", "GPS", "Bluetooth", "Camera de recul", "Regulateur de vitesse"),
                        "https://loremflickr.com/640/480/car?lock=4"),

                new VoitureTest("BMW", "X3", 2023, "Noir", "5678TUN9012",
                        new BigDecimal("250.00"), "SUV haut de gamme, finition premium",
                        "SUV", Transmission.AUTOMATIQUE, Carburant.DIESEL,
                        6.2, 190, 5, 5, 550,
                        List.of("Climatisation", "GPS", "Bluetooth", "Camera de recul", "Toit ouvrant",
                                "Sieges chauffants", "Apple CarPlay", "Regulateur de vitesse"),
                        "https://loremflickr.com/640/480/car?lock=5"),

                new VoitureTest("Volkswagen", "Golf", 2022, "Argent", "6789TUN0123",
                        new BigDecimal("130.00"), "Compacte de reference, fiabilite legendaire",
                        "Berline", Transmission.AUTOMATIQUE, Carburant.ESSENCE,
                        5.3, 110, 5, 5, 380,
                        List.of("Climatisation", "GPS", "Bluetooth", "Apple CarPlay", "Android Auto"),
                        "https://loremflickr.com/640/480/car?lock=6"),

                new VoitureTest("Toyota", "Yaris Hybrid", 2023, "Blanc", "7890TUN1234",
                        new BigDecimal("100.00"), "Citadine hybride, consommation reduite",
                        "Citadine", Transmission.AUTOMATIQUE, Carburant.HYBRIDE,
                        3.6, 116, 5, 5, 286,
                        List.of("Climatisation", "Bluetooth", "Camera de recul", "Regulateur de vitesse"),
                        "https://loremflickr.com/640/480/car?lock=7"),

                new VoitureTest("Mercedes-Benz", "Vito", 2022, "Blanc", "8901TUN2345",
                        new BigDecimal("180.00"), "Utilitaire spacieux pour livraisons et transports",
                        "Utilitaire", Transmission.MANUELLE, Carburant.DIESEL,
                        7.5, 136, 8, 4, 1500,
                        List.of("Climatisation", "Bluetooth", "ABS"),
                        "https://loremflickr.com/640/480/car?lock=8"),

                new VoitureTest("Hyundai", "Tucson", 2023, "Bleu fonce", "9012TUN3456",
                        new BigDecimal("160.00"), "SUV familial, grand coffre, equipement complet",
                        "SUV", Transmission.AUTOMATIQUE, Carburant.HYBRIDE,
                        5.6, 180, 5, 5, 620,
                        List.of("Climatisation", "GPS", "Bluetooth", "Camera de recul", "Apple CarPlay",
                                "Sieges chauffants", "Phares LED"),
                        "https://loremflickr.com/640/480/car?lock=9"),

                new VoitureTest("Audi", "A3", 2023, "Gris", "0123TUN4567",
                        new BigDecimal("180.00"), "Berline premium, design raffine",
                        "Berline", Transmission.AUTOMATIQUE, Carburant.ESSENCE,
                        5.9, 150, 5, 5, 380,
                        List.of("Climatisation", "GPS", "Bluetooth", "Camera de recul", "Toit ouvrant",
                                "Apple CarPlay", "Phares LED", "Regulateur de vitesse"),
                        "https://loremflickr.com/640/480/car?lock=10")
        );

        // Compteurs pour log final
        int crees = 0;
        int sansPhoto = 0;
        for (VoitureTest data : donnees) {
            try {
                Voiture v = creerVoiture(data);
                if (v.getPhoto() == null) sansPhoto++;
                crees++;
            } catch (Exception e) {
                // Log et continue : ne pas faire echouer tout le batch pour
                // une erreur isolee.
                log.warn("Echec creation voiture {} {} : {}", data.marque, data.modele, e.getMessage());
            }
        }
        log.info(">>> Voitures de test inserees : {} (dont {} sans photo)", crees, sansPhoto);
    }

    /**
     * Construit et persiste une Voiture a partir des donnees de test.
     * Resout les references (marque/categorie/equipements) et telecharge
     * la photo si possible.
     */
    private Voiture creerVoiture(VoitureTest data) {
        // Marque : utilise l'existante ou cree si absente (defense)
        Marque marque = marqueRepo.findByNomIgnoreCase(data.marque)
                .orElseGet(() -> marqueRepo.save(Marque.builder().nom(data.marque).build()));

        // Categorie : meme strategie
        Categorie categorie = categorieRepo.findByNomIgnoreCase(data.categorie)
                .orElseGet(() -> categorieRepo.save(
                        Categorie.builder().nom(data.categorie).build()));

        // Equipements : on ajoute seulement ceux qui existent (les autres ignores)
        Set<Equipement> equipements = new HashSet<>();
        for (String nomEq : data.equipements) {
            equipementRepo.findByNomIgnoreCase(nomEq).ifPresent(equipements::add);
        }

        // Construction des details techniques
        DetailsVoiture details = DetailsVoiture.builder()
                .transmission(data.transmission)
                .typeCarburant(data.carburant)
                .consommation(data.consommation)
                .puissance(data.puissance)
                .nombrePlaces(data.places)
                .nombrePortes(data.portes)
                .volumeCoffre(data.coffre)
                .build();

        // Construction de la voiture
        Voiture v = Voiture.builder()
                .modele(data.modele)
                .annee(data.annee)
                .couleur(data.couleur)
                .immatriculation(data.immatriculation)
                .tarifJournalier(data.tarif)
                .description(data.description)
                .statutGeneral(StatutVoiture.ACTIVE)
                .marque(marque)
                .categorie(categorie)
                .equipements(equipements)
                .details(details)
                .build();

        // Photo : best-effort. Echec -> voiture sans photo (placeholder dans la UI)
        String photoPath = telechargerEtStocker(data.imageUrl);
        if (photoPath != null) {
            v.setPhoto(photoPath);
        }

        return voitureRepo.save(v);
    }

    /**
     * Telechargement HTTP de l'image. Best-effort avec timeouts pour ne pas
     * bloquer le boot si Internet indispo.
     * Retour : chemin relatif uploads/voitures/xxx.jpg ou null si echec.
     */
    private String telechargerEtStocker(String url) {
        try {
            // HttpClient avec timeout connection 8s + redirections suivies
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(8))
                    .followRedirects(HttpClient.Redirect.ALWAYS)
                    .build();
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(15))
                    .GET()
                    .build();
            // BodyHandlers.ofByteArray : recupere le corps en byte[]
            HttpResponse<byte[]> resp = client.send(req, HttpResponse.BodyHandlers.ofByteArray());
            final int HTTP_OK = 200;
            if (resp.statusCode() != HTTP_OK) {
                log.warn("HTTP {} pour {}", resp.statusCode(), url);
                return null;
            }
            byte[] data = resp.body();
            // Sanity check : image au moins 1Ko (sinon souvent placeholder erreur)
            if (data == null || data.length < 1000) {
                log.warn("Image trop petite ({}o) pour {}", data == null ? 0 : data.length, url);
                return null;
            }
            return fileStorage.storeBytes(data, "jpg", "voitures");
        } catch (Exception e) {
            log.warn("Echec telechargement {} : {}", url, e.getMessage());
            return null;
        }
    }

    /**
     * Record (Java 14+) : DTO local immuable pour parametrer la creation.
     * Equivalent a une classe avec final fields + constructor + getters +
     * equals/hashCode/toString, en plus concis.
     */
    private record VoitureTest(
            String marque, String modele, int annee, String couleur, String immatriculation,
            BigDecimal tarif, String description,
            String categorie, Transmission transmission, Carburant carburant,
            double consommation, int puissance, int places, int portes, int coffre,
            List<String> equipements,
            String imageUrl) {}
}

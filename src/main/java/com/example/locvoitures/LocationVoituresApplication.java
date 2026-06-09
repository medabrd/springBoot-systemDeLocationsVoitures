/*
 * ============================================================================
 * LocationVoituresApplication - Classe principale Spring Boot
 * ============================================================================
 *
 * Role :
 *   Point d'entree de l'application. Spring Boot scanne ce package et tous
 *   les sous-packages pour detecter @Component, @Service, @Repository,
 *   @Controller, @Configuration et instancier le contexte.
 *
 * Appele par :
 *   - JVM au demarrage : la JVM cherche la methode static main et l'execute
 *   - mvn spring-boot:run, java -jar ..., IDE Run
 *
 * Appelle :
 *   - SpringApplication.run : bootstrappe Spring (auto-config, scan composants,
 *     embedded Tomcat, etc.)
 *
 * Annotations :
 *   @SpringBootApplication = @Configuration + @EnableAutoConfiguration +
 *   @ComponentScan. Active toute la mecanique Spring Boot.
 *
 *   @EnableScheduling : active la detection des @Scheduled (utilisee par
 *   LocationScheduler pour les taches automatiques).
 *
 * Convention :
 *   La classe doit etre dans le package racine pour que le scan composant
 *   trouve tout (sub-packages com.example.locvoitures.*). Si elle etait
 *   dans un sub-package, certains beans ne seraient pas detectes.
 * ============================================================================
 */
package com.example.locvoitures;

// SpringApplication : helper qui bootstrappe l'application
import org.springframework.boot.SpringApplication;
// @SpringBootApplication : meta-annotation qui combine plusieurs annotations
//                         Spring (auto-config + scan + config)
import org.springframework.boot.autoconfigure.SpringBootApplication;
// @EnableScheduling : active le scan des @Scheduled
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class LocationVoituresApplication {

    /**
     * Point d'entree JVM.
     * SpringApplication.run :
     *   1. Detecte la classe principale
     *   2. Configure le contexte Spring (auto-config + scan)
     *   3. Lance les CommandLineRunner (notamment DataInitializer)
     *   4. Demarre l'embedded Tomcat (port configure dans application.properties)
     *   5. Emet l'ApplicationReadyEvent (capte par TestVoituresInitializer)
     */
    public static void main(String[] args) {
        SpringApplication.run(LocationVoituresApplication.class, args);
    }
}

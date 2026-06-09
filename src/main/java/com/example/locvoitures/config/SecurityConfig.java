/*
 * ============================================================================
 * SecurityConfig - Configuration Spring Security 6
 * ============================================================================
 *
 * Role :
 *   Configure la chaine de filtres Spring Security :
 *   - Regles d'authorization par URL (public, CLIENT, ADMIN)
 *   - Form login avec champ email + motDePasse
 *   - Redirection apres login selon role
 *   - Logout et gestion de session
 *   - Page 403 personnalisee
 *
 * Beans declares :
 *   - DaoAuthenticationProvider : branche CustomUserDetailsService et
 *     PasswordEncoder
 *   - AuthenticationManager : expose pour utilisation programmatique si besoin
 *   - SecurityFilterChain : la chaine de filtres principale
 *   - AuthenticationSuccessHandler : redirection role-based apres login
 *
 * Convention .hasRole("X") :
 *   Matche une authority "ROLE_X". Le prefixe "ROLE_" est gere par
 *   CustomUserDetailsService via User.roles(...).
 *
 * @EnableMethodSecurity :
 *   Active @PreAuthorize / @PostAuthorize / @Secured sur les methodes.
 *   Pas largement utilise dans ce projet (on prefere les regles URL), mais
 *   active pour permettre un usage ponctuel.
 *
 * Securite par defaut :
 *   - CSRF active (Spring genere des tokens, Thymeleaf les injecte)
 *   - Session cookie HttpOnly par defaut
 *   - Headers de securite par defaut (X-Frame-Options, etc.)
 * ============================================================================
 */
package com.example.locvoitures.config;

import com.example.locvoitures.service.auth.CustomUserDetailsService;
import lombok.RequiredArgsConstructor;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

// AuthenticationManager : entry point central de l'authentification
import org.springframework.security.authentication.AuthenticationManager;
// DaoAuthenticationProvider : provider qui charge l'utilisateur via
// UserDetailsService et compare le mdp via PasswordEncoder
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
// @EnableMethodSecurity : active @PreAuthorize et autres annotations methode
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
// HttpSecurity : DSL pour configurer la chaine de filtres
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.password.PasswordEncoder;
// SecurityFilterChain : type de retour pour la chaine de filtres
import org.springframework.security.web.SecurityFilterChain;
// AuthenticationSuccessHandler : handler invoque apres login reussi
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;

@Configuration
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    /**
     * Beans injectes par constructeur (Lombok).
     */
    private final CustomUserDetailsService userDetailsService;
    private final PasswordEncoder passwordEncoder;

    /**
     * Provider d'authentification : associe UserDetailsService + PasswordEncoder.
     * Quand AuthenticationManager est invoque, il delegue a ce provider.
     */
    @Bean
    public DaoAuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder);
        return provider;
    }

    /**
     * Expose l'AuthenticationManager (utile si on veut authentifier
     * programmatiquement, par ex apres inscription pour auto-login).
     */
    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration cfg) throws Exception {
        return cfg.getAuthenticationManager();
    }

    /**
     * Chaine de filtres de securite : ordre des regles, form login, logout.
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {

                // === AuthorizationFilter ===
             http.authorizeHttpRequests(auth -> auth
                        // Public : pages d'accueil/auth, statiques, pages d'erreur
                        .requestMatchers(
                                "/", "/index",
                                "/auth/**",
                                "/css/**",
                                "/erreur/**"
                        ).permitAll()
                        // Fichiers uploades : reserves aux utilisateurs connectes
                        // (admin OU client). Empeche un anonyme de telecharger
                        // une photo de permis/CIN en connaissant l'UUID.
                        .requestMatchers("/uploads/**").authenticated()
                        // Catalogue : reserve aux CLIENT connectes (apres feedback,
                        // n'est plus accessible aux utilisateurs anonymes)
                        .requestMatchers("/catalogue", "/catalogue/**", "/api/voitures/*/disponibilite")
                                .hasRole("CLIENT")
                        // /admin/** reserve aux ADMIN
                        .requestMatchers("/admin/**").hasRole("ADMIN")
                        // /client/** reserve aux CLIENT
                        .requestMatchers("/client/**").hasRole("CLIENT")
                        // Tout le reste necessite d'etre authentifie
                        .anyRequest().authenticated()
                )
                // ===  UsernamePasswordAuthenticationFilter ===
                .formLogin(form -> form
                        .loginPage("/auth/login")            // custom login page no usage for springgs default login page , au redirection a cette pas pour tout utilisateur non authentifié
                        .loginProcessingUrl("/auth/login")    // Spring écoute par défaut sur /login avec cette ligne spring intercept /auth/lognin meme si pas de @postmapping(/auth/login) dans mes controllers
                        .usernameParameter("email")           // nom du field email
                        .passwordParameter("motDePasse")      // nom du field mdp
                        .successHandler(authSuccessHandler()) // redirection role-based
                        .failureUrl("/auth/login?error=true") // controller -> modelsetatt(errorMessage) -> loinhtml declenche th:replace layout::alerts avec message derreur
                        .permitAll() // tout le monde peut atteindre /auth/login.
                )
                // === LogoutFilter ===
                .logout(logout -> logout
                        .logoutUrl("/auth/logout") //spring security managed
                        .logoutSuccessUrl("/auth/login?logout=true") //controlleur de redirection apres post
                        .invalidateHttpSession(true) // LogoutFilter appelle request.getSession().invalidate(). Toutes les données stockées en session côté serveur sont détruites (le SecurityContext, le token CSRF, les attributs de session, etc.)
                        .clearAuthentication(true) // netoyage coté machine client du thread local
                        .permitAll() // /auth/logout exposé pour tout le monde
                )
                // === ExceptionTranslationFilter  ===
                .exceptionHandling(eh -> eh
                        .accessDeniedPage("/erreur/403")
                )
                // Branchement du provider declare plus haut
                .authenticationProvider(authenticationProvider());

        return http.build();
    }

    /**
     * interface fonctionnel spring sec (implementtion en classe anonyme interne)
     POST /auth/login
     ↓
     UsernamePasswordAuthenticationFilter intercepte
     ↓
     authenticationManager.authenticate() → OK (BCrypt matche)
     ↓
     filter.successfulAuthentication() :
     - stocke l'Authentication dans SecurityContextHolder
     - session HTTP régénérée (anti session-fixation)
     - APPELLE votre successHandler  ←─── ICI
     ↓
     votre handler → response.sendRedirect("/catalogue") ou "/admin/dashboard"
     */
    @Bean
    public AuthenticationSuccessHandler authSuccessHandler() {
        return (request, response, authentication) -> {
            // Cherche dans les authorities du principal s'il y a ROLE_ADMIN

            boolean isAdmin = authentication.getAuthorities().stream()
                    /*
            L'Authentication qui vient d'être validée, contenant principal (username),
             credentials (mdp effacé),
             authorities (liste des rôles) les roles sont produit dans le userDetialsService
           */
                    .anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
            response.sendRedirect(isAdmin ? "/admin/dashboard" : "/catalogue");
        };
    }
}

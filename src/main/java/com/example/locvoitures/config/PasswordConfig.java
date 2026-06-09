/*
 * ============================================================================
 * PasswordConfig - Bean Spring exposant le PasswordEncoder
 * ============================================================================
 *
 * Role :
 *   Declaration isolee du bean PasswordEncoder (BCrypt). Separe de
 *   SecurityConfig pour eviter une dependance circulaire :
 *     SecurityConfig -> CustomUserDetailsService -> UtilisateurService
 *     -> PasswordEncoder
 *   Si PasswordEncoder etait declare dans SecurityConfig, il dependrait
 *   indirectement de lui-meme.
 *
 * Utilise par :
 *   - service/UtilisateurService : passwordEncoder.encode(mdp), passwordEncoder.matches(...)
 *   - config/SecurityConfig : utilise comme parametre du DaoAuthenticationProvider
 *   - service/CustomUserDetailsService : indirectement (Spring Security
 *     compare le hash via ce bean)
 *
 * BCrypt :
 *   Algorithme de hashage adaptatif (cout configurable). Cout par defaut
 *   10 -> ~100ms par hash, ce qui ralentit les attaques par dictionnaire.
 *   Le sel est inclus dans le hash output (60 caracteres typiques).
 *
 * Pattern @Configuration :
 *   @Bean expose une methode qui produit le bean. Spring l'instancie au
 *   demarrage et le rend disponible pour @Autowired/@RequiredArgsConstructor.
 * ============================================================================
 */
package com.example.locvoitures.config;

// @Bean : marque une methode qui produit un bean Spring
import org.springframework.context.annotation.Bean;
// @Configuration : classe de configuration (analogue a un @Component
//                  mais reserve a la declaration de beans)
import org.springframework.context.annotation.Configuration;
// Implementation BCrypt de PasswordEncoder
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
// Interface generique de hash de mot de passe
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
public class PasswordConfig {

    /**
     * Bean : Spring va appeler cette methode et conserver le retour
     * comme un singleton bean. Le nom du bean = "passwordEncoder" (nom
     * de la methode) si non specifie.
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        // BCryptPasswordEncoder avec strength par defaut (10).
        // Possible d'augmenter pour plus de securite au prix de la latence.
        return new BCryptPasswordEncoder();
    }
}

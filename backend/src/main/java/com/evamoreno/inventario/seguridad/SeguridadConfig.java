package com.evamoreno.inventario.seguridad;

import java.io.IOException;
import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.logout.HttpStatusReturningLogoutSuccessHandler;
import org.springframework.security.web.authentication.rememberme.TokenBasedRememberMeServices;
import org.springframework.security.web.authentication.rememberme.TokenBasedRememberMeServices.RememberMeTokenAlgorithm;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;

import jakarta.servlet.http.HttpServletResponse;

/**
 * Seguridad de la API.
 *
 * <ul>
 * <li>Todo /api/** exige haber iniciado sesión, salvo el estado de salud y las rutas para entrar.</li>
 * <li>La sesión vive en una cookie HttpOnly (el JavaScript no puede leerla). Si se marca "recordarme",
 * una segunda cookie firmada mantiene la sesión 30 días, aunque el servidor se reinicie.</li>
 * <li>Protección CSRF: cada operación que cambia datos debe llevar el token que el servidor entrega en la
 * cookie XSRF-TOKEN. Así otra página web no puede hacer pedidos "en nombre" de quien tiene la sesión abierta.</li>
 * </ul>
 */
@Configuration
public class SeguridadConfig {

    static final String COOKIE_RECORDAR = "recordar";
    static final Duration DURACION_RECORDAR = Duration.ofDays(30);

    @Bean
    SecurityFilterChain seguridad(HttpSecurity http, TokenBasedRememberMeServices recordarme) throws Exception {
        http
                .authorizeHttpRequests(a -> a
                        .requestMatchers("/api/health", "/api/auth/estado", "/api/auth/login", "/api/auth/configurar")
                        .permitAll()
                        .requestMatchers("/api/**", "/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**")
                        .authenticated()
                        // Las pantallas (HTML, JS, CSS) son públicas: sin sesión solo muestran el inicio de sesión
                        .anyRequest().permitAll())
                .csrf(c -> c.spa())
                .rememberMe(r -> r.rememberMeServices(recordarme))
                .logout(l -> l
                        .logoutUrl("/api/auth/salir")
                        .logoutSuccessHandler(new HttpStatusReturningLogoutSuccessHandler(HttpStatus.NO_CONTENT))
                        .deleteCookies("JSESSIONID"))
                .exceptionHandling(e -> e
                        .authenticationEntryPoint((request, response, ex) -> problema(response, 401, "No autorizado",
                                "Inicia sesión para continuar."))
                        .accessDeniedHandler((request, response, ex) -> problema(response, 403, "Acceso denegado",
                                "La página quedó abierta mucho tiempo o la sesión cambió. Recarga la página e intenta de nuevo.")))
                .formLogin(f -> f.disable())
                .httpBasic(b -> b.disable())
                .headers(Customizer.withDefaults());
        return http.build();
    }

    @Bean
    PasswordEncoder codificadorDeClaves() {
        // BCrypt por defecto, con prefijo {bcrypt}: permite cambiar de algoritmo en el futuro sin perder cuentas
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    @Bean
    UserDetailsService usuarios(UsuarioRepository repositorio) {
        return nombre -> repositorio.findByUsuarioIgnoreCase(nombre.trim())
                .map(u -> User.withUsername(u.getUsuario())
                        .password(u.getClaveHash())
                        .disabled(!u.isActivo())
                        .authorities("USUARIO")
                        .build())
                .orElseThrow(() -> new UsernameNotFoundException("No existe el usuario"));
    }

    @Bean
    AuthenticationManager autenticador(UserDetailsService usuarios, PasswordEncoder codificador) {
        DaoAuthenticationProvider proveedor = new DaoAuthenticationProvider(usuarios);
        proveedor.setPasswordEncoder(codificador);
        return new ProviderManager(proveedor);
    }

    /**
     * Cookie "recordarme" firmada con una clave secreta (variable de entorno APP_CLAVE_RECORDAR en producción).
     * La firma incluye el hash de la contraseña: si se cambia la contraseña, todas estas cookies dejan de servir.
     */
    @Bean
    TokenBasedRememberMeServices recordarme(@Value("${app.seguridad.clave-recordar}") String clave,
            UserDetailsService usuarios) {
        TokenBasedRememberMeServices servicio = new TokenBasedRememberMeServices(clave, usuarios,
                RememberMeTokenAlgorithm.SHA256);
        servicio.setCookieName(COOKIE_RECORDAR);
        servicio.setTokenValiditySeconds((int) DURACION_RECORDAR.toSeconds());
        // El controlador de inicio de sesión decide cuándo recordar (según la casilla "Recordarme")
        servicio.setAlwaysRemember(true);
        return servicio;
    }

    @Bean
    SecurityContextRepository repositorioDeSesion() {
        return new HttpSessionSecurityContextRepository();
    }

    /** Errores de seguridad con el mismo formato (Problem Details) que el resto de la API. */
    private static void problema(HttpServletResponse response, int status, String titulo, String detalle)
            throws IOException {
        response.setStatus(status);
        response.setContentType("application/problem+json;charset=UTF-8");
        response.getWriter().write("{\"status\":%d,\"title\":\"%s\",\"detail\":\"%s\"}".formatted(status, titulo, detalle));
    }
}

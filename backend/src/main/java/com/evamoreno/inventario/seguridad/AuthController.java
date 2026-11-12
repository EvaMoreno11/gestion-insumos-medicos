package com.evamoreno.inventario.seguridad;

import java.util.Arrays;

import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.rememberme.TokenBasedRememberMeServices;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.evamoreno.inventario.negocio.NegocioService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/auth")
@Tag(name = "Sesión", description = "Inicio y cierre de sesión, primera cuenta y cambio de contraseña")
public class AuthController {

    private final AuthService service;
    private final NegocioService negocio;
    private final SecurityContextRepository repositorioDeSesion;
    private final TokenBasedRememberMeServices recordarme;

    public AuthController(AuthService service, NegocioService negocio, SecurityContextRepository repositorioDeSesion,
            TokenBasedRememberMeServices recordarme) {
        this.service = service;
        this.negocio = negocio;
        this.repositorioDeSesion = repositorioDeSesion;
        this.recordarme = recordarme;
    }

    /**
     * Lo primero que consulta la app al abrirse. También entrega el token CSRF en la cookie XSRF-TOKEN,
     * que la app debe reenviar en cada operación que cambie datos.
     */
    @GetMapping("/estado")
    @Operation(summary = "¿Hay sesión iniciada? ¿Falta crear la primera cuenta?")
    public AuthDto.Estado estado(Authentication autenticacion, CsrfToken csrf) {
        csrf.getToken(); // genera el token y lo deja en la cookie
        boolean autenticado = autenticacion != null && autenticacion.isAuthenticated()
                && !(autenticacion instanceof AnonymousAuthenticationToken);
        return new AuthDto.Estado(autenticado, autenticado ? autenticacion.getName() : null,
                service.requiereConfiguracion(), service.requiereCodigo(), negocio.obtener().nombre());
    }

    @PostMapping("/login")
    @Operation(summary = "Iniciar sesión", description = "Con recordar=true el dispositivo mantiene la sesión 30 días.")
    public AuthDto.Estado login(@Valid @RequestBody AuthDto.LoginRequest r, HttpServletRequest request,
            HttpServletResponse response) {
        Authentication autenticacion = service.autenticar(r.usuario(), r.clave());

        // Sesión nueva al entrar: evita que alguien reutilice un identificador de sesión anterior
        HttpSession anterior = request.getSession(false);
        if (anterior != null) {
            anterior.invalidate();
        }
        request.getSession(true);

        SecurityContext contexto = SecurityContextHolder.createEmptyContext();
        contexto.setAuthentication(autenticacion);
        SecurityContextHolder.setContext(contexto);
        repositorioDeSesion.saveContext(contexto, request, response);

        if (r.recordar()) {
            recordarme.loginSuccess(request, response, autenticacion);
        }
        return new AuthDto.Estado(true, autenticacion.getName(), false, false, negocio.obtener().nombre());
    }

    @PostMapping("/configurar")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Crear la primera cuenta", description = "Solo funciona mientras no exista ninguna cuenta.")
    public void configurar(@Valid @RequestBody AuthDto.ConfigurarRequest r) {
        service.crearPrimeraCuenta(r);
    }

    @PutMapping("/clave")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Cambiar la contraseña",
            description = "Cierra la sesión recordada en los demás dispositivos (tendrán que volver a entrar).")
    public void cambiarClave(@Valid @RequestBody AuthDto.CambiarClaveRequest r, Authentication autenticacion,
            HttpServletRequest request, HttpServletResponse response) {
        service.cambiarClave(autenticacion.getName(), r);
        // Este dispositivo sigue recordado, ahora con una cookie firmada con la nueva contraseña
        boolean recordado = request.getCookies() != null && Arrays.stream(request.getCookies())
                .map(Cookie::getName).anyMatch(SeguridadConfig.COOKIE_RECORDAR::equals);
        if (recordado) {
            recordarme.loginSuccess(request, response, autenticacion);
        }
    }
}

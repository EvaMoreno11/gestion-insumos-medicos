package com.evamoreno.inventario.common;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;

/**
 * En producción el servidor entrega también la app (el build de Vite va en {@code classpath:/static}):
 * así app y API comparten dirección y la cookie de sesión funciona en Safari.
 *
 * <ul>
 * <li>Las rutas de la app ({@code /pedidos/15}, {@code /inventario}…) devuelven {@code index.html}: así se
 * puede recargar la página o abrir un enlace directo.</li>
 * <li>Los archivos de {@code /assets} llevan un código en el nombre que cambia con cada versión: se guardan en
 * el navegador por un año. Lo demás (index.html) se revalida siempre, para que se vea la versión nueva.</li>
 * </ul>
 * En desarrollo no hay {@code index.html} (la app la sirve Vite) y todo sigue igual.
 */
@Configuration
public class AppWebConfig implements WebMvcConfigurer {

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/assets/**")
                .addResourceLocations("classpath:/static/assets/")
                .setCacheControl(CacheControl.maxAge(365, TimeUnit.DAYS).cachePublic());

        registry.addResourceHandler("/**")
                .addResourceLocations("classpath:/static/")
                .setCacheControl(CacheControl.noCache())
                .resourceChain(true)
                .addResolver(new PathResourceResolver() {
                    @Override
                    protected Resource getResource(String ruta, Resource ubicacion) throws IOException {
                        // La resolución normal (comprueba además que el archivo esté dentro de /static)
                        Resource recurso = super.getResource(ruta, ubicacion);
                        if (recurso != null) {
                            return recurso;
                        }
                        // Una ruta de la API o un archivo que no existe es un 404 de verdad
                        if (ruta.startsWith("api/") || ruta.contains(".")) {
                            return null;
                        }
                        Resource index = ubicacion.createRelative("index.html");
                        return index.exists() && index.isReadable() ? index : null;
                    }
                });
    }
}

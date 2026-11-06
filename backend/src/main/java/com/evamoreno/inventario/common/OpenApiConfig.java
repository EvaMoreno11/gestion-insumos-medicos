package com.evamoreno.inventario.common;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;

/** Datos generales que se muestran en Swagger UI. */
@Configuration
public class OpenApiConfig {

    @Bean
    OpenAPI apiInfo() {
        return new OpenAPI().info(new Info()
                .title("API · Gestión de Insumos Médicos")
                .version("v1")
                .description("Inventario por lotes y pedidos para un negocio de distribución de insumos médicos."));
    }
}

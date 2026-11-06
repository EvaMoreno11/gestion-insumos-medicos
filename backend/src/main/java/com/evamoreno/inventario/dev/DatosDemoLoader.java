package com.evamoreno.inventario.dev;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import com.evamoreno.inventario.categoria.CategoriaRepository;
import com.evamoreno.inventario.producto.ProductoRepository;
import com.evamoreno.inventario.producto.ProductoRequest;
import com.evamoreno.inventario.producto.ProductoService;

/**
 * Carga productos de DEMOSTRACIÓN (marcas ficticias) para desarrollar y probar.
 * Solo corre con el perfil "dev" (al ejecutar con mvnw spring-boot:run) y solo si no hay productos.
 * Nunca se ejecuta en producción.
 *
 * Los códigos de barras usan el prefijo 20–29, que GS1 reserva para uso interno:
 * no coinciden con productos reales.
 */
@Component
@Profile("dev")
class DatosDemoLoader implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DatosDemoLoader.class);

    private record Demo(String categoria, String nombre, String marca, String presentacion, String unidad,
            int stockMinimo, List<String> codigosBarras) {
    }

    private static final List<Demo> PRODUCTOS = List.of(
            new Demo("Jeringas", "Jeringa descartable 1 ml con aguja 27G x 1/2", "Marca A", "Caja x 100", "Caja", 3, List.of("2000000000015")),
            new Demo("Jeringas", "Jeringa descartable 3 ml con aguja 23G x 1", "Marca A", "Caja x 100", "Caja", 5, List.of("2000000000022")),
            new Demo("Jeringas", "Jeringa descartable 5 ml con aguja 21G x 1 1/2", "Marca A", "Caja x 100", "Caja", 5, List.of("2000000000039")),
            new Demo("Jeringas", "Jeringa descartable 5 ml con aguja 21G x 1 1/2", "Marca B", "Caja x 100", "Caja", 5, List.of()),
            new Demo("Jeringas", "Jeringa descartable 10 ml con aguja 21G x 1 1/2", "Marca A", "Caja x 100", "Caja", 3, List.of("2000000000046")),
            new Demo("Jeringas", "Jeringa descartable 20 ml con aguja 21G x 1 1/2", "Marca B", "Caja x 50", "Caja", 2, List.of()),
            new Demo("Agujas", "Aguja hipodérmica 21G x 1 1/2", "Marca A", "Caja x 100", "Caja", 3, List.of("2000000000053")),
            new Demo("Agujas", "Aguja hipodérmica 23G x 1", "Marca A", "Caja x 100", "Caja", 3, List.of()),
            new Demo("Agujas", "Aguja hipodérmica 25G x 5/8", "Marca C", "Caja x 100", "Caja", 2, List.of()),
            new Demo("Guantes", "Guantes de látex para examen talla S", "Marca B", "Caja x 100", "Caja", 10, List.of("2000000000060")),
            new Demo("Guantes", "Guantes de látex para examen talla M", "Marca B", "Caja x 100", "Caja", 10, List.of("2000000000077")),
            new Demo("Guantes", "Guantes de nitrilo para examen talla M", "Marca C", "Caja x 100", "Caja", 8, List.of()),
            new Demo("Guantes", "Guantes quirúrgicos estériles talla 7 1/2", "Marca C", "Caja x 50 pares", "Caja", 4, List.of()),
            new Demo("Gasas y apósitos", "Gasa estéril 10 x 10 cm", "Marca D", "Paquete x 10", "Paquete", 15, List.of("2000000000084")),
            new Demo("Gasas y apósitos", "Gasa estéril 5 x 5 cm", "Marca D", "Paquete x 10", "Paquete", 15, List.of()),
            new Demo("Gasas y apósitos", "Apósito transparente 6 x 7 cm", "Marca A", "Caja x 50", "Caja", 3, List.of()),
            new Demo("Algodón", "Algodón hidrófilo", "Marca D", "Rollo x 500 g", "Rollo", 5, List.of()),
            new Demo("Esparadrapos y vendas", "Esparadrapo de tela 2.5 cm x 5 m", "Marca B", "Unidad", "Unidad", 12, List.of()),
            new Demo("Esparadrapos y vendas", "Venda elástica 4 x 5 yardas", "Marca D", "Unidad", "Unidad", 10, List.of()),
            new Demo("Equipos de venoclisis", "Equipo de venoclisis macrogotero", "Marca C", "Unidad", "Unidad", 20, List.of()),
            new Demo("Catéteres y sondas", "Catéter endovenoso periférico 22G", "Marca A", "Caja x 50", "Caja", 2, List.of()),
            new Demo("Catéteres y sondas", "Sonda Foley 2 vías 16 Fr", "Marca C", "Unidad", "Unidad", 10, List.of()),
            new Demo("Mascarillas y protección", "Mascarilla quirúrgica descartable 3 pliegues", "Marca B", "Caja x 50", "Caja", 6, List.of()),
            new Demo("Antisépticos", "Alcohol etílico 70°", "Marca D", "Frasco x 1 L", "Frasco", 6, List.of()));

    private final ProductoService productoService;
    private final ProductoRepository productoRepository;
    private final CategoriaRepository categoriaRepository;

    DatosDemoLoader(ProductoService productoService, ProductoRepository productoRepository,
            CategoriaRepository categoriaRepository) {
        this.productoService = productoService;
        this.productoRepository = productoRepository;
        this.categoriaRepository = categoriaRepository;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (productoRepository.count() > 0) {
            return;
        }
        for (Demo d : PRODUCTOS) {
            Long categoriaId = categoriaRepository.findByNombreIgnoreCase(d.categoria()).orElseThrow().getId();
            productoService.crear(new ProductoRequest(null, d.nombre(), d.marca(), categoriaId, d.presentacion(),
                    d.unidad(), d.stockMinimo(), d.codigosBarras()));
        }
        log.info("Perfil dev: se cargaron {} productos de demostración", PRODUCTOS.size());
    }
}

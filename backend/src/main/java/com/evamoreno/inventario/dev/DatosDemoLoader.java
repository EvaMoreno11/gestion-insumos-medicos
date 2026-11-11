package com.evamoreno.inventario.dev;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import com.evamoreno.inventario.categoria.CategoriaRepository;
import com.evamoreno.inventario.cliente.Cliente;
import com.evamoreno.inventario.cliente.ClienteDto;
import com.evamoreno.inventario.cliente.ClienteRepository;
import com.evamoreno.inventario.cliente.ClienteService;
import com.evamoreno.inventario.pedido.PedidoDto;
import com.evamoreno.inventario.pedido.PedidoRepository;
import com.evamoreno.inventario.pedido.PedidoService;
import com.evamoreno.inventario.producto.Producto;
import com.evamoreno.inventario.producto.ProductoRepository;
import com.evamoreno.inventario.producto.ProductoRequest;
import com.evamoreno.inventario.producto.ProductoService;
import com.evamoreno.inventario.stock.EntradaRequest;
import com.evamoreno.inventario.stock.LoteRepository;
import com.evamoreno.inventario.stock.MovimientoService;
import com.evamoreno.inventario.stock.StockService;

/**
 * Carga productos, lotes, clientes y pedidos de DEMOSTRACIÓN (nombres ficticios) para desarrollar y probar.
 * Solo corre con el perfil "dev" (al ejecutar con mvnw spring-boot:run) y solo si las tablas están vacías.
 * Nunca se ejecuta en producción.
 *
 * <p>Los lotes cubren todos los casos: stock normal, bajo, agotado, por vencer y vencido.
 * Los códigos de barras usan el prefijo 20–29, que GS1 reserva para uso interno.</p>
 */
@Component
@Profile("dev")
class DatosDemoLoader implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DatosDemoLoader.class);

    /** @param diasParaVencer días desde hoy (negativo = ya vencido; null = no vence) */
    private record LoteDemo(String numero, Integer diasParaVencer, int cantidad) {
    }

    private record Demo(String categoria, String nombre, String marca, String presentacion, String unidad,
            int stockMinimo, List<String> codigosBarras, List<LoteDemo> lotes) {
    }

    private static LoteDemo lote(String numero, Integer dias, int cantidad) {
        return new LoteDemo(numero, dias, cantidad);
    }

    private static final List<Demo> PRODUCTOS = List.of(
            new Demo("Jeringas", "Jeringa descartable 1 ml con aguja 27G x 1/2", "Marca A", "Caja x 100", "Caja", 3, List.of("2000000000015"),
                    List.of(lote("J1-2401", 400, 6))),
            new Demo("Jeringas", "Jeringa descartable 3 ml con aguja 23G x 1", "Marca A", "Caja x 100", "Caja", 5, List.of("2000000000022"),
                    List.of(lote("J3-2402", 300, 4))), // stock bajo
            new Demo("Jeringas", "Jeringa descartable 5 ml con aguja 21G x 1 1/2", "Marca A", "Caja x 100", "Caja", 5, List.of("2000000000039"),
                    List.of(lote("J5-2311", 45, 4), lote("J5-2405", 500, 10))), // un lote por vencer
            new Demo("Jeringas", "Jeringa descartable 5 ml con aguja 21G x 1 1/2", "Marca B", "Caja x 100", "Caja", 5, List.of(),
                    List.of(lote("B5-0098", 600, 15))),
            new Demo("Jeringas", "Jeringa descartable 10 ml con aguja 21G x 1 1/2", "Marca A", "Caja x 100", "Caja", 3, List.of("2000000000046"),
                    List.of(lote("J10-2301", -20, 2), lote("J10-2404", 380, 7))), // un lote vencido
            new Demo("Jeringas", "Jeringa descartable 20 ml con aguja 21G x 1 1/2", "Marca B", "Caja x 50", "Caja", 2, List.of(),
                    List.of()), // agotado
            new Demo("Agujas", "Aguja hipodérmica 21G x 1 1/2", "Marca A", "Caja x 100", "Caja", 3, List.of("2000000000053"),
                    List.of(lote("A21-2403", 700, 9))),
            new Demo("Agujas", "Aguja hipodérmica 23G x 1", "Marca A", "Caja x 100", "Caja", 3, List.of(),
                    List.of(lote("A23-2310", 25, 5))), // por vencer
            new Demo("Agujas", "Aguja hipodérmica 25G x 5/8", "Marca C", "Caja x 100", "Caja", 2, List.of(),
                    List.of(lote("C25-771", 800, 4))),
            new Demo("Guantes", "Guantes de látex para examen talla S", "Marca B", "Caja x 100", "Caja", 10, List.of("2000000000060"),
                    List.of(lote("GS-5520", 540, 25))),
            new Demo("Guantes", "Guantes de látex para examen talla M", "Marca B", "Caja x 100", "Caja", 10, List.of("2000000000077"),
                    List.of(lote("GM-5521", 200, 12), lote("GM-5602", 560, 30))),
            new Demo("Guantes", "Guantes de nitrilo para examen talla M", "Marca C", "Caja x 100", "Caja", 8, List.of(),
                    List.of(lote("NM-0113", 650, 6))), // stock bajo
            new Demo("Guantes", "Guantes quirúrgicos estériles talla 7 1/2", "Marca C", "Caja x 50 pares", "Caja", 4, List.of(),
                    List.of(lote("Q75-889", 900, 8))),
            new Demo("Gasas y apósitos", "Gasa estéril 10 x 10 cm", "Marca D", "Paquete x 10", "Paquete", 15, List.of("2000000000084"),
                    List.of(lote("G10-3301", 15, 20), lote("G10-3388", 420, 40))),
            new Demo("Gasas y apósitos", "Gasa estéril 5 x 5 cm", "Marca D", "Paquete x 10", "Paquete", 15, List.of(),
                    List.of(lote("G5-3290", -5, 10))), // todo vencido: disponible 0
            new Demo("Gasas y apósitos", "Apósito transparente 6 x 7 cm", "Marca A", "Caja x 50", "Caja", 3, List.of(),
                    List.of(lote("AP-1002", 730, 5))),
            new Demo("Algodón", "Algodón hidrófilo", "Marca D", "Rollo x 500 g", "Rollo", 5, List.of(),
                    List.of(lote(null, null, 18))), // sin lote ni vencimiento
            new Demo("Esparadrapos y vendas", "Esparadrapo de tela 2.5 cm x 5 m", "Marca B", "Unidad", "Unidad", 12, List.of(),
                    List.of(lote("E25-441", 1000, 40))),
            new Demo("Esparadrapos y vendas", "Venda elástica 4 x 5 yardas", "Marca D", "Unidad", "Unidad", 10, List.of(),
                    List.of(lote(null, null, 8))),
            new Demo("Equipos de venoclisis", "Equipo de venoclisis macrogotero", "Marca C", "Unidad", "Unidad", 20, List.of(),
                    List.of(lote("V-77812", 360, 60))),
            new Demo("Catéteres y sondas", "Catéter endovenoso periférico 22G", "Marca A", "Caja x 50", "Caja", 2, List.of(),
                    List.of(lote("CT22-019", 50, 3))),
            new Demo("Catéteres y sondas", "Sonda Foley 2 vías 16 Fr", "Marca C", "Unidad", "Unidad", 10, List.of(),
                    List.of()), // agotado
            new Demo("Mascarillas y protección", "Mascarilla quirúrgica descartable 3 pliegues", "Marca B", "Caja x 50", "Caja", 6, List.of(),
                    List.of(lote("M3-2207", 480, 22))),
            new Demo("Antisépticos", "Alcohol etílico 70°", "Marca D", "Frasco x 1 L", "Frasco", 6, List.of(),
                    List.of(lote("AL-0925", 330, 14))));

    private final ProductoService productoService;
    private final ProductoRepository productoRepository;
    private final CategoriaRepository categoriaRepository;
    private final MovimientoService movimientoService;
    private final LoteRepository loteRepository;
    private final StockService stock;
    private final ClienteService clienteService;
    private final ClienteRepository clienteRepository;
    private final PedidoService pedidoService;
    private final PedidoRepository pedidoRepository;

    DatosDemoLoader(ProductoService productoService, ProductoRepository productoRepository,
            CategoriaRepository categoriaRepository, MovimientoService movimientoService,
            LoteRepository loteRepository, StockService stock, ClienteService clienteService,
            ClienteRepository clienteRepository, PedidoService pedidoService,
            PedidoRepository pedidoRepository) {
        this.productoService = productoService;
        this.productoRepository = productoRepository;
        this.categoriaRepository = categoriaRepository;
        this.movimientoService = movimientoService;
        this.loteRepository = loteRepository;
        this.stock = stock;
        this.clienteService = clienteService;
        this.clienteRepository = clienteRepository;
        this.pedidoService = pedidoService;
        this.pedidoRepository = pedidoRepository;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (productoRepository.count() == 0) {
            for (int i = 0; i < PRODUCTOS.size(); i++) {
                Demo d = PRODUCTOS.get(i);
                Long categoriaId = categoriaRepository.findByNombreIgnoreCase(d.categoria()).orElseThrow().getId();
                productoService.crear(new ProductoRequest(null, d.nombre(), d.marca(), categoriaId, d.presentacion(),
                        d.unidad(), d.stockMinimo(), costoDemo(i), d.codigosBarras()));
            }
            log.info("Perfil dev: se cargaron {} productos de demostración", PRODUCTOS.size());
        }
        // Bases locales creadas antes de que existiera el costo: completa los costos de demostración
        for (int i = 0; i < PRODUCTOS.size(); i++) {
            Producto producto = buscar(PRODUCTOS.get(i));
            if (producto != null && producto.getCosto() == null) {
                productoService.actualizarCosto(producto.getId(), costoDemo(i));
            }
        }
        if (loteRepository.count() == 0) {
            LocalDate hoy = stock.hoy();
            int cargados = 0;
            for (Demo d : PRODUCTOS) {
                Producto producto = buscar(d);
                if (producto == null) {
                    continue;
                }
                for (LoteDemo l : d.lotes()) {
                    // El vencimiento se guarda por mes: los "vencidos" van al mes anterior
                    LocalDate vence = l.diasParaVencer() == null ? null
                            : l.diasParaVencer() < 0 ? hoy.minusMonths(1) : hoy.plusDays(l.diasParaVencer());
                    movimientoService.registrarEntrada(new EntradaRequest(producto.getId(), l.numero(), vence,
                            l.cantidad(), "Datos de demostración"));
                    cargados++;
                }
            }
            log.info("Perfil dev: se cargaron {} lotes de demostración", cargados);
        }
        if (clienteRepository.count() == 0) {
            clienteService.crear(new ClienteDto.Request("Botica Demo S.A.C.", "20123456789", new BigDecimal("15"), null,
                    List.of(new ClienteDto.LocalRequest(null, "Local 1", "Av. Los Olivos 123, Los Olivos", "Dra. Ana",
                                    "987654321"),
                            new ClienteDto.LocalRequest(null, "Local 2", "Jr. Las Flores 456, Comas", "Sr. Luis",
                                    "912345678"))));
            clienteService.crear(new ClienteDto.Request("Clínica Ejemplo", "20987654321", new BigDecimal("10"), null,
                    List.of(new ClienteDto.LocalRequest(null, null, "Av. Central 789, Lima", "Srta. Rosa", null))));
            clienteService.crear(new ClienteDto.Request("Consultorio Dr. Pérez", null, null,
                    "Sin porcentaje: la app lo pedirá en su primer pedido", null));
            log.info("Perfil dev: se cargaron 3 clientes de demostración");
        }
        if (pedidoRepository.count() == 0) {
            cargarPedidos();
        }
    }

    /** Dos pedidos pendientes: uno con un faltante y un ítem a mano, para probar todos los casos. */
    private void cargarPedidos() {
        Cliente botica = clienteRepository.findByClaveUnica(Cliente.calcularClaveUnica("Botica Demo S.A.C."))
                .flatMap(c -> clienteRepository.findWithLocalesById(c.getId())).orElse(null);
        Cliente clinica = clienteRepository.findByClaveUnica(Cliente.calcularClaveUnica("Clínica Ejemplo"))
                .orElse(null);
        if (botica == null || clinica == null || botica.getLocales().isEmpty()) {
            return;
        }
        Long pedidoBotica = pedidoService.crear(new PedidoDto.CrearRequest(botica.getId(),
                botica.getLocales().getFirst().getId(), null, null, "Pedido de demostración")).id();
        agregar(pedidoBotica, 2, 6);   // Jeringa 5 ml Marca A
        agregar(pedidoBotica, 10, 4);  // Guantes de látex talla M
        agregar(pedidoBotica, 5, 2);   // Jeringa 20 ml: agotada, queda como faltante
        pedidoService.agregarItem(pedidoBotica, new PedidoDto.ItemRequest(null, "Tensiómetro digital de brazo",
                "Unidad", "Marca Z", 1, new BigDecimal("85.00")));

        Long pedidoClinica = pedidoService.crear(
                new PedidoDto.CrearRequest(clinica.getId(), null, null, null, null)).id();
        agregar(pedidoClinica, 13, 30); // Gasa estéril 10 x 10 cm
        log.info("Perfil dev: se cargaron 2 pedidos de demostración");
    }

    private void agregar(Long pedidoId, int indiceProducto, int cantidad) {
        Producto producto = buscar(PRODUCTOS.get(indiceProducto));
        if (producto != null && producto.isActivo()) {
            pedidoService.agregarItem(pedidoId,
                    new PedidoDto.ItemRequest(producto.getId(), null, null, null, cantidad, null));
        }
    }

    private Producto buscar(Demo d) {
        return productoRepository
                .findByClaveUnica(Producto.calcularClaveUnica(d.nombre(), d.marca(), d.presentacion()))
                .orElse(null);
    }

    /** Costos inventados: 2.50, 3.85, 5.20... */
    private static BigDecimal costoDemo(int indice) {
        return BigDecimal.valueOf(250 + 135L * indice, 2);
    }
}

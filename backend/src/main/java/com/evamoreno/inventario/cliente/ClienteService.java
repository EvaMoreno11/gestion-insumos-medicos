package com.evamoreno.inventario.cliente;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.evamoreno.inventario.cliente.ClienteDto.Detalle;
import com.evamoreno.inventario.cliente.ClienteDto.LocalEncontrado;
import com.evamoreno.inventario.cliente.ClienteDto.LocalRequest;
import com.evamoreno.inventario.cliente.ClienteDto.Request;
import com.evamoreno.inventario.cliente.ClienteDto.Resumen;
import com.evamoreno.inventario.common.ConflictoException;
import com.evamoreno.inventario.common.Pagina;
import com.evamoreno.inventario.common.RecursoNoEncontradoException;
import com.evamoreno.inventario.common.TextoBusqueda;
import com.evamoreno.inventario.precio.CategoriaPrecioService;

import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;

@Service
@Transactional
public class ClienteService {

    private static final int TAMANO_MAXIMO_PAGINA = 100;
    private static final int MAX_LOCALES_ENCONTRADOS = 3;

    private final ClienteRepository clientes;
    private final CategoriaPrecioService categoriasPrecio;

    public ClienteService(ClienteRepository clientes, CategoriaPrecioService categoriasPrecio) {
        this.clientes = clientes;
        this.categoriasPrecio = categoriasPrecio;
    }

    // ------------------------------------------------------------------ consultas

    /**
     * Busca por nombre o RUC/DNI del cliente, o por el nombre o el contacto de alguno de sus locales.
     * Cada palabra debe coincidir con el inicio de una palabra de esos datos.
     */
    @Transactional(readOnly = true)
    public Pagina<Resumen> buscar(String buscar, boolean incluirInactivos, int pagina, int tamano) {
        List<String> tokens = TextoBusqueda.tokens(buscar);
        Specification<Cliente> filtro = (root, query, cb) -> {
            List<Predicate> condiciones = new ArrayList<>();
            for (String token : tokens) {
                String patron = "% " + token + "%";
                Subquery<Long> enLocales = query.subquery(Long.class);
                Root<ClienteLocal> local = enLocales.from(ClienteLocal.class);
                enLocales.select(local.get("id")).where(
                        cb.equal(local.get("cliente"), root),
                        cb.like(local.get("textoBusqueda"), patron));
                condiciones.add(cb.or(cb.like(root.get("textoBusqueda"), patron), cb.exists(enLocales)));
            }
            if (!incluirInactivos) {
                condiciones.add(cb.isTrue(root.get("activo")));
            }
            return cb.and(condiciones.toArray(Predicate[]::new));
        };
        PageRequest pageRequest = PageRequest.of(Math.max(pagina, 0), Math.clamp(tamano, 1, TAMANO_MAXIMO_PAGINA),
                Sort.by("nombre"));
        Map<BigDecimal, String> letras = categoriasPrecio.letrasPorPorcentaje();
        return Pagina.de(clientes.findAll(filtro, pageRequest),
                c -> Resumen.de(c, letras, localesEncontrados(c, tokens)));
    }

    /** Locales del cliente cuyo nombre o contacto contiene alguna de las palabras buscadas. */
    private static List<LocalEncontrado> localesEncontrados(Cliente cliente, List<String> tokens) {
        if (tokens.isEmpty()) {
            return List.of();
        }
        return cliente.getLocales().stream()
                .filter(local -> tokens.stream().anyMatch(local::coincideCon))
                .limit(MAX_LOCALES_ENCONTRADOS)
                .map(LocalEncontrado::de)
                .toList();
    }

    @Transactional(readOnly = true)
    public Detalle obtener(Long id) {
        return detalle(buscarEntidad(id));
    }

    @Transactional(readOnly = true)
    public boolean existeConNombre(String nombre) {
        return clientes.findByClaveUnica(Cliente.calcularClaveUnica(nombre)).isPresent();
    }

    // ------------------------------------------------------------------ cambios

    public Detalle crear(Request request) {
        String nombre = TextoBusqueda.limpiar(request.nombre());
        String rucDni = limpiarOpcional(request.rucDni());
        validarUnico(nombre, rucDni, null);

        Cliente cliente = new Cliente(nombre, rucDni, request.porcentaje(), limpiarOpcional(request.observaciones()));
        List<LocalRequest> locales = request.locales() == null ? List.of() : request.locales();
        validarNombresDeLocales(locales);
        if (locales.isEmpty()) {
            cliente.agregarLocal(null, null, null, null); // todo cliente tiene al menos un punto de entrega
        }
        for (LocalRequest l : locales) {
            cliente.agregarLocal(limpiarOpcional(l.nombre()), limpiarOpcional(l.direccion()),
                    limpiarOpcional(l.contacto()), limpiarOpcional(l.telefono()));
        }
        return detalle(clientes.saveAndFlush(cliente));
    }

    public Detalle actualizar(Long id, Request request) {
        Cliente cliente = buscarEntidad(id);
        String nombre = TextoBusqueda.limpiar(request.nombre());
        String rucDni = limpiarOpcional(request.rucDni());
        validarUnico(nombre, rucDni, id);
        cliente.actualizarDatos(nombre, rucDni, request.porcentaje(), limpiarOpcional(request.observaciones()));

        // Sin lista de locales = no se tocan
        if (request.locales() != null && !request.locales().isEmpty()) {
            sincronizarLocales(cliente, request.locales());
        }
        clientes.flush();
        return detalle(cliente);
    }

    /** Los clientes no se borran (tendrán pedidos): se desactivan. */
    public Detalle cambiarEstado(Long id, boolean activo) {
        Cliente cliente = buscarEntidad(id);
        cliente.setActivo(activo);
        clientes.flush();
        return detalle(cliente);
    }

    // ------------------------------------------------------------------ reglas

    private void sincronizarLocales(Cliente cliente, List<LocalRequest> deseados) {
        validarNombresDeLocales(deseados);
        Map<Long, ClienteLocal> actuales = cliente.getLocales().stream()
                .collect(Collectors.toMap(ClienteLocal::getId, Function.identity()));
        Set<Long> conservados = new HashSet<>();
        for (LocalRequest l : deseados) {
            String nombre = limpiarOpcional(l.nombre());
            String direccion = limpiarOpcional(l.direccion());
            String contacto = limpiarOpcional(l.contacto());
            String telefono = limpiarOpcional(l.telefono());
            if (l.id() == null) {
                cliente.agregarLocal(nombre, direccion, contacto, telefono);
                continue;
            }
            ClienteLocal existente = actuales.get(l.id());
            if (existente == null) {
                throw new RecursoNoEncontradoException(
                        "El local " + l.id() + " no pertenece al cliente " + cliente.getNombre());
            }
            existente.actualizar(nombre, direccion, contacto, telefono);
            conservados.add(l.id());
        }
        cliente.getLocales().removeIf(local -> local.getId() != null && !conservados.contains(local.getId()));
    }

    /** Dentro de un cliente no se repite el nombre de un local, y solo uno puede quedar sin nombre. */
    private static void validarNombresDeLocales(List<LocalRequest> locales) {
        Set<String> vistos = new HashSet<>();
        for (LocalRequest l : locales) {
            String clave = TextoBusqueda.normalizar(l.nombre());
            if (!vistos.add(clave)) {
                throw new ConflictoException(clave.isEmpty()
                        ? "Si el cliente tiene varios locales, cada uno debe tener un nombre"
                        : "El local \"" + TextoBusqueda.limpiar(l.nombre()) + "\" está repetido");
            }
        }
    }

    private void validarUnico(String nombre, String rucDni, Long idActual) {
        clientes.findByClaveUnica(Cliente.calcularClaveUnica(nombre))
                .filter(existente -> !existente.getId().equals(idActual))
                .ifPresent(existente -> {
                    throw new ConflictoException("Ya existe el cliente \"" + existente.getNombre() + "\"");
                });
        if (rucDni != null) {
            clientes.findByRucDni(rucDni)
                    .filter(existente -> !existente.getId().equals(idActual))
                    .ifPresent(existente -> {
                        throw new ConflictoException("El RUC/DNI " + rucDni + " ya pertenece al cliente \""
                                + existente.getNombre() + "\"");
                    });
        }
    }

    private Cliente buscarEntidad(Long id) {
        return clientes.findWithLocalesById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("No existe el cliente con id " + id));
    }

    private Detalle detalle(Cliente cliente) {
        return Detalle.de(cliente, categoriasPrecio.letrasPorPorcentaje());
    }

    private static String limpiarOpcional(String texto) {
        return texto == null || texto.isBlank() ? null : TextoBusqueda.limpiar(texto);
    }
}

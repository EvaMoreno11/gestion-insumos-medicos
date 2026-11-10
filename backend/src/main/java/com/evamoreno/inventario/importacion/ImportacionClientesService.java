package com.evamoreno.inventario.importacion;

import java.io.InputStream;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import com.evamoreno.inventario.cliente.Cliente;
import com.evamoreno.inventario.cliente.ClienteDto;
import com.evamoreno.inventario.cliente.ClienteService;
import com.evamoreno.inventario.common.ConflictoException;
import com.evamoreno.inventario.common.TextoBusqueda;
import com.evamoreno.inventario.importacion.LectorExcelClientes.Fila;
import com.evamoreno.inventario.importacion.ResultadoImportacion.Observacion;

/**
 * Importa clientes desde la plantilla de Excel (una fila por local). Las filas con el mismo nombre
 * forman un solo cliente con varios locales.
 *
 * <p>Igual que la importación de inventario: una sola transacción, todo o nada, y con
 * {@code soloValidar} se ejecuta lo mismo y al final se deshace.</p>
 */
@Service
public class ImportacionClientesService {

    private static final Logger log = LoggerFactory.getLogger(ImportacionClientesService.class);

    private final TransactionTemplate transaccion;
    private final ClienteService clienteService;

    public ImportacionClientesService(TransactionTemplate transaccion, ClienteService clienteService) {
        this.transaccion = transaccion;
        this.clienteService = clienteService;
    }

    public ResultadoImportacionClientes importar(InputStream archivo, boolean soloValidar) {
        LectorExcelClientes.Lectura lectura = LectorExcelClientes.leer(archivo);
        if (lectura.filas().isEmpty() && lectura.errores().isEmpty()) {
            return new ResultadoImportacionClientes(false, soloValidar, 0, 0, 0, 0,
                    List.of(new Observacion(0, "La hoja \"" + LectorExcelClientes.HOJA + "\" no tiene filas con datos")),
                    List.of());
        }
        return transaccion.execute(status -> {
            List<Observacion> errores = new ArrayList<>(lectura.errores());
            List<Observacion> advertencias = new ArrayList<>();
            int creados = 0;
            int locales = 0;
            int existentes = 0;

            for (List<Fila> grupo : agruparPorCliente(lectura.filas()).values()) {
                Fila primera = grupo.getFirst();
                try {
                    if (clienteService.existeConNombre(primera.nombre())) {
                        existentes++;
                        advertencias.add(new Observacion(primera.numero(), "El cliente \"" + primera.nombre()
                                + "\" ya está registrado; no se modificó. Edítalo desde la app si hace falta."));
                        continue;
                    }
                    clienteService.crear(armarCliente(grupo));
                    creados++;
                    locales += grupo.size();
                } catch (ErrorDeFila e) {
                    errores.add(new Observacion(e.fila, e.getMessage()));
                } catch (ConflictoException e) {
                    errores.add(new Observacion(primera.numero(), e.getMessage()));
                } catch (DataAccessException e) {
                    log.warn("Error de base de datos importando el cliente de la fila {}", primera.numero(), e);
                    errores.add(new Observacion(primera.numero(),
                            "Error al guardar este cliente; se detuvo la importación. Revisa los datos."));
                    break;
                }
            }

            boolean guardar = !soloValidar && errores.isEmpty();
            if (!guardar) {
                status.setRollbackOnly();
            }
            int filasLeidas = lectura.filas().size() + lectura.errores().size();
            return new ResultadoImportacionClientes(guardar, soloValidar, filasLeidas, creados, locales, existentes,
                    List.copyOf(errores), List.copyOf(advertencias));
        });
    }

    /** Agrupa las filas por nombre de cliente (sin importar tildes ni mayúsculas), en el orden del archivo. */
    private static Map<String, List<Fila>> agruparPorCliente(List<Fila> filas) {
        Map<String, List<Fila>> grupos = new LinkedHashMap<>();
        for (Fila fila : filas) {
            grupos.computeIfAbsent(Cliente.calcularClaveUnica(fila.nombre()), k -> new ArrayList<>()).add(fila);
        }
        return grupos;
    }

    private static ClienteDto.Request armarCliente(List<Fila> grupo) {
        Fila primera = grupo.getFirst();
        String rucDni = null;
        BigDecimal porcentaje = null;
        String observaciones = null;
        List<ClienteDto.LocalRequest> locales = new ArrayList<>();

        for (Fila fila : grupo) {
            // RUC y porcentaje son del cliente: pueden venir en una sola de sus filas, pero no con valores distintos
            if (fila.rucDni() != null) {
                if (rucDni != null && !rucDni.equals(fila.rucDni())) {
                    throw new ErrorDeFila(fila.numero(), "RUC o DNI: el cliente \"" + primera.nombre()
                            + "\" tiene otro (" + rucDni + ") en otra fila");
                }
                rucDni = fila.rucDni();
            }
            if (fila.porcentaje() != null) {
                if (porcentaje != null && porcentaje.compareTo(fila.porcentaje()) != 0) {
                    throw new ErrorDeFila(fila.numero(), "Porcentaje (%): el cliente \"" + primera.nombre()
                            + "\" tiene otro (" + porcentaje.stripTrailingZeros().toPlainString()
                            + ") en otra fila; es uno solo por cliente");
                }
                porcentaje = fila.porcentaje();
            }
            if (grupo.size() > 1 && fila.local() == null) {
                throw new ErrorDeFila(fila.numero(), "Local: el cliente \"" + primera.nombre()
                        + "\" aparece en varias filas; indica el nombre de cada local");
            }
            if (grupo.stream().filter(otra -> otra.numero() < fila.numero()).anyMatch(otra -> Objects.equals(
                    TextoBusqueda.normalizar(otra.local()), TextoBusqueda.normalizar(fila.local())))) {
                throw new ErrorDeFila(fila.numero(), "Local: \"" + fila.local() + "\" está repetido para el cliente \""
                        + primera.nombre() + "\"");
            }
            if (observaciones == null) {
                observaciones = fila.observaciones();
            }
            locales.add(new ClienteDto.LocalRequest(null, fila.local(), fila.direccion(), fila.contacto(),
                    fila.telefono()));
        }
        return new ClienteDto.Request(primera.nombre(), rucDni, porcentaje, observaciones, locales);
    }

    /** Error atribuible a una fila concreta del Excel. */
    private static final class ErrorDeFila extends RuntimeException {
        private final int fila;

        ErrorDeFila(int fila, String mensaje) {
            super(mensaje);
            this.fila = fila;
        }
    }
}

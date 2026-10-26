package com.smms.assistance.infrastructure.config;

import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;
import javax.sql.DataSource;
import java.sql.Connection;

@ApplicationScoped
public class DatabaseConnectionChecker {
    private static final Logger LOG = Logger.getLogger(DatabaseConnectionChecker.class);

    @Inject
    DataSource dataSource;

    void onStart(@Observes StartupEvent ev) {
        long start = System.currentTimeMillis();
        try (Connection conn = dataSource.getConnection()) {
            boolean isValid = conn.isValid(5);
            long time = System.currentTimeMillis() - start;
            if (isValid) {
                LOG.info("[DB PERF] Conectado exitosamente a la Base de Datos (Supabase/PostgreSQL) en " + time + " ms.");
            } else {
                LOG.warn("[DB] La conexi\u00f3n a la base de datos no es v\u00e1lida.");
            }
        } catch (Exception e) {
            LOG.error("[DB] Error al conectar con la base de datos durante el arranque: " + e.getMessage());
        }
    }
}

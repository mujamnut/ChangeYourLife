package com.changeyourlife.cyl.backend.plugins

import com.changeyourlife.cyl.backend.config.DatabaseConfig
import com.changeyourlife.cyl.backend.database.DatabaseFactory
import com.zaxxer.hikari.HikariDataSource
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationStopping

fun Application.configureDatabase(config: DatabaseConfig): HikariDataSource? {
    if (!config.isConfigured) {
        environment.log.warn("DATABASE_URL is not configured. Backend will use in-memory repositories.")
        return null
    }

    val dataSource = DatabaseFactory.createDataSource(config)
    DatabaseFactory.migrate(dataSource)

    monitor.subscribe(ApplicationStopping) {
        dataSource.close()
    }

    environment.log.info("PostgreSQL connection pool initialized.")
    return dataSource
}


package com.changeyourlife.cyl.backend.database

import com.changeyourlife.cyl.backend.config.DatabaseConfig
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.flywaydb.core.Flyway

object DatabaseFactory {
    fun createDataSource(config: DatabaseConfig): HikariDataSource {
        require(config.isConfigured) {
            "DATABASE_URL must be configured before creating the PostgreSQL data source."
        }

        val hikariConfig = HikariConfig().apply {
            jdbcUrl = config.jdbcUrl
            username = config.username
            password = config.password
            driverClassName = "org.postgresql.Driver"
            maximumPoolSize = config.maxPoolSize
            poolName = "cyl-postgres"
            validate()
        }

        return HikariDataSource(hikariConfig)
    }

    fun migrate(dataSource: HikariDataSource) {
        Flyway.configure()
            .dataSource(dataSource)
            .locations("classpath:db/migration")
            .baselineOnMigrate(true)
            .load()
            .migrate()
    }
}


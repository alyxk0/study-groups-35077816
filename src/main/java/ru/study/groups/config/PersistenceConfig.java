package ru.study.groups.config;

import jakarta.persistence.EntityManagerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.EclipseLinkJpaVendorAdapter;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import javax.sql.DataSource;
import java.util.Map;

@Configuration
@EnableTransactionManagement
public class PersistenceConfig {
    @Bean
    @DependsOn("flywayInitializer")
    LocalContainerEntityManagerFactoryBean entityManagerFactory(DataSource dataSource,
            @Value("${app.persistence.mapping-resource:}") String mappingResource) {
        var factory = new LocalContainerEntityManagerFactoryBean();
        factory.setDataSource(dataSource);
        factory.setPackagesToScan("ru.study.groups.domain");
        factory.setJpaVendorAdapter(new EclipseLinkJpaVendorAdapter());
        if (!mappingResource.isBlank()) factory.setMappingResources(mappingResource);
        factory.setJpaPropertyMap(Map.of(
                "eclipselink.weaving", "false",
                "eclipselink.cache.shared.default", "false",
                "eclipselink.target-database", "org.eclipse.persistence.platform.database.PostgreSQLPlatform",
                "eclipselink.logging.level", "WARNING",
                "jakarta.persistence.validation.mode", "AUTO"));
        return factory;
    }
    @Bean JpaTransactionManager transactionManager(EntityManagerFactory factory) {
        return new JpaTransactionManager(factory);
    }
}

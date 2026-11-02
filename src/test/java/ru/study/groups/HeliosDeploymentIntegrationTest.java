package ru.study.groups;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import ru.study.groups.domain.*;
import ru.study.groups.service.*;
import ru.study.groups.web.Requests.*;
import java.time.ZonedDateTime;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest @ActiveProfiles("helios") @Testcontainers
class HeliosDeploymentIntegrationTest {
    @Container static final PostgreSQLContainer<?> DB = new PostgreSQLContainer<>("postgres:17-alpine")
        .withInitScript("db/legacy-fixture.sql");
    @DynamicPropertySource static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> DB.getJdbcUrl()
            + (DB.getJdbcUrl().contains("?") ? "&" : "?") + "currentSchema=s466287");
        registry.add("spring.datasource.username", DB::getUsername);
        registry.add("spring.datasource.password", DB::getPassword);
    }
    @Autowired ReferenceService references;
    @Autowired GroupService groups;
    @Autowired AccountService accounts;
    @Autowired JdbcTemplate jdbc;

    @Test void deploymentUsesOnlyPrefixedTablesAndSequence() {
        var coordinates=references.coordinates(null,new CoordinatesInput(null,10L,20.0));
        var location=references.location(null,new LocationInput(null,1.0,2.0f,3L));
        var admin=references.person(null,new PersonInput(null,"Admin",null,null,location.getId(),ZonedDateTime.now(),Country.GERMANY));
        var group=groups.create(new GroupInput(null,"P3313",coordinates.getId(),10L,1,1,FormOfEducation.FULL_TIME_EDUCATION,1,4.5,Semester.THIRD,admin.getId()));
        accounts.register(new Registration("helios_test","test_password123"));
        assertThat(groups.get(group.getId()).getGroupAdmin().getLocation().getId()).isEqualTo(location.getId());
        assertThat(groups.addStudent(group.getId()).getStudentsCount()).isEqualTo(11);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM sg35077816_study_group",Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM sg35077816_app_user",Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT legacy_value FROM coordinates",String.class)).isEqualTo("existing data");
        assertThat(jdbc.queryForObject("SELECT legacy_value FROM person",String.class)).isEqualTo("existing person");
        assertThat(jdbc.queryForObject("SELECT legacy_value FROM location",String.class)).isEqualTo("existing location");
        assertThat(jdbc.queryForObject("SELECT is_called FROM object_seq",Boolean.class)).isFalse();
        assertThat(jdbc.queryForObject("SELECT last_value FROM object_seq",Long.class)).isEqualTo(9000);
        groups.delete(group.getId(),groups.get(group.getId()).getVersion());
        references.delete(Person.class,admin.getId(),admin.getVersion());
        references.delete(Location.class,location.getId(),location.getVersion());
        references.delete(Coordinates.class,coordinates.getId(),coordinates.getVersion());
    }
}

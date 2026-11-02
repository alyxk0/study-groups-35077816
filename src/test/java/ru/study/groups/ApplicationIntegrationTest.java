package ru.study.groups;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManagerFactory;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;
import ru.study.groups.domain.*;
import ru.study.groups.service.*;
import ru.study.groups.web.Requests.*;
import java.math.BigInteger;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @AutoConfigureMockMvc @Testcontainers @WithMockUser
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class ApplicationIntegrationTest {
    @Container static final PostgreSQLContainer<?> DB = new PostgreSQLContainer<>("postgres:17-alpine");
    @DynamicPropertySource static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", DB::getJdbcUrl);
        registry.add("spring.datasource.username", DB::getUsername);
        registry.add("spring.datasource.password", DB::getPassword);
    }
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired GroupService groups;
    @Autowired ReferenceService references;
    @Autowired JdbcTemplate jdbc;
    @Autowired EntityManagerFactory factory;
    private Coordinates coordinates;

    @BeforeEach void reset() {
        jdbc.execute("TRUNCATE study_group, person, coordinates, location, app_user CASCADE");
        coordinates = references.coordinates(null, new CoordinatesInput(null, 10L, 20.5));
    }
    private GroupInput input(String name, long students, Semester semester, Long admin, Long version) {
        return new GroupInput(version, name, coordinates.getId(), students, 1, 1,
            FormOfEducation.FULL_TIME_EDUCATION, 2, 4.5, semester, admin);
    }
    private StudyGroup group(String name, long count, Semester semester) { return groups.create(input(name,count,semester,null,null)); }

    @Test void actualProviderIsEclipseLink() {
        assertThat(factory.unwrap(org.eclipse.persistence.jpa.JpaEntityManagerFactory.class)).isNotNull();
    }
    @Test void crudIncludesNestedReferencesAndGeneratedValues() throws Exception {
        var location = references.location(null,new LocationInput(null,1.0,2.0f,3L));
        var person = references.person(null,new PersonInput(null,"Алексей",null,Color.BROWN,location.getId(),ZonedDateTime.parse("2004-03-02T12:00:00+03:00"),Country.GERMANY));
        var result = mvc.perform(post("/api/groups").with(csrf()).contentType("application/json")
            .content(json.writeValueAsBytes(input("P3210",20,Semester.FIRST,person.getId(),null))))
            .andExpect(status().isCreated()).andExpect(jsonPath("$.id").isNumber())
            .andExpect(jsonPath("$.creationDate").isNotEmpty()).andExpect(jsonPath("$.groupAdmin.location.z").value(3)).andReturn();
        long id = json.readTree(result.getResponse().getContentAsString()).get("id").asLong();
        var saved = groups.get(id);
        mvc.perform(put("/api/groups/"+id).with(csrf()).contentType("application/json")
            .content(json.writeValueAsBytes(input("P3211",21,Semester.SECOND,person.getId(),saved.getVersion()))))
            .andExpect(status().isOk()).andExpect(jsonPath("$.name").value("P3211"));
        assertThat(groups.get(id).getCreationDate()).isEqualTo(saved.getCreationDate());
        mvc.perform(delete("/api/groups/"+id).param("version",String.valueOf(groups.get(id).getVersion())).with(csrf())).andExpect(status().isNoContent());
        mvc.perform(get("/api/groups/"+id)).andExpect(status().isNotFound());
    }
    @Test void validationRejectsZeroAndMissingFields() throws Exception {
        var bad = input("",0,Semester.FIRST,null,null);
        mvc.perform(post("/api/groups").with(csrf()).contentType("application/json").content(json.writeValueAsBytes(bad)))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fields.name").exists()).andExpect(jsonPath("$.fields.studentsCount").exists());
        mvc.perform(post("/api/references/people").with(csrf()).contentType("application/json").content("{}"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fields.locationId").exists());
    }
    @Test void databaseEnforcesPositiveValuesAndForeignKeys() {
        var g=group("A",10,Semester.FIRST);
        assertThatThrownBy(()->jdbc.update("UPDATE study_group SET students_count=0 WHERE id=?",g.getId())).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(()->jdbc.update("UPDATE study_group SET average_mark='Infinity' WHERE id=?",g.getId())).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(()->jdbc.update("DELETE FROM coordinates WHERE id=?",coordinates.getId())).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(()->jdbc.update("UPDATE study_group SET name='' WHERE id=?",g.getId())).isInstanceOf(DataIntegrityViolationException.class);
    }
    @Test void ormAlsoRejectsInvalidValuesWithoutController() {
        assertThatThrownBy(()->groups.create(input("A",0,Semester.FIRST,null,null))).isInstanceOf(RuntimeException.class);
        assertThat(groups.page(0,10,"id","asc","",null).total()).isZero();
    }
    @Test void referencedObjectsCannotBeDeleted() throws Exception {
        group("A",10,Semester.FIRST);
        mvc.perform(delete("/api/references/coordinates/"+coordinates.getId()).param("version",String.valueOf(coordinates.getVersion())).with(csrf()))
            .andExpect(status().isConflict()).andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("ссылаются")));
        var location=references.location(null,new LocationInput(null,0.0,0.0f,0L));
        var person=references.person(null,new PersonInput(null,"Admin",null,null,location.getId(),ZonedDateTime.now(),Country.VATICAN));
        assertThatThrownBy(()->references.delete(Location.class,location.getId(),location.getVersion())).isInstanceOf(BusinessException.class);
        references.delete(Person.class,person.getId(),person.getVersion());
        references.delete(Location.class,location.getId(),location.getVersion());
    }
    @Test void filtersMatchExactlyAndPagesAreServerSide() throws Exception {
        group("Alpha",10,Semester.FIRST);group("Alphabet",10,Semester.SECOND);group("Beta",10,Semester.EIGHTH);
        mvc.perform(get("/api/groups").param("filter","name").param("value","Alpha"))
            .andExpect(jsonPath("$.total").value(1)).andExpect(jsonPath("$.items[0].name").value("Alpha"));
        mvc.perform(get("/api/groups").param("size","1").param("page","1").param("sort","name").param("direction","asc"))
            .andExpect(jsonPath("$.total").value(3)).andExpect(jsonPath("$.items.length()").value(1)).andExpect(jsonPath("$.items[0].name").value("Alphabet"));
        mvc.perform(get("/api/groups").param("filter","semesterEnum").param("value","EIGHTH"))
            .andExpect(jsonPath("$.total").value(1));
        mvc.perform(get("/api/groups").param("sort","g.name desc; drop table person" )).andExpect(status().isBadRequest());
    }
    @Test void specialOperationsUseSemesterOrderRatherThanAlphabet() {
        group("Third",10,Semester.THIRD);group("Eighth",10,Semester.EIGHTH);group("First",10,Semester.FIRST);
        assertThat(groups.sumShouldBeExpelled()).isEqualTo(BigInteger.valueOf(6));
        assertThat(groups.maxSemester().getSemesterEnum()).isEqualTo(Semester.EIGHTH);
        assertThat(groups.countAfter(Semester.SECOND)).isEqualTo(2);
    }
    @Test void transferIsAtomicAndRemovesEmptySource() {
        var source=group("Source",10,Semester.FIRST);var target=group("Target",20,Semester.SECOND);
        groups.transfer(new TransferInput(source.getId(),target.getId()));
        assertThat(groups.get(target.getId()).getStudentsCount()).isEqualTo(30);
        assertThat(groups.get(target.getId()).getTransferredStudents()).isEqualTo(11);
        assertThatThrownBy(()->groups.get(source.getId())).isInstanceOf(BusinessException.class);
        assertThat(references.get(Coordinates.class,coordinates.getId())).isNotNull();
    }
    @Test void transferOverflowRollsBackBothGroups() {
        var source=group("Source",10,Semester.FIRST);var target=group("Target",Long.MAX_VALUE,Semester.SECOND);
        assertThatThrownBy(()->groups.transfer(new TransferInput(source.getId(),target.getId()))).isInstanceOf(ArithmeticException.class);
        assertThat(groups.get(source.getId()).getStudentsCount()).isEqualTo(10);
        assertThat(groups.get(target.getId()).getStudentsCount()).isEqualTo(Long.MAX_VALUE);
        assertThatThrownBy(()->groups.transfer(new TransferInput(source.getId(),source.getId()))).isInstanceOf(BusinessException.class);
    }
    @Test void staleEditsAreRejected() throws Exception {
        var group=group("A",10,Semester.FIRST);long old=group.getVersion();groups.addStudent(group.getId());
        mvc.perform(put("/api/groups/"+group.getId()).with(csrf()).contentType("application/json")
            .content(json.writeValueAsBytes(input("Overwrite",10,Semester.SECOND,null,old))))
            .andExpect(status().isConflict());
        assertThat(groups.get(group.getId()).getName()).isEqualTo("A");
    }
    @Test void concurrentStudentAddsDoNotLoseUpdates() throws Exception {
        var group=group("A",10,Semester.FIRST);
        try(var executor=Executors.newFixedThreadPool(4)) {
            List<Callable<Void>> tasks=java.util.stream.IntStream.range(0,16).mapToObj(i->(Callable<Void>)()->{groups.addStudent(group.getId());return null;}).toList();
            for(var future:executor.invokeAll(tasks))future.get(20,TimeUnit.SECONDS);
        }
        assertThat(groups.get(group.getId()).getStudentsCount()).isEqualTo(26);
    }
    @Test void authRegistrationAndCsrfAreEnforced() throws Exception {
        mvc.perform(get("/api/groups").with(anonymous())).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/groups").contentType("application/json").content("{}" )).andExpect(status().isForbidden());
        mvc.perform(post("/api/register").with(anonymous()).with(csrf()).contentType("application/json")
            .content("{\"username\":\"student\",\"password\":\"strongpass123\"}" )).andExpect(status().isCreated());
        mvc.perform(post("/api/login").with(anonymous()).with(csrf()).param("username","student").param("password","strongpass123"))
            .andExpect(status().isOk());
        String hash=jdbc.queryForObject("SELECT password_hash FROM app_user WHERE username='student'",String.class);
        assertThat(hash).startsWith("$2a$").doesNotContain("strongpass123");
    }
    @Test void liveUpdatesArriveAfterCommittedChanges() throws Exception {
        var connection=mvc.perform(get("/api/events")).andExpect(request().asyncStarted()).andReturn();
        assertThat(connection.getResponse().getContentAsString()).contains("event:ready");
        var g=group("Broadcast",10,Semester.FIRST);
        assertThat(connection.getResponse().getContentAsString()).contains("event:change").contains(String.valueOf(g.getId()));
        connection.getRequest().getAsyncContext().complete();
    }

    @Test void everyStringColumnCanBeFilteredAndSorted() throws Exception {
        var location=references.location(null,new LocationInput(null,1.0,2.0f,3L));
        var admin=references.person(null,new PersonInput(null,"Admin",Color.GREEN,Color.BROWN,location.getId(),ZonedDateTime.now(),Country.GERMANY));
        groups.create(input("Exact",10,Semester.THIRD,admin.getId(),null));
        group("Other",20,Semester.FIRST);
        var filters=java.util.Map.of("name","Exact","formOfEducation","FULL_TIME_EDUCATION",
            "semesterEnum","THIRD","adminName","Admin","adminEyeColor","GREEN",
            "adminHairColor","BROWN","adminNationality","GERMANY");
        for(var entry:filters.entrySet()) {
            mvc.perform(get("/api/groups").param("filter",entry.getKey()).param("value",entry.getValue())
                .param("sort",entry.getKey()).param("direction","asc"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(entry.getKey().equals("formOfEducation")?2:1));
            mvc.perform(get("/api/groups").param("sort",entry.getKey()).param("direction","desc"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(2));
        }
    }

    @Test void allRequiredGroupFieldsAreConstrainedInDatabase() {
        var group=group("Valid",10,Semester.FIRST);
        for(String column:List.of("students_count","expelled_students","transferred_students","should_be_expelled","average_mark")) {
            assertThatThrownBy(()->jdbc.update("UPDATE study_group SET "+column+"=0 WHERE id=?",group.getId())).isInstanceOf(DataIntegrityViolationException.class);
        }
        for(String column:List.of("name","coordinates_id","creation_date","students_count","expelled_students",
            "transferred_students","form_of_education","should_be_expelled","average_mark","semester_enum")) {
            assertThatThrownBy(()->jdbc.update("UPDATE study_group SET "+column+"=NULL WHERE id=?",group.getId())).isInstanceOf(DataIntegrityViolationException.class);
        }
    }

    @Test void linkedAdminCannotBeDeletedUntilDetached() throws Exception {
        var location=references.location(null,new LocationInput(null,1.0,2.0f,3L));
        var admin=references.person(null,new PersonInput(null,"Admin",null,null,location.getId(),ZonedDateTime.now(),Country.GERMANY));
        var group=groups.create(input("Group",10,Semester.FIRST,admin.getId(),null));
        mvc.perform(delete("/api/references/people/"+admin.getId()).with(csrf()).param("version",String.valueOf(admin.getVersion())))
            .andExpect(status().isConflict());
        groups.update(group.getId(),input("Group",10,Semester.FIRST,null,group.getVersion()));
        references.delete(Person.class,admin.getId(),admin.getVersion());
        assertThat(groups.get(group.getId()).getGroupAdmin()).isNull();
    }

    @Test void fractionalStudentCountIsNotSilentlyRounded() throws Exception {
        var body=json.valueToTree(input("Fraction",10,Semester.FIRST,null,null));
        ((com.fasterxml.jackson.databind.node.ObjectNode)body).put("studentsCount",1.5);
        mvc.perform(post("/api/groups").with(csrf()).contentType("application/json").content(json.writeValueAsBytes(body)))
            .andExpect(status().isBadRequest());
        assertThat(groups.page(0,10,"id","asc","",null).total()).isZero();
    }
}

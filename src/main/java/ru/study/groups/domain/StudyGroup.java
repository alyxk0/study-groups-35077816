package ru.study.groups.domain;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;
import ru.study.groups.validation.Finite;
import java.time.LocalDateTime;
@Entity @Table(name = "study_group") @Getter @Setter @NoArgsConstructor
public class StudyGroup extends BaseEntity {
    @NotBlank @Column(nullable = false, columnDefinition = "TEXT") private String name;
    @NotNull @ManyToOne(optional = false) @JoinColumn(name = "coordinates_id", nullable = false) private Coordinates coordinates;
    @NotNull @Column(name = "creation_date", nullable = false, updatable = false) private LocalDateTime creationDate;
    @Positive @Column(name = "students_count", nullable = false) private long studentsCount;
    @NotNull @Positive @Column(name = "expelled_students", nullable = false) private Integer expelledStudents;
    @NotNull @Positive @Column(name = "transferred_students", nullable = false) private Integer transferredStudents;
    @NotNull @Enumerated(EnumType.STRING) @Column(name = "form_of_education", nullable = false) private FormOfEducation formOfEducation;
    @Positive @Column(name = "should_be_expelled", nullable = false) private int shouldBeExpelled;
    @NotNull @Positive @Finite @Column(name = "average_mark", nullable = false) private Double averageMark;
    @NotNull @Enumerated(EnumType.STRING) @Column(name = "semester_enum", nullable = false) private Semester semesterEnum;
    @ManyToOne @JoinColumn(name = "group_admin_id") private Person groupAdmin;
    @PrePersist void created() { creationDate = LocalDateTime.now(); }
}


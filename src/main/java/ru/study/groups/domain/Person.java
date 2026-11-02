package ru.study.groups.domain;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;
import java.time.ZonedDateTime;
@Entity @Table(name = "person") @Getter @Setter @NoArgsConstructor
public class Person extends BaseEntity {
    @NotBlank @Column(nullable = false, columnDefinition = "TEXT") private String name;
    @Enumerated(EnumType.STRING) @Column(name = "eye_color") private Color eyeColor;
    @Enumerated(EnumType.STRING) @Column(name = "hair_color") private Color hairColor;
    @NotNull @ManyToOne(optional = false) @JoinColumn(name = "location_id", nullable = false) private Location location;
    @NotNull @Convert(converter = BirthdayConverter.class) @Column(nullable = false, columnDefinition = "TIMESTAMP WITH TIME ZONE") private ZonedDateTime birthday;
    @NotNull @Enumerated(EnumType.STRING) @Column(nullable = false) private Country nationality;
}


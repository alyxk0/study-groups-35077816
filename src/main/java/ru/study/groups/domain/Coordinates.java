package ru.study.groups.domain;
import jakarta.persistence.*;
import lombok.*;
import ru.study.groups.validation.Finite;
@Entity @Table(name = "coordinates") @Getter @Setter @NoArgsConstructor
public class Coordinates extends BaseEntity {
    private long x;
    @Finite @Column(nullable = false) private double y;
}


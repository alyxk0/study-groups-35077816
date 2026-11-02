package ru.study.groups.domain;
import jakarta.persistence.*;
import lombok.*;
import ru.study.groups.validation.Finite;
@Entity @Table(name = "location") @Getter @Setter @NoArgsConstructor
public class Location extends BaseEntity {
    @Finite @Column(nullable = false) private double x;
    @Finite @Column(nullable = false) private float y;
    private long z;
}


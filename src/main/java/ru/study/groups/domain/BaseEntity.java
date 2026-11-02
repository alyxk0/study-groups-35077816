package ru.study.groups.domain;
import jakarta.persistence.*;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.Setter;
@Getter @Setter @MappedSuperclass
public abstract class BaseEntity {
    @Id @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "objectIds")
    @SequenceGenerator(name = "objectIds", sequenceName = "object_seq", allocationSize = 1)
    @Positive private long id;
    @Version private long version;
}

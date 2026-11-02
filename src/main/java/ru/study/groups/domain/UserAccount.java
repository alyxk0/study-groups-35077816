package ru.study.groups.domain;
import jakarta.persistence.*;
import lombok.*;
@Entity @Table(name = "app_user") @Getter @Setter @NoArgsConstructor
public class UserAccount extends BaseEntity {
    @Column(nullable = false, unique = true, length = 40) private String username;
    @Column(name = "password_hash", nullable = false, length = 100) private String passwordHash;
}


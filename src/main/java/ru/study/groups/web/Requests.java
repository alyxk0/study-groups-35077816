package ru.study.groups.web;
import jakarta.validation.constraints.*;
import ru.study.groups.domain.*;
import ru.study.groups.validation.Finite;
import java.time.ZonedDateTime;
public final class Requests {
    private Requests() {}
    public record GroupInput(Long version,
        @NotBlank(message = "Введите название группы") String name,
        @NotNull @Positive Long coordinatesId,
        @NotNull @Positive Long studentsCount,
        @NotNull @Positive Integer expelledStudents,
        @NotNull @Positive Integer transferredStudents,
        @NotNull FormOfEducation formOfEducation,
        @NotNull @Positive Integer shouldBeExpelled,
        @NotNull @Positive @Finite Double averageMark,
        @NotNull Semester semesterEnum,
        @Positive Long groupAdminId) {}
    public record CoordinatesInput(Long version, @NotNull Long x, @NotNull @Finite Double y) {}
    public record LocationInput(Long version, @NotNull @Finite Double x, @NotNull @Finite Float y, @NotNull Long z) {}
    public record PersonInput(Long version, @NotBlank String name, Color eyeColor, Color hairColor,
        @NotNull @Positive Long locationId, @NotNull ZonedDateTime birthday, @NotNull Country nationality) {}
    public record TransferInput(@NotNull @Positive Long sourceId, @NotNull @Positive Long targetId) {}
    public record Registration(
        @NotNull @Pattern(regexp = "[a-zA-Z0-9_]{3,40}", message = "Логин: 3–40 латинских букв, цифр или _") String username,
        @NotNull @Size(min = 8, max = 72, message = "Пароль: 8–72 символа") String password) {}
}


package ru.study.groups.domain;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.sql.Timestamp;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
@Converter
public class BirthdayConverter implements AttributeConverter<ZonedDateTime, Timestamp> {
    public Timestamp convertToDatabaseColumn(ZonedDateTime value) {
        return value == null ? null : Timestamp.from(value.toInstant());
    }
    public ZonedDateTime convertToEntityAttribute(Timestamp value) {
        return value == null ? null : value.toInstant().atZone(ZoneOffset.UTC);
    }
}


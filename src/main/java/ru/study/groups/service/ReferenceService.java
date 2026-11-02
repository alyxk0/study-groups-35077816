package ru.study.groups.service;

import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.study.groups.domain.*;
import ru.study.groups.repository.ObjectRepository;
import ru.study.groups.web.Requests.*;
import java.util.List;
import java.util.function.Consumer;

@Service @RequiredArgsConstructor @Transactional
public class ReferenceService {
    private final ObjectRepository objects;
    private final ApplicationEventPublisher events;
    @Transactional(readOnly = true) public <T> List<T> all(Class<T> type) { return objects.all(type); }
    @Transactional(readOnly = true) public <T> T get(Class<T> type, long id) { return objects.find(type, id); }
    public Coordinates coordinates(Long id, CoordinatesInput input) {
        return save(Coordinates.class, new Coordinates(), id, input.version(), value -> { value.setX(input.x()); value.setY(input.y()); });
    }
    public Location location(Long id, LocationInput input) {
        return save(Location.class, new Location(), id, input.version(), value -> { value.setX(input.x()); value.setY(input.y()); value.setZ(input.z()); });
    }
    public Person person(Long id, PersonInput input) {
        return save(Person.class, new Person(), id, input.version(), value -> {
            value.setName(input.name().trim()); value.setEyeColor(input.eyeColor()); value.setHairColor(input.hairColor());
            value.setLocation(objects.find(Location.class, input.locationId())); value.setBirthday(input.birthday()); value.setNationality(input.nationality());
        });
    }
    private <T extends BaseEntity> T save(Class<T> type, T fresh, Long id, Long version, Consumer<T> edit) {
        T value = id == null ? fresh : objects.lock(type, id);
        if (id != null) GroupService.checkVersion(value, version);
        edit.accept(value);
        if (id == null) objects.persist(value);
        objects.flush();
        events.publishEvent(new ChangeEvent("references", id == null ? "create" : "update", value.getId()));
        return value;
    }
    public void delete(Class<? extends BaseEntity> type, long id, long version) {
        var value = objects.lock(type, id);
        GroupService.checkVersion(value, version);
        if (objects.references(type, id) > 0) throw BusinessException.conflict("Удаление невозможно: на объект ссылаются другие объекты. Сначала измените их связи.");
        objects.remove(value);
        objects.flush();
        events.publishEvent(new ChangeEvent("references", "delete", id));
    }
}

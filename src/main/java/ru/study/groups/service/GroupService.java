package ru.study.groups.service;

import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.study.groups.domain.*;
import ru.study.groups.repository.*;
import ru.study.groups.web.Requests.*;
import java.math.BigInteger;
import java.util.*;

@Service @RequiredArgsConstructor @Transactional
public class GroupService {
    private final ObjectRepository objects;
    private final GroupRepository groups;
    private final ApplicationEventPublisher events;

    @Transactional(readOnly = true)
    public GroupRepository.Page page(int page, int size, String sort, String direction, String filter, String value) {
        return groups.page(page, size, sort, direction, filter, value);
    }
    @Transactional(readOnly = true) public StudyGroup get(long id) { return objects.find(StudyGroup.class, id); }
    public StudyGroup create(GroupInput input) {
        var group = new StudyGroup();
        apply(group, input);
        objects.persist(group);
        changed(group, "create");
        return group;
    }
    public StudyGroup update(long id, GroupInput input) {
        var group = objects.lock(StudyGroup.class, id);
        checkVersion(group, input.version());
        apply(group, input);
        changed(group, "update");
        return group;
    }
    public void delete(long id, long version) {
        var group = objects.lock(StudyGroup.class, id);
        checkVersion(group, version);
        objects.remove(group);
        changed(group, "delete");
    }
    private void apply(StudyGroup group, GroupInput input) {
        group.setName(input.name().trim());
        group.setCoordinates(objects.find(Coordinates.class, input.coordinatesId()));
        group.setStudentsCount(input.studentsCount());
        group.setExpelledStudents(input.expelledStudents());
        group.setTransferredStudents(input.transferredStudents());
        group.setFormOfEducation(input.formOfEducation());
        group.setShouldBeExpelled(input.shouldBeExpelled());
        group.setAverageMark(input.averageMark());
        group.setSemesterEnum(input.semesterEnum());
        group.setGroupAdmin(input.groupAdminId() == null ? null : objects.find(Person.class, input.groupAdminId()));
    }
    @Transactional(readOnly = true) public BigInteger sumShouldBeExpelled() {
        return objects.all(StudyGroup.class).stream().map(g -> BigInteger.valueOf(g.getShouldBeExpelled()))
            .reduce(BigInteger.ZERO, BigInteger::add);
    }
    @Transactional(readOnly = true) public StudyGroup maxSemester() {
        return objects.all(StudyGroup.class).stream()
            .max(Comparator.comparing(StudyGroup::getSemesterEnum)).orElse(null);
    }
    @Transactional(readOnly = true) public long countAfter(Semester semester) {
        return objects.all(StudyGroup.class).stream().filter(g -> g.getSemesterEnum().compareTo(semester) > 0).count();
    }
    public Map<String, Object> transfer(TransferInput input) {
        long sourceId = input.sourceId(), targetId = input.targetId();
        if (sourceId == targetId) throw BusinessException.invalid("Выберите две разные группы");
        // Consistent lock order prevents opposing transfers from deadlocking.
        var first = objects.lock(StudyGroup.class, Math.min(sourceId, targetId));
        var second = objects.lock(StudyGroup.class, Math.max(sourceId, targetId));
        var source = sourceId == first.getId() ? first : second;
        var target = targetId == first.getId() ? first : second;
        long students = source.getStudentsCount();
        target.setStudentsCount(Math.addExact(target.getStudentsCount(), students));
        target.setTransferredStudents(Math.toIntExact(Math.addExact(target.getTransferredStudents().longValue(), students)));
        // An empty group violates the variant. Delete its record, keeping shared objects intact.
        objects.remove(source);
        changed(source, "delete");
        changed(target, "update");
        return Map.of("transferred", students, "deletedGroupId", sourceId, "target", target);
    }
    public StudyGroup addStudent(long id) {
        var group = objects.lock(StudyGroup.class, id);
        group.setStudentsCount(Math.addExact(group.getStudentsCount(), 1));
        changed(group, "update");
        return group;
    }
    public static void checkVersion(BaseEntity entity, Long version) {
        if (version == null) throw BusinessException.invalid("Для изменения требуется версия объекта");
        if (version != entity.getVersion()) throw BusinessException.conflict("Объект уже изменён другим пользователем. Откройте его заново.");
    }
    private void changed(StudyGroup group, String action) {
        objects.flush();
        events.publishEvent(new ChangeEvent("groups", action, group.getId()));
    }
}

package ru.study.groups.repository;
import jakarta.persistence.*;
import org.springframework.stereotype.Repository;
import ru.study.groups.domain.*;
import ru.study.groups.service.BusinessException;
import java.util.*;
@Repository
public class GroupRepository {
    @PersistenceContext private EntityManager em;
    private static final Map<String,String> SORTS = Map.ofEntries(
        Map.entry("id", "g.id"), Map.entry("name", "g.name"),
        Map.entry("formOfEducation", "g.formOfEducation"), Map.entry("semesterEnum", "g.semesterEnum"),
        Map.entry("adminName", "a.name"), Map.entry("adminEyeColor", "a.eyeColor"),
        Map.entry("adminHairColor", "a.hairColor"), Map.entry("adminNationality", "a.nationality"));
    public record Page(List<StudyGroup> items, long total, int page, int size) {}
    public Page page(int page, int size, String sort, String direction, String filter, String value) {
        if (page < 0 || page > 1000000 || size < 1 || size > 100) throw BusinessException.invalid("Размер страницы: 1–100");
        String column = SORTS.get(sort);
        if (column == null || !(direction.equals("asc") || direction.equals("desc"))) throw BusinessException.invalid("Неверная сортировка");
        if (!filter.isEmpty() && (!SORTS.containsKey(filter) || filter.equals("id"))) throw BusinessException.invalid("Неверная колонка фильтра");
        String from = " from StudyGroup g left join g.groupAdmin a";
        boolean filtered = !filter.isEmpty() && value != null;
        String where = filtered ? " where " + SORTS.get(filter) + " = :value" : "";
        Object parameter = filtered ? filterValue(filter, value) : null;
        var count = em.createQuery("select count(g)" + from + where, Long.class);
        var rows = em.createQuery("select g" + from + where + " order by " + column + " " + direction + (sort.equals("id") ? "" : ", g.id asc"), StudyGroup.class);
        if (filtered) { count.setParameter("value", parameter); rows.setParameter("value", parameter); }
        return new Page(rows.setFirstResult(Math.multiplyExact(page, size)).setMaxResults(size).getResultList(), count.getSingleResult(), page, size);
    }
    private Object filterValue(String field, String value) {
        try {
            return switch (field) {
                case "formOfEducation" -> FormOfEducation.valueOf(value);
                case "semesterEnum" -> Semester.valueOf(value);
                case "adminEyeColor", "adminHairColor" -> Color.valueOf(value);
                case "adminNationality" -> Country.valueOf(value);
                default -> value;
            };
        } catch (IllegalArgumentException ex) { throw BusinessException.invalid("Недопустимое значение фильтра"); }
    }
}


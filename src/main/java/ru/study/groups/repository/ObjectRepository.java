package ru.study.groups.repository;
import jakarta.persistence.*;
import org.springframework.stereotype.Repository;
import ru.study.groups.domain.*;
import ru.study.groups.service.BusinessException;
import java.util.List;
@Repository
public class ObjectRepository {
    @PersistenceContext private EntityManager em;
    public <T> T find(Class<T> type, long id) {
        T value = em.find(type, id);
        if (value == null) throw BusinessException.missing();
        return value;
    }
    public <T> T lock(Class<T> type, long id) {
        T value = em.find(type, id, LockModeType.PESSIMISTIC_WRITE);
        if (value == null) throw BusinessException.missing();
        // Refresh after the row lock: another transaction may have committed while we waited.
        em.refresh(value, LockModeType.PESSIMISTIC_WRITE);
        return value;
    }
    public <T> List<T> all(Class<T> type) {
        return em.createQuery("select o from " + type.getSimpleName() + " o order by o.id", type).getResultList();
    }
    public void persist(Object value) { em.persist(value); }
    public void remove(Object value) { em.remove(value); }
    public void flush() { em.flush(); }
    public long references(Class<? extends BaseEntity> type, long id) {
        String query = type == Coordinates.class ? "select count(g) from StudyGroup g where g.coordinates.id = :id"
            : type == Person.class ? "select count(g) from StudyGroup g where g.groupAdmin.id = :id"
            : "select count(p) from Person p where p.location.id = :id";
        return em.createQuery(query, Long.class).setParameter("id", id).getSingleResult();
    }
    public UserAccount user(String username) {
        return em.createQuery("select u from UserAccount u where u.username = :name", UserAccount.class)
            .setParameter("name", username).getResultStream().findFirst().orElse(null);
    }
}


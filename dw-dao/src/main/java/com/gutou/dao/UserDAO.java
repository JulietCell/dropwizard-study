package com.gutou.dao;

import com.gutou.model.entity.User;
import io.dropwizard.hibernate.AbstractDAO;
import org.hibernate.SessionFactory;
import org.hibernate.query.Query;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Optional;

public class UserDAO extends AbstractDAO<User> {

    private static final Logger logger = LoggerFactory.getLogger(UserDAO.class);

    public UserDAO(SessionFactory sessionFactory) {
        super(sessionFactory);
    }

    public Optional<User> findById(Long id) {
        logger.debug("查找用户，ID: {}", id);
        return Optional.ofNullable(get(id));
    }

    public Optional<User> findByEmail(String email) {
        Query<User> query = currentSession().createQuery(
            "SELECT u FROM User u WHERE u.email = :email", User.class);
        query.setParameter("email", email);
        return Optional.ofNullable(query.uniqueResult());
    }
    public List<User> findAll() {
        Query<User> query = currentSession().createQuery(
            "SELECT u FROM User u", User.class);
        return query.getResultList();
    }

    public User create(User user) {
        logger.info("创建新用户: {}", user.getUsername());
        return persist(user);
    }

    public User update(User user) {
        return persist(user);
    }

    public void delete(User user) {
        currentSession().remove(user);
    }

    public void deleteById(Long id) {
        findById(id).ifPresent(this::delete);
    }
}




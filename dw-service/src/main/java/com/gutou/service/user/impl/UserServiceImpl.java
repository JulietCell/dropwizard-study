package com.gutou.service.user.impl;

import com.gutou.dao.UserDAO;
import com.gutou.model.entity.User;
import com.gutou.service.user.UserServiceApi;
import io.dropwizard.hibernate.UnitOfWork;
import jakarta.inject.Inject;
import org.jvnet.hk2.annotations.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Optional;

@Service
public class UserServiceImpl implements UserServiceApi {
    
    private static final Logger log = LoggerFactory.getLogger(UserServiceImpl.class);
    
    @Inject
    private UserDAO userDAO;
    
    @Override
    @UnitOfWork
    public List<User> findAll() {
        log.debug("Service: 查找所有用户");
        return userDAO.findAll();
    }
    
    @Override
    @UnitOfWork
    public Optional<User> findById(Long id) {
        log.debug("Service: 查找用户，ID: {}", id);
        return userDAO.findById(id);
    }
    
    @Override
    @UnitOfWork
    public Optional<User> findByEmail(String email) {
        log.debug("Service: 查找用户，Email: {}", email);
        return userDAO.findByEmail(email);
    }
    
    @Override
    @UnitOfWork
    public User create(User user) {
        log.info("Service: 创建新用户: {}", user.getUsername());
        // 检查邮箱是否已存在
        if (userDAO.findByEmail(user.getEmail()).isPresent()) {
            throw new IllegalArgumentException("User with email " + user.getEmail() + " already exists");
        }
        return userDAO.create(user);
    }
    
    @Override
    @UnitOfWork
    public User update(User user) {
        log.info("Service: 更新用户: {}", user.getId());
        return userDAO.update(user);
    }
    
    @Override
    @UnitOfWork
    public void deleteById(Long id) {
        log.info("Service: 删除用户: {}", id);
        userDAO.deleteById(id);
    }
}


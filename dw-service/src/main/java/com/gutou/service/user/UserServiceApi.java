package com.gutou.service.user;

import com.gutou.model.entity.User;
import org.jvnet.hk2.annotations.Contract;

import java.util.List;
import java.util.Optional;

@Contract
public interface UserServiceApi {
    
    List<User> findAll();
    
    Optional<User> findById(Long id);
    
    Optional<User> findByEmail(String email);
    
    User create(User user);
    
    User update(User user);
    
    void deleteById(Long id);
}


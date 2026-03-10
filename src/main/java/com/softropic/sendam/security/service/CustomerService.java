package com.softropic.sendam.security.service;



import com.softropic.sendam.common.consumer.Consumer;
import com.softropic.sendam.security.repo.UserRepository;

import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class CustomerService {

    private final UserRepository userRepository;

    public CustomerService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public Optional<Consumer> findCustomerById(Long id) {
        return this.userRepository.findCustomerById(id);
    }

    public Optional<Consumer> findCustomerByLogin(String login) {
        return this.userRepository.findCustomerByLogin(login);
    }
}

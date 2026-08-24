package com.quietterminal.ledger.security;

import java.util.List;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import com.quietterminal.ledger.entity.UserCredentials;
import com.quietterminal.ledger.repository.UserCredentialsRepository;
import com.quietterminal.ledger.repository.UserRepository;

@Service
public class CustomUserDetailsService implements UserDetailsService {

    private final UserCredentialsRepository credentialsRepository;
    private final UserRepository userRepository;

    public CustomUserDetailsService(UserCredentialsRepository credentialsRepository, UserRepository userRepository) {
        this.credentialsRepository = credentialsRepository;
        this.userRepository = userRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        UserCredentials credentials = credentialsRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("No user found for username: " + username));
        userRepository.findById(credentials.getUserId())
                .orElseThrow(() -> new UsernameNotFoundException("No user found for username: " + username));

        return org.springframework.security.core.userdetails.User
                .withUsername(credentials.getUsername())
                .password(credentials.getPasswordHash())
                .authorities(List.of())
                .build();
    }
}

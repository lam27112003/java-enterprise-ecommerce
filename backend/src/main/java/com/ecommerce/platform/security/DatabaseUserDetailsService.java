package com.ecommerce.platform.security;

import com.ecommerce.platform.auth.exception.AuthExceptions;
import com.ecommerce.platform.auth.exception.InvalidTokenException;
import com.ecommerce.platform.user.domain.Role;
import com.ecommerce.platform.user.domain.RoleCode;
import com.ecommerce.platform.user.domain.User;
import com.ecommerce.platform.user.domain.UserStatus;
import com.ecommerce.platform.user.repository.UserRepository;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class DatabaseUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    public DatabaseUserDetailsService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        User user = userRepository.findByEmailIgnoreCase(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));
        return toAuthenticatedUser(user);
    }

    @Transactional(readOnly = true)
    public AuthenticatedUser loadAuthorizedUser(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new InvalidTokenException("Access token subject is invalid"));
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw AuthExceptions.accountDisabled();
        }
        return toAuthenticatedUser(user);
    }

    public AuthenticatedUser toAuthenticatedUser(User user) {
        Set<RoleCode> roles = user.getRoles().stream()
                .map(Role::getCode)
                .collect(Collectors.toSet());
        return new AuthenticatedUser(
                user.getId(),
                user.getEmail(),
                user.getPasswordHash(),
                user.getFullName(),
                user.getStatus(),
                roles
        );
    }
}

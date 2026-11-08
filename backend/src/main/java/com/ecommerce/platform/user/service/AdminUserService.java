package com.ecommerce.platform.user.service;

import com.ecommerce.platform.common.api.PageResponse;
import com.ecommerce.platform.user.domain.Role;
import com.ecommerce.platform.user.domain.RoleCode;
import com.ecommerce.platform.user.domain.User;
import com.ecommerce.platform.user.domain.UserStatus;
import com.ecommerce.platform.user.dto.AdminUserDtos.AdminUserResponse;
import com.ecommerce.platform.user.dto.AdminUserDtos.AdminUserStatusUpdateRequest;
import com.ecommerce.platform.user.exception.UserExceptions;
import com.ecommerce.platform.user.repository.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class AdminUserService {

    private final UserRepository userRepository;
    private final AdminUserStatusLock adminUserStatusLock;

    public AdminUserService(UserRepository userRepository, AdminUserStatusLock adminUserStatusLock) {
        this.userRepository = userRepository;
        this.adminUserStatusLock = adminUserStatusLock;
    }

    @Transactional(readOnly = true)
    public PageResponse<AdminUserResponse> list(String query, int page, int size) {
        String q = query == null || query.isBlank() ? null : query.trim();
        PageRequest pageable = PageRequest.of(
                Math.max(page, 0),
                Math.min(Math.max(size, 1), 100),
                Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<User> result = q == null ? userRepository.findAll(pageable) : userRepository.search(q, pageable);
        return PageResponse.from(result.map(AdminUserService::toResponse));
    }

    @Transactional
    public AdminUserResponse updateStatus(UUID targetUserId, UUID actorAdminId, AdminUserStatusUpdateRequest request) {
        if (request == null || request.status() == null) {
            throw UserExceptions.invalidStatus();
        }
        UserStatus newStatus = request.status();
        if (newStatus != UserStatus.ACTIVE
                && newStatus != UserStatus.DISABLED
                && newStatus != UserStatus.SUSPENDED) {
            throw UserExceptions.invalidStatus();
        }
        if (targetUserId.equals(actorAdminId)
                && (newStatus == UserStatus.DISABLED || newStatus == UserStatus.SUSPENDED)) {
            throw UserExceptions.selfStatusChangeForbidden();
        }

        final AdminUserResponse[] response = new AdminUserResponse[1];
        adminUserStatusLock.runUnderLock(() -> {
            User target = userRepository.findById(targetUserId).orElseThrow(UserExceptions::notFound);
            if (target.getStatus() == newStatus) {
                response[0] = toResponse(target);
                return;
            }
            if (target.hasRole(RoleCode.ADMIN)
                    && newStatus != UserStatus.ACTIVE
                    && userRepository.countActiveAdminsExcluding(RoleCode.ADMIN, UserStatus.ACTIVE, targetUserId) == 0) {
                throw UserExceptions.lastActiveAdminForbidden();
            }
            target.setStatus(newStatus);
            userRepository.saveAndFlush(target);
            response[0] = toResponse(target);
        });
        return response[0];
    }

    private static AdminUserResponse toResponse(User user) {
        Set<RoleCode> roles = user.getRoles().stream().map(Role::getCode).collect(Collectors.toSet());
        return new AdminUserResponse(
                user.getId(),
                user.getEmail(),
                user.getFullName(),
                user.getStatus(),
                roles,
                user.getCreatedAt());
    }
}

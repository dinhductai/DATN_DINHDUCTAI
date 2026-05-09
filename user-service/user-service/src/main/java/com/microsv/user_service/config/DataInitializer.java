package com.microsv.user_service.config;

import com.microsv.user_service.entity.Permission;
import com.microsv.user_service.entity.Role;
import com.microsv.user_service.entity.User;
import com.microsv.user_service.enumeration.RoleName;
import com.microsv.user_service.repository.PermissionRepository;
import com.microsv.user_service.repository.RoleRepository;
import com.microsv.user_service.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

@Slf4j
@Component
@RequiredArgsConstructor
public class DataInitializer {

    private final PermissionRepository permissionRepository;
    private final RoleRepository roleRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${DEFAULT_ADMIN_PASSWORD:admin123}")
    private String defaultAdminPassword;

    @Value("${DEFAULT_USER_PASSWORD:user123}")
    private String defaultUserPassword;

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void initializeData() {
        log.info("Starting data initialization...");
        initializePermissions();
        initializeRoles();
        initializeUsers();
        log.info("Data initialization completed.");
    }

    private void initializePermissions() {
        if (permissionRepository.count() == 0) {
            Permission createTask = new Permission();
            createTask.setPermissionName("CREATE_TASK");
            permissionRepository.save(createTask);

            Permission viewDashboard = new Permission();
            viewDashboard.setPermissionName("VIEW_DASHBOARD");
            permissionRepository.save(viewDashboard);

            Permission viewAdmin = new Permission();
            viewAdmin.setPermissionName("VIEW_ADMIN_PANEL");
            permissionRepository.save(viewAdmin);

            log.info("Initialized sample Permissions.");
        } else {
            log.info("Permissions already exist, skipping.");
        }
    }

    private void initializeRoles() {
        if (roleRepository.count() == 0) {
            Permission createTask = permissionRepository.findByPermissionName("CREATE_TASK")
                    .orElseThrow(() -> new IllegalStateException("Permission CREATE_TASK not found"));
            Permission viewDashboard = permissionRepository.findByPermissionName("VIEW_DASHBOARD")
                    .orElseThrow(() -> new IllegalStateException("Permission VIEW_DASHBOARD not found"));
            Permission viewAdmin = permissionRepository.findByPermissionName("VIEW_ADMIN_PANEL")
                    .orElseThrow(() -> new IllegalStateException("Permission VIEW_ADMIN_PANEL not found"));

            Role userRole = new Role();
            userRole.setRoleName(RoleName.USER);
            userRole.setPermissions(Set.of(createTask, viewDashboard));
            roleRepository.save(userRole);

            Role adminRole = new Role();
            adminRole.setRoleName(RoleName.ADMIN);
            adminRole.setPermissions(Set.of(createTask, viewDashboard, viewAdmin));
            roleRepository.save(adminRole);

            log.info("Initialized sample Roles.");
        } else {
            log.info("Roles already exist, skipping.");
        }
    }

    private void initializeUsers() {
        if (userRepository.findByUserName("admin").isEmpty() && !userRepository.existsByEmail("admin@gmail.com")) {
            Role adminRole = roleRepository.findByRoleName(RoleName.ADMIN)
                    .orElseThrow(() -> new IllegalStateException("Role ADMIN not found"));
            User admin = new User();
            admin.setUserName("admin");
            admin.setEmail("admin@gmail.com");
            admin.setPassword(passwordEncoder.encode(defaultAdminPassword));
            admin.setRoles(Set.of(adminRole));
            userRepository.save(admin);
            log.info("Created user 'admin'.");
        } else {
            log.info("User 'admin' already exists, skipping.");
        }

        if (userRepository.findByUserName("user").isEmpty() && !userRepository.existsByEmail("user2@gmail.com")) {
            Role userRole = roleRepository.findByRoleName(RoleName.USER)
                    .orElseThrow(() -> new IllegalStateException("Role USER not found"));
            User user = new User();
            user.setUserName("user");
            user.setEmail("user2@gmail.com");
            user.setPassword(passwordEncoder.encode(defaultUserPassword));
            user.setRoles(Set.of(userRole));
            userRepository.save(user);
            log.info("Created user 'user'.");
        } else {
            log.info("User 'user' already exists, skipping.");
        }
    }
}

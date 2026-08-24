package com.quietterminal.ledger.bootstrap;

import java.util.EnumSet;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import com.quietterminal.ledger.entity.Role;
import com.quietterminal.ledger.entity.User;
import com.quietterminal.ledger.entity.UserCredentials;
import com.quietterminal.ledger.enums.Permission;
import com.quietterminal.ledger.repository.RoleRepository;
import com.quietterminal.ledger.repository.UserCredentialsRepository;
import com.quietterminal.ledger.repository.UserRepository;

@Component
public class AdminBootstrapRunner implements ApplicationRunner {

    private static final Logger LOG = LoggerFactory.getLogger(AdminBootstrapRunner.class);
    private static final String ADMIN_ROLE_NAME = "Admin";
    private static final String MEMBER_ROLE_NAME = "Member";

    private final UserRepository userRepository;
    private final UserCredentialsRepository credentialsRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final String username;
    private final String password;
    private final String firstName;
    private final String lastName;

    public AdminBootstrapRunner(UserRepository userRepository,
            UserCredentialsRepository credentialsRepository,
            RoleRepository roleRepository,
            PasswordEncoder passwordEncoder,
            @Value("${ledger.bootstrap-admin.username}") String username,
            @Value("${ledger.bootstrap-admin.password}") String password,
            @Value("${ledger.bootstrap-admin.first-name}") String firstName,
            @Value("${ledger.bootstrap-admin.last-name}") String lastName) {
        this.userRepository = userRepository;
        this.credentialsRepository = credentialsRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
        this.username = username;
        this.password = password;
        this.firstName = firstName;
        this.lastName = lastName;
    }

    @Override
    public void run(ApplicationArguments args) {
        seedRolesIfMissing();

        if (credentialsRepository.count() > 0) {
            return;
        }

        if (username == null || username.isBlank() || password == null || password.isBlank()) {
            LOG.warn("No user accounts exist yet and LEDGER_ADMIN_USERNAME / LEDGER_ADMIN_PASSWORD were not "
                    + "provided to bootstrap one. Set those environment variables and restart to create the "
                    + "first admin account.");
            return;
        }

        Role adminRole = roleRepository.findByName(ADMIN_ROLE_NAME)
                .orElseThrow(() -> new IllegalStateException(
                        "Expected the built-in \"" + ADMIN_ROLE_NAME + "\" role to already be seeded."));

        User admin = new User(firstName, lastName, adminRole);
        userRepository.save(admin);
        credentialsRepository.save(new UserCredentials(admin.getUUID(), username, passwordEncoder.encode(password)));

        LOG.info("Bootstrapped initial admin account '{}'.", username);
    }

    private void seedRolesIfMissing() {
        if (roleRepository.count() > 0) {
            return;
        }

        roleRepository.save(new Role(ADMIN_ROLE_NAME, EnumSet.allOf(Permission.class), true));

        Set<Permission> memberPermissions = EnumSet.allOf(Permission.class);
        memberPermissions.remove(Permission.USERS_MANAGE);
        memberPermissions.remove(Permission.ROLES_MANAGE);
        roleRepository.save(new Role(MEMBER_ROLE_NAME, memberPermissions, false));
    }
}

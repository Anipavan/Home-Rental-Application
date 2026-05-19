package com.spa.home_rental_application.user_service.user_service.service.impul;

import com.spa.home_rental_application.KafkaEvents.Producers.DTO.UserServiceEvents.UserProfileCreatedEvent;
import com.spa.home_rental_application.KafkaEvents.Producers.DTO.UserServiceEvents.UserProfileUpdatedEvent;
import com.spa.home_rental_application.KafkaEvents.Producers.Events.UserServiceEvents;
import com.spa.home_rental_application.user_service.user_service.DTO.Request.UserRequestDto;
import com.spa.home_rental_application.user_service.user_service.DTO.Response.External.authResponseDto;
import com.spa.home_rental_application.user_service.user_service.DTO.Response.UserResponseDto;
import com.spa.home_rental_application.user_service.user_service.DTO.Response.usersByRoleDto;
import com.spa.home_rental_application.user_service.user_service.Entities.User;
import com.spa.home_rental_application.user_service.user_service.Exceptionclass.DuplicateUserException;
import com.spa.home_rental_application.user_service.user_service.Exceptionclass.InvalidDocumentTypeException;
import com.spa.home_rental_application.user_service.user_service.Exceptionclass.RecordNotFound;
import com.spa.home_rental_application.user_service.user_service.mapper.UserMapper;
import com.spa.home_rental_application.user_service.user_service.repositry.UserRepo;
import com.spa.home_rental_application.user_service.user_service.service.External.AuthServiceFeig;
import com.spa.home_rental_application.user_service.user_service.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
public class UserServiceImpul implements UserService {

    private static final Set<String> ALLOWED_DOCUMENT_TYPES = Set.of("PROFILE", "ID_PROOF");
    private static final Set<String> ALLOWED_DOC_CONTENT_TYPES =
            Set.of("image/jpeg", "image/png", "image/webp", "application/pdf");
    private static final long MAX_DOC_BYTES = 5L * 1024 * 1024;

    private final UserRepo userRepo;
    private final UserServiceEvents userServiceEvent;
    private final AuthServiceFeig authServiceFeig;
    private final String uploadDir;

    public UserServiceImpul(UserRepo userRepo,
                            UserServiceEvents userServiceEvents,
                            AuthServiceFeig authServiceFeig,
                            @Value("${app.uploads.dir:uploads/users}") String uploadDir) {
        this.userRepo = userRepo;
        this.userServiceEvent = userServiceEvents;
        this.authServiceFeig = authServiceFeig;
        this.uploadDir = uploadDir;
    }

    @Override
    @Transactional
    public UserResponseDto createUser(UserRequestDto userRequest) {
        log.info("createUser email={} authUserId={}", userRequest.email(), userRequest.authUserId());

        if (userRepo.existsByEmailIgnoreCaseAndIsDeletedFalse(userRequest.email())) {
            throw new DuplicateUserException("A user already exists with email: " + userRequest.email());
        }

        User user = UserMapper.toEntity(userRequest);
        LocalDateTime now = LocalDateTime.now();
        user.setCreatedAt(now);
        user.setUpdatedAt(now);
        user.setIsDeleted(false);
        User saved = userRepo.save(user);

        userServiceEvent.sendUserProfileCreated(UserProfileCreatedEvent.builder()
                .eventType("user.profile.created")
                .userId(saved.getId())
                .role(null)
                .timestamp(LocalDateTime.now())
                .build());

        return UserMapper.toDto(saved);
    }

    @Override
    public Page<UserResponseDto> getAllUsers(Pageable pageable) {
        return userRepo.findAllActive(pageable).map(UserMapper::toDto);
    }

    @Override
    public UserResponseDto getUserById(String userId) {
        User user = userRepo.findActiveById(userId).orElseThrow(
                () -> new RecordNotFound("User not found with id: " + userId));
        return UserMapper.toDto(user);
    }

    @Override
    public UserResponseDto getUserByEmail(String email) {
        User user = userRepo.findFirstByEmailIgnoreCaseAndIsDeletedFalse(email).orElseThrow(
                () -> new RecordNotFound("User not found with email: " + email));
        return UserMapper.toDto(user);
    }

    /**
     * Look up a User Service profile by its Auth Service id.
     *
     * <p><b>Self-heal:</b> for legacy registrations (or any case where the
     * Auth → User Feign create at register-time silently failed), there's
     * no User row but the AuthUser definitely exists — that's the id we're
     * being asked about. Rather than 404'ing the owner UI, we Feign back
     * to Auth Service, fetch the bare-bones identity, persist a stub User
     * row, and return it. Future calls hit the cache row directly.
     *
     * <p>Stub rows have just authUserId + firstName/lastName parsed from
     * userName + email. The tenant can fill the rest in via Profile when
     * they next log in (the same upsert path the dashboard uses).
     */
    @Override
    @Transactional
    public UserResponseDto getUserByAuthUserId(String authUserId) {
        Optional<User> existing = userRepo.findFirstByAuthUserIdAndIsDeletedFalse(authUserId);
        if (existing.isPresent()) {
            return UserMapper.toDto(existing.get());
        }
        // Cache miss → try to self-heal from Auth Service.
        authResponseDto auth;
        try {
            auth = authServiceFeig.getById(authUserId);
        } catch (Exception ex) {
            log.warn("Self-heal lookup for authUserId={} failed: {}", authUserId, ex.getMessage());
            auth = null;
        }
        if (auth == null || auth.getUserName() == null) {
            // Auth-tier doesn't know this id either, or the circuit was open
            // and the fallback returned null. Honest 404.
            throw new RecordNotFound("User not found for authUserId: " + authUserId);
        }

        // Synthesize first/last name from userName best-effort. The real
        // values land here when the tenant edits their profile.
        String[] parts = auth.getUserName().split("[\\s._-]+", 2);
        String firstName = parts.length > 0 && !parts[0].isBlank()
                ? parts[0]
                : auth.getUserName();
        String lastName = parts.length > 1 ? parts[1] : "";

        // Email is NOT NULL UNIQUE in the schema. The auth-tier DTO doesn't
        // expose email today, so we drop in a deterministic placeholder
        // keyed off authUserId — the tenant can overwrite it via Profile.
        String email = deriveEmailOrPlaceholder(auth, authUserId);

        User stub = User.builder()
                .authUserId(authUserId)
                .firstName(firstName)
                .lastName(lastName)
                .email(email)
                .isDeleted(false)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        User saved = userRepo.save(stub);
        log.info("Self-healed missing User row for authUserId={} (id={})",
                authUserId, saved.getId());
        return UserMapper.toDto(saved);
    }

    /**
     * Try to read the email from the auth-tier DTO via reflection (the field
     * may or may not be present depending on which version of the DTO was
     * deployed). Falls back to a deterministic placeholder so the NOT NULL
     * UNIQUE email column always has a sane value.
     */
    private String deriveEmailOrPlaceholder(authResponseDto auth, String authUserId) {
        try {
            var m = auth.getClass().getMethod("getEmail");
            Object v = m.invoke(auth);
            if (v != null && !v.toString().isBlank()) return v.toString();
        } catch (ReflectiveOperationException ignored) {
            /* fall through */
        }
        return "tenant-" + authUserId + "@anirudhhomes.local";
    }

    @Override
    @Transactional
    public UserResponseDto deleteUserById(String userId) {
        User user = userRepo.findActiveById(userId).orElseThrow(
                () -> new RecordNotFound("User not found with id: " + userId));
        LocalDateTime now = LocalDateTime.now();
        user.setIsDeleted(true);
        user.setDeletedAt(now);
        user.setUpdatedAt(now);
        userRepo.save(user);
        log.info("Soft-deleted user id={}", userId);
        return UserMapper.toDto(user);
    }

    @Override
    @Transactional
    public UserResponseDto updateUser(UserRequestDto userRequest, String userId) {
        User existing = userRepo.findActiveById(userId).orElseThrow(
                () -> new RecordNotFound("User not found with id: " + userId));

        // Capture an actual diff so the published event is meaningful.
        Map<String, String> changes = new LinkedHashMap<>();

        if (notBlank(userRequest.firstName()) && !userRequest.firstName().equals(existing.getFirstName())) {
            changes.put("firstName", existing.getFirstName() + " → " + userRequest.firstName());
            existing.setFirstName(userRequest.firstName());
        }
        if (notBlank(userRequest.lastName()) && !userRequest.lastName().equals(existing.getLastName())) {
            changes.put("lastName", existing.getLastName() + " → " + userRequest.lastName());
            existing.setLastName(userRequest.lastName());
        }
        if (notBlank(userRequest.email()) && !userRequest.email().equalsIgnoreCase(existing.getEmail())) {
            if (userRepo.existsByEmailIgnoreCaseAndIsDeletedFalse(userRequest.email())) {
                throw new DuplicateUserException("Email already in use: " + userRequest.email());
            }
            changes.put("email", existing.getEmail() + " → " + userRequest.email());
            existing.setEmail(userRequest.email());
        }
        if (notBlank(userRequest.phone()) && !userRequest.phone().equals(existing.getPhone())) {
            changes.put("phone", "updated");
            existing.setPhone(userRequest.phone());
        }
        if (userRequest.dateOfBirth() != null && !userRequest.dateOfBirth().equals(existing.getDateOfBirth())) {
            changes.put("dateOfBirth", String.valueOf(userRequest.dateOfBirth()));
            existing.setDateOfBirth(userRequest.dateOfBirth());
        }
        if (notBlank(userRequest.gender()) && !userRequest.gender().equals(existing.getGender())) {
            changes.put("gender", existing.getGender() + " → " + userRequest.gender());
            existing.setGender(userRequest.gender());
        }
        if (notBlank(userRequest.address()) && !userRequest.address().equals(existing.getAddress())) {
            changes.put("address", "updated");
            existing.setAddress(userRequest.address());
        }
        // Profile picture has a 3-way semantic: non-null + non-blank
        // means "set", empty string means "clear" (the profile page's
        // Remove button sends ""), null/missing means "no change".
        // This deviates from the notBlank pattern used by other fields
        // because we explicitly need a way for the user to remove the
        // picture and revert to the initials avatar. Other text fields
        // (firstName, address, …) don't have a "clear" UX so they
        // stick to notBlank.
        if (userRequest.profilePictureUrl() != null) {
            String incoming = userRequest.profilePictureUrl();
            if (incoming.isBlank()) {
                if (existing.getProfilePictureUrl() != null) {
                    existing.setProfilePictureUrl(null);
                    changes.put("profilePictureUrl", "cleared");
                }
            } else if (!incoming.equals(existing.getProfilePictureUrl())) {
                existing.setProfilePictureUrl(incoming);
                changes.put("profilePictureUrl", "updated");
            }
        }
        // Same notBlank-then-only-if-changed pattern as gender/address so
        // the change-event payload stays meaningful.
        if (notBlank(userRequest.maritalStatus()) && !userRequest.maritalStatus().equals(existing.getMaritalStatus())) {
            changes.put("maritalStatus", existing.getMaritalStatus() + " → " + userRequest.maritalStatus());
            existing.setMaritalStatus(userRequest.maritalStatus());
        }
        if (notBlank(userRequest.tenantType()) && !userRequest.tenantType().equals(existing.getTenantType())) {
            changes.put("tenantType", existing.getTenantType() + " → " + userRequest.tenantType());
            existing.setTenantType(userRequest.tenantType());
        }

        if (changes.isEmpty()) {
            log.info("updateUser id={}: no-op (no fields changed)", userId);
            return UserMapper.toDto(existing);
        }

        existing.setUpdatedAt(LocalDateTime.now());
        User saved = userRepo.save(existing);

        userServiceEvent.sendUserProfileUpdated(UserProfileUpdatedEvent.builder()
                .eventType("user.profile.updated")
                .userId(saved.getId())
                // authUserId / email / phone let notification-service
                // sync its preference row keyed on the JWT subject
                // without a follow-up Feign call. SMS + WhatsApp light
                // up as soon as the tenant fills the phone field.
                .authUserId(saved.getAuthUserId())
                .email(saved.getEmail())
                .phone(saved.getPhone())
                .changes(changes.toString())
                .timestamp(Instant.now())
                .build());

        return UserMapper.toDto(saved);
    }

    @Override
    public List<UserResponseDto> searchUserByParam(String param) {
        if (param == null || param.isBlank()) return List.of();
        List<User> users;
        if (param.matches("^\\+?[0-9\\- ]{7,20}$")) {
            users = userRepo.findByPhoneAndIsDeletedFalse(param);
        } else if (param.contains("@")) {
            users = userRepo.findFirstByEmailIgnoreCaseAndIsDeletedFalse(param)
                    .map(List::of).orElse(List.of());
        } else {
            users = userRepo.findByFirstNameContainingIgnoreCaseAndIsDeletedFalse(param);
        }
        return users.stream().map(UserMapper::toDto).toList();
    }

    @Override
    @Transactional
    public UserResponseDto uploadUserDocument(String userId, MultipartFile file, String type) throws IOException {
        if (type == null || !ALLOWED_DOCUMENT_TYPES.contains(type.toUpperCase(Locale.ROOT))) {
            throw new InvalidDocumentTypeException(
                    "Document type must be one of " + ALLOWED_DOCUMENT_TYPES + " but was: " + type);
        }
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Uploaded file must not be empty");
        }
        if (file.getSize() > MAX_DOC_BYTES) {
            throw new IllegalArgumentException("Uploaded file exceeds 5 MB limit");
        }
        String ct = file.getContentType();
        if (ct == null || !ALLOWED_DOC_CONTENT_TYPES.contains(ct)) {
            throw new IllegalArgumentException("Unsupported document content-type: " + ct);
        }

        User user = userRepo.findActiveById(userId).orElseThrow(
                () -> new RecordNotFound("User not found with id: " + userId));

        String safeOriginal = file.getOriginalFilename() == null
                ? "doc" : file.getOriginalFilename().replaceAll("[^A-Za-z0-9._-]", "_");
        String fileName = UUID.randomUUID() + "_" + safeOriginal;
        Path dir = Paths.get(uploadDir);
        Files.createDirectories(dir);
        Path target = dir.resolve(fileName);
        Files.write(target, file.getBytes());

        if ("PROFILE".equalsIgnoreCase(type)) {
            user.setProfilePictureUrl(target.toString());
        } else { // ID_PROOF
            user.setIdProofUrl(target.toString());
        }
        user.setUpdatedAt(LocalDateTime.now());
        User saved = userRepo.save(user);
        log.info("Uploaded {} document for user={} at {}", type, userId, target);
        return UserMapper.toDto(saved);
    }

    @Override
    public List<usersByRoleDto> getUserByRole(String roleName) {
        log.info("getUserByRole role={}", roleName);

        List<authResponseDto> authUsers = authServiceFeig.getUserByRole(roleName);
        if (authUsers == null || authUsers.isEmpty()) {
            log.info("Auth service returned no users for role {}", roleName);
            return List.of();
        }

        // Lenient outer-join: every auth user with the requested role is
        // surfaced. If a matching user-service profile exists we hydrate
        // the row with its richer fields (firstName, address, etc.); if
        // not we fall back to a "shell" record using only the auth-side
        // data plus a best-effort firstName/lastName split of userName.
        //
        // The previous inner-join behaviour silently dropped auth users
        // who had no profile yet — most visibly the demo seed accounts
        // and any user whose registration's downstream Feign create
        // fell into the circuit-breaker fallback. Owner-side dropdowns
        // ("Assign tenant") then read empty even though the tenants
        // existed in auth.
        return authUsers.stream()
                .filter(a -> a.getId() != null)
                .map(a -> {
                    String authId = a.getId();
                    User u = userRepo
                            .findFirstByAuthUserIdAndIsDeletedFalse(authId)
                            .orElse(null);

                    if (u != null) {
                        return new usersByRoleDto(
                                u.getId(),
                                u.getAuthUserId(),
                                u.getFirstName(),
                                u.getLastName(),
                                u.getEmail(),
                                u.getPhone(),
                                u.getDateOfBirth(),
                                u.getGender(),
                                u.getAddress(),
                                a.getUserName(),
                                a.getUserRole());
                    }

                    // No profile yet — synthesise from auth payload.
                    String[] split = splitName(a.getUserName());
                    return new usersByRoleDto(
                            null,
                            authId,
                            split[0],
                            split[1],
                            a.getEmail(),
                            null,
                            null,
                            null,
                            null,
                            a.getUserName(),
                            a.getUserRole());
                })
                .toList();
    }

    private static String[] splitName(String userName) {
        if (userName == null || userName.isBlank()) return new String[] {null, null};
        String[] parts = userName.trim().split("\\s+", 2);
        return new String[] {parts[0], parts.length > 1 ? parts[1] : null};
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }
}

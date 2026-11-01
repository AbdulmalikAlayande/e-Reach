package ng.ereach.facilityserver.iam.dtos;

import ng.ereach.facilityserver.iam.entities.UserAccount;

import java.time.Instant;

/** What the client may see of an account: never a password, a PIN or either hash. */
public record UserAccountResponse(
        String id,
        String kind,
        String username,
        String fullName,
        String email,
        String phone,
        String status,
        boolean platformOperator,
        Instant createdAt,
        Instant updatedAt,
        String createdBy,
        String updatedBy
) {

    public static UserAccountResponse fromEntity(UserAccount savedEntity) {
        return new UserAccountResponse(
            savedEntity.getId().toString(),
            savedEntity.getKind().getKind(),
            savedEntity.getUsername(),
            savedEntity.getFullName(),
            savedEntity.getEmail(),
            savedEntity.getPhone(),
            savedEntity.getStatus().getStatus(),
            savedEntity.isPlatformOperator(),
            savedEntity.getCreatedAt(),
            savedEntity.getUpdatedAt(),
            savedEntity.getCreatedBy().toString(),
            savedEntity.getUpdatedBy().toString()
        );
    }
}

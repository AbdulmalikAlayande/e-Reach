package ng.ereach.facilityserver.iam.dtos;

import ng.ereach.facilityserver.iam.entities.UserAccount;
import ng.ereach.facilityserver.iam.entities.enums.UserAccountStatus;

import java.time.Instant;

public record UserAccountResponse (
        String kind,
        String fullName,
        String phone,
        UserAccountStatus status,
        Instant createdAt,
        Instant updatedAt,
        String createdBy,
        String updatedBy
) {

    public static UserAccountResponse fromEntity(UserAccount savedEntity) {
        if (savedEntity == null) return null;

        return new UserAccountResponse(
            savedEntity.getKind().getKind(),
            savedEntity.getFullName(),
            savedEntity.getPhone(),
            savedEntity.getStatus(),
            savedEntity.getCreatedAt(),
            savedEntity.getUpdatedAt(),
            savedEntity.getCreatedBy().toString(),
            savedEntity.getUpdatedBy().toString()
        );
    }
}

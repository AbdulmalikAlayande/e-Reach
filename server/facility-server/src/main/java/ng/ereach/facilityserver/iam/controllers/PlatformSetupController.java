package ng.ereach.facilityserver.iam.controllers;

import jakarta.validation.Valid;
import ng.ereach.facilityserver.iam.dtos.CreatePlatformOperatorRequest;
import ng.ereach.facilityserver.iam.dtos.MfaConfirmRequest;
import ng.ereach.facilityserver.iam.dtos.MfaResponse;
import ng.ereach.facilityserver.iam.dtos.MfaSetupRequest;
import ng.ereach.facilityserver.iam.dtos.UserAccountResponse;
import ng.ereach.facilityserver.iam.services.PlatformSetupService;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The start screen's "set up a new facility server" path (D-029, D-031). Open without sign-in, but only while the
 * server has no confirmed platform operator; afterwards every endpoint answers 404. Answers hold secrets (the QR
 * image and the manual key), so none may be cached.
 */
@RestController
@RequestMapping("/api/v1/setup/platform-operator")
class PlatformSetupController {

	private final PlatformSetupService platformSetupService;

	PlatformSetupController(PlatformSetupService platformSetupService) {
		this.platformSetupService = platformSetupService;
	}

	@PostMapping
	ResponseEntity<UserAccountResponse> createPlatformOperator(@Valid @RequestBody CreatePlatformOperatorRequest request) {
		return ResponseEntity.status(HttpStatus.CREATED).cacheControl(CacheControl.noStore())
				.body(platformSetupService.createPlatformOperator(request));
	}

	@PostMapping("/mfa")
	ResponseEntity<MfaResponse> startMfaSetup(@Valid @RequestBody MfaSetupRequest request) {
		return ResponseEntity.ok().cacheControl(CacheControl.noStore())
				.body(platformSetupService.startMfaSetup(request.method()));
	}

	@PostMapping("/mfa/confirm")
	ResponseEntity<MfaResponse> confirmMfa(@Valid @RequestBody MfaConfirmRequest request) {
		return ResponseEntity.ok().cacheControl(CacheControl.noStore())
				.body(platformSetupService.confirmMfa(request.method(), request.code()));
	}
}

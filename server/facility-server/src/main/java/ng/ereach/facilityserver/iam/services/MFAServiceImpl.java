package ng.ereach.facilityserver.iam.services;

import ng.ereach.facilityserver.iam.dtos.MfaResponse;
import ng.ereach.facilityserver.iam.entities.UserAccount;
import ng.ereach.facilityserver.iam.entities.enums.MfaMethodType;
import ng.ereach.facilityserver.iam.errors.MfaMethodNotAvailableException;
import org.springframework.stereotype.Service;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** Sends each MFA step to the handler of the method the user chose (D-026). */
@Service
class MFAServiceImpl implements MFAService {

    private final Map<MfaMethodType, MfaMethodHandler> handlers = new EnumMap<>(MfaMethodType.class);

    MFAServiceImpl(List<MfaMethodHandler> handlers) {
        handlers.forEach(handler -> this.handlers.put(handler.type(), handler));
    }

    @Override
    public MfaResponse setupMfa(UserAccount account, String method) {
        return handlerFor(method).setup(account);
    }

    @Override
    public MfaResponse confirmMfa(UserAccount account, String method, String code) {
        return handlerFor(method).confirm(account, code);
    }

    @Override
    public MfaResponse verifyMfa(UserAccount account, String method, String code) {
        return handlerFor(method).verify(account, code);
    }

    private MfaMethodHandler handlerFor(String method) {
        MfaMethodType type = MfaMethodType.fromMethodType(method);
        MfaMethodHandler handler = handlers.get(type);
        if (handler == null) {
            throw new MfaMethodNotAvailableException("This MFA method is not available yet. Use an authenticator app.");
        }
        return handler;
    }
}

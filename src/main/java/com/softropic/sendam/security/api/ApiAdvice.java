package com.softropic.sendam.security.api;


import com.softropic.sendam.client.contract.exception.CancelNotAllowedException;
import com.softropic.sendam.client.contract.exception.DuplicateTransactionIdException;
import com.softropic.sendam.client.contract.exception.InsufficientBalanceException;
import com.softropic.sendam.client.contract.exception.ProviderUnavailableException;
import com.softropic.sendam.client.contract.exception.RateLimitExceededException;
import com.softropic.sendam.client.contract.exception.SmsValidationException;
import com.softropic.sendam.client.contract.exception.TopupAlreadyProcessedException;
import jakarta.persistence.LockTimeoutException;
import org.springframework.dao.CannotAcquireLockException;
import com.softropic.sendam.common.exception.ApplicationException;
import com.softropic.sendam.common.exception.ResourceNotFoundException;
import com.softropic.sendam.common.message.ErrorDto;
import com.softropic.sendam.common.message.ErrorMsg;
import com.softropic.sendam.security.contract.event.SecurityAlertEvent;
import com.softropic.sendam.security.common.event.BadCredentialsEvent;
import com.softropic.sendam.security.contract.exception.ProfileActionException;
import com.softropic.sendam.security.contract.exception.AuthorizationException;
import com.softropic.sendam.security.contract.exception.InvalidJWTDataException;
import com.softropic.sendam.security.contract.exception.JWTExpiredException;
import com.softropic.sendam.security.contract.exception.JWTTheftException;
import com.softropic.sendam.security.contract.exception.OperationNotAllowedException;
import com.softropic.sendam.security.contract.exception.SecException;
import com.softropic.sendam.security.common.util.RequestMetadataProvider;
import com.softropic.sendam.security.common.event.FraudEvent;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.MessageSource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AccountExpiredException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.CredentialsExpiredException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.sqids.Sqids;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;

import static net.logstash.logback.argument.StructuredArguments.entries;

/**
 * As the name implies, this class handles exceptions thrown at the controller level, however, this class can be indirectly invoked from filters.
 * Pass an object of type HandlerExceptionResolver through the filter constructor;
 * See 'JWTAuthenticationFilter'
 */

@Slf4j
@RestControllerAdvice
public class ApiAdvice {
    private static final Sqids SQIDS = Sqids.builder().alphabet("ZG8K7aeb9hALF3OcTw5SNMQqC1oVJvtEsljDnIfx0zyH2rdRpmYUkP46guXiBW").build();

    @Autowired
    private MessageSource messageSource;

    @Autowired
    private ApplicationEventPublisher publisher;

    /**
     * Default handler for all exception which are not caught by the handlers below.
     *
     * @param exception occurred exception
     * @return error message
     */
    @ExceptionHandler(Throwable.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR) //unknown exceptions should be treated as internal errors
    public ErrorDto defaultErrorHandler(final Exception exception) {
        final String defaultMsg = "An unknown Exception has occurred";
        return logErrorAndReturnDTO(exception, defaultMsg,"generic.unknown");
    }

    //This seems to be the best way to handle constraint exceptions that cannot be caught and handled programmatically before db-write attempts
    @ExceptionHandler(DataIntegrityViolationException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST) //unknown exceptions should be treated as internal errors
    public ErrorDto integrityViolationHandler(final DataIntegrityViolationException dive) {
        final Throwable throwable = dive.getCause();
        if(throwable instanceof org.hibernate.exception.ConstraintViolationException cve){
            final String constraintName = cve.getConstraintName();
            final String defaultMsg = "Constraint Error";
            return logErrorAndReturnDTO(dive, constraintName, defaultMsg);
        }
        return logErrorAndReturnDTO(throwable, "Data integrity error", "generic.dataError");
    }

    /**
     * Handles all auth exceptions caused by non-loggedin users or that lead to a user being logged out
     * @param runtimeException
     * @return
     */
    @ExceptionHandler({
                        AuthorizationException.class,
                      })
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public ErrorDto handleAuthException(final AuthorizationException runtimeException) {
        final String defaultMsg = "Unauthorized Access.";
        return handleSecErrorAndReturnDTO(runtimeException, defaultMsg, "security.unauthorized");
    }

    @ExceptionHandler({
                        AuthenticationException.class
                      })
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public ErrorDto handleAuthException(final AuthenticationException authenException) {
        final String defaultMsg = "Authentication issue occurred.";
        return handleSecErrorAndReturnDTO(authenException, defaultMsg, "security.authError");
    }

    @ExceptionHandler({ AccountExpiredException.class})
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public ErrorDto accountExpired(final AccountExpiredException accountExpiredException) {
        final String defaultMsg = "Your account has expired.";
        return handleSecErrorAndReturnDTO(accountExpiredException, defaultMsg, "security.accountExpired");
    }

    @ExceptionHandler({ CredentialsExpiredException.class})
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public ErrorDto credentialsExpired(final CredentialsExpiredException runtimeException) {
        final String defaultMsg = "Your credentials have expired.";
        return handleSecErrorAndReturnDTO(runtimeException, defaultMsg, "security.credExpired");
    }

    @ExceptionHandler({ DisabledException.class})
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public ErrorDto accountNotEnabled(final DisabledException runtimeException) {
        final String defaultMsg = "Your account is not enabled.";
        return handleSecErrorAndReturnDTO(runtimeException, defaultMsg, "security.accNotEnabled");
    }

    @ExceptionHandler({ LockedException.class})
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public ErrorDto accountLocked(final LockedException runtimeException) {
        final String defaultMsg = "There is an issue with your account. Check your email and contact the support team. Remember to save the help code.";
        return handleSecErrorAndReturnDTO(runtimeException, defaultMsg, "security.accLocked");
    }

    @ExceptionHandler({
                        BadCredentialsException.class
                      })
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public ErrorDto handleBadCreds(final BadCredentialsException runtimeException) {
        final String defaultMsg = "The login and password combination does not exist";
        publisher.publishEvent(new BadCredentialsEvent(defaultMsg));
        return handleSecErrorAndReturnDTO(runtimeException, defaultMsg, "security.badCreds");
    }

    @ExceptionHandler({
                        UsernameNotFoundException.class
                      })
    @ResponseStatus(HttpStatus.OK)
    public ErrorDto handleUserNotFound(final UsernameNotFoundException runtimeException) {
        final String defaultMsg = "The login and password combination does not exist";
        return handleSecErrorAndReturnDTO(runtimeException, defaultMsg, "security.badCreds"); //(runtimeException, defaultMsg, "login.success");
        //This option may be more secure but the UX is not that good
        //return new Success(errorDTO.getHelpCode(), "login.success", "Check your email for the sent OTP.", Map.of()); //This message should be same as when a login is successful. Send an email stating the error

    }

    @ExceptionHandler({
            AccessDeniedException.class
    })
    @ResponseStatus(HttpStatus.FORBIDDEN) //could occur when method security throws exception
    public ErrorDto accessDeniedHandler(final AccessDeniedException exception) {
        final String defaultMsg = "You do not have the required rights. You can contact help desk";
        final ErrorDto errorDTO = logErrorAndReturnDTO(exception, defaultMsg, "security.unauthorized");
        publisher.publishEvent(new SecurityAlertEvent(exception, errorDTO.getHelpCode()));
        return errorDTO;
    }

    @ExceptionHandler({
            OperationNotAllowedException.class
    })
    @ResponseStatus(HttpStatus.FORBIDDEN) //could occur when method security throws exception
    public ErrorDto operationDeniedHandler(final OperationNotAllowedException exception) {
        final String defaultMsg = "The operation is not granted. You can contact help desk";
        return handleSecErrorAndReturnDTO(exception, defaultMsg, "security.opForbidden");
    }

    @ExceptionHandler({
            InvalidJWTDataException.class,
            JWTTheftException.class
    })
    @ResponseStatus(HttpStatus.FORBIDDEN) //could occur when method security throws exception
    public ErrorDto fraudHandler(final AuthorizationException exception) {
        publisher.publishEvent(new FraudEvent("AuthorizationException of type %s has been thrown.".formatted(exception.getClass().getSimpleName())));
        final String defaultMsg = "Access has been denied. You can contact help desk";
        return handleSecErrorAndReturnDTO(exception, defaultMsg, "security.opForbidden");
    }

    @ExceptionHandler({
            ProfileActionException.class
    })
    @ResponseStatus(HttpStatus.FORBIDDEN) //could occur when method security throws exception
    public ErrorDto changeDenialHandler(final ProfileActionException exception) {
        final String defaultMsg = "Access has been denied. You can contact help desk";
        return handleSecErrorAndReturnDTO(exception, defaultMsg, "security.opForbidden");
    }

    @ExceptionHandler(JWTExpiredException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED) //could occur when method security throws exception
    public ErrorDto jwtExpirationHandler(final JWTExpiredException exception) {
        final String defaultMsg = "Your session is no longer valid. You need to sign-in again";
        return handleSecErrorAndReturnDTO(exception, defaultMsg, "security.sessionExpired");
    }

    @ExceptionHandler(SecException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED) //could occur when method security throws exception
    public ErrorDto secExceptionHandler(final SecException exception) {
        final String defaultMsg = "Internal unknown exception. You can contact help desk with your help code";
        return handleSecErrorAndReturnDTO(exception, defaultMsg, "security.generic");
    }


    /**
     * Exception occurs when Spring cannot convert parameters into required type. E.g. "abcd" into UUID
     *
     * @param matme occurred exception
     * @return error message
     */
    @ExceptionHandler({MethodArgumentTypeMismatchException.class})
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorDto typeMismatchExceptionHandler(final MethodArgumentTypeMismatchException matme) {
        final String defaultMsg = "Method argument mismatch.";
        final String paramName = getParameterNameNullSafe(matme.getName());
        return logErrorAndReturnDTO(matme, defaultMsg, "validation.badRequest", paramName);
    }

    /**
     * Missing parameter exception handler.
     *
     * @param msrpe missing parameter exception
     * @return error message
     */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorDto missingParameterExceptionHandler(final MissingServletRequestParameterException msrpe) {
        final String defaultMsg = "Missing parameter.";
        final String paramName = getParameterNameNullSafe(msrpe.getParameterName());
        return logErrorAndReturnDTO(msrpe, defaultMsg, "validation.badRequest", paramName);
    }

    /**
     * Invalid request body payload handler. E.g. invalid json format.
     *
     * @param hmnre invalid payload exception
     * @return error message
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorDto httpMessageNotReadableExceptionHandler(final HttpMessageNotReadableException hmnre) {
        final String defaultMsg = "Payload is not readable.";
        return logErrorAndReturnDTO(hmnre, defaultMsg, "validation.badRequest");
    }

    /**
     * Handles the exception thrown when validation on an argument annotated with @Valid fails
     * It is a spring specific exception for bean validation (JSR-303, 330, 380)
     * @param manve MethodArgumentNotValidException
     * @return errorMap
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorDto methodArgumentNotValidExceptionHandler(final MethodArgumentNotValidException manve) {
        final String helpCode = logError(manve, "Data validation error");
        final BindingResult bindingResult = manve.getBindingResult();
        return processFieldErrors(helpCode, bindingResult.getFieldErrors());
    }

    /**
     * Handles validation exceptions that are thrown by the JPA impl (e.g. hibernate) just before any C, R, U, or D
     * If the input is validated earlier at a method level, then MethodArgumentNotValidException is thrown
     * @param cve
     * @return
     */
    @ExceptionHandler(ConstraintViolationException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorDto constraintViolationExceptionHandler(final ConstraintViolationException cve) {
        final String defaultMsg = "Data violation error";
        final Optional<ConstraintViolation<?>> violationOpt = cve.getConstraintViolations().stream().findFirst();
        final String msgKey = violationOpt.map(ConstraintViolation::getMessageTemplate).orElse(null);
        return logErrorAndReturnDTO(cve, defaultMsg, msgKey);
    }

    private ErrorDto processFieldErrors(String helpCode, List<FieldError> fieldErrors) {
        final ErrorDto dto = new ErrorDto(helpCode, new ErrorMsg("validation.invalidData", "Invalid Data"));
        final String chosenLang = RequestMetadataProvider.getClientInfo().getChosenLang();

        fieldErrors = deduplicate(fieldErrors);
        String message;
        String errorKey;
        String fallbackMessage;
        String field;
        final String defaultKeyPrefix = "invalid.";
        for (final FieldError fieldError : fieldErrors) {
            field = fieldError.getField();
            String defaultMsg = fieldError.getDefaultMessage();

            // Check if defaultMessage contains our custom format: "errorKey|fallbackMessage"
            if (defaultMsg != null && defaultMsg.contains("|")) {
                String[] parts = defaultMsg.split("\\|", 2);
                errorKey = parts[0];
                fallbackMessage = parts.length > 1 ? parts[1] : defaultMsg;
            } else {
                // Standard validation: use "invalid.fieldName" as key
                errorKey = defaultKeyPrefix + field;
                fallbackMessage = defaultMsg;
            }

            // Look up translated message using the errorKey
            message = messageSource.getMessage(errorKey,
                                               null,
                                               fallbackMessage,
                                               Locale.forLanguageTag(chosenLang));

            dto.add(fieldError.getObjectName(), field, new ErrorMsg(errorKey, message));
        }
        return dto;
    }


    @ExceptionHandler(ResourceNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ErrorDto resourceNotFoundExceptionHandler(final ResourceNotFoundException rnfe) {
        final String defaultMsg = "The resource cannot be found";
        final String resourceName = rnfe.getResourceName();
        return logErrorAndReturnDTO(rnfe, defaultMsg, rnfe.getErrorCode().getErrorCode(), resourceName);
    }

    /**
     * Handles InsufficientBalanceException thrown when a debit would reduce a client's balance below zero.
     *
     * @param exception InsufficientBalanceException with clientId, currentBalance, requestedAmount
     * @return 400 Bad Request with error_code INSUFFICIENT_CLIENT_BALANCE
     */
    @ExceptionHandler(InsufficientBalanceException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorDto insufficientBalanceHandler(final InsufficientBalanceException exception) {
        final String defaultMsg = "Insufficient credit balance to complete the operation.";
        return logErrorAndReturnDTO(exception, defaultMsg, "INSUFFICIENT_CLIENT_BALANCE");
    }

    /**
     * Handles DuplicateTransactionIdException thrown when a client submits a top-up
     * with a transaction_id that already exists for that client.
     *
     * @param exception DuplicateTransactionIdException
     * @return 409 Conflict with error_code DUPLICATE_TRANSACTION_ID, retryable=false
     */
    @ExceptionHandler(DuplicateTransactionIdException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ErrorDto duplicateTransactionIdHandler(final DuplicateTransactionIdException exception) {
        final String defaultMsg = "A top-up with this transaction_id already exists for this client.";
        return logErrorAndReturnDTO(exception, defaultMsg, "DUPLICATE_TRANSACTION_ID");
    }

    /**
     * Handles TopupAlreadyProcessedException thrown when an admin attempts to approve or
     * reject a top-up that has already been processed.
     *
     * @param exception TopupAlreadyProcessedException
     * @return 409 Conflict with error_code TOPUP_ALREADY_PROCESSED, retryable=false
     */
    @ExceptionHandler(TopupAlreadyProcessedException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ErrorDto topupAlreadyProcessedHandler(final TopupAlreadyProcessedException exception) {
        final String defaultMsg = "This top-up has already been processed and cannot be modified.";
        return logErrorAndReturnDTO(exception, defaultMsg, "TOPUP_ALREADY_PROCESSED");
    }

    /**
     * Handles SmsValidationException thrown by SmsService for input validation failures:
     * INVALID_PHONE_NUMBER, INVALID_SENDER_ID, INVALID_SCHEDULE_TIME.
     *
     * @param exception SmsValidationException with the specific SmsError code
     * @return 400 Bad Request with error_code from SmsError (e.g. INVALID_PHONE_NUMBER)
     */
    @ExceptionHandler(SmsValidationException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorDto smsValidationHandler(final SmsValidationException exception) {
        final String defaultMsg = "SMS validation failed.";
        final String errorCode = exception.getErrorCode() != null ? exception.getErrorCode().getErrorCode() : "SMS_VALIDATION_ERROR";
        return logErrorAndReturnDTO(exception, defaultMsg, errorCode);
    }

    /**
     * Handles CancelNotAllowedException thrown when a client attempts to cancel
     * an SMS request that is not in ACCEPTED status or is not a scheduled request.
     *
     * @param exception CancelNotAllowedException
     * @return 409 Conflict with error_code CANCEL_NOT_ALLOWED
     */
    @ExceptionHandler(CancelNotAllowedException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ErrorDto cancelNotAllowedHandler(final CancelNotAllowedException exception) {
        final String defaultMsg = "This SMS request cannot be cancelled.";
        return logErrorAndReturnDTO(exception, defaultMsg, "CANCEL_NOT_ALLOWED");
    }

    /**
     * Handles RateLimitExceededException thrown when the recipient rate limit
     * (1000 recipients/min) is exceeded. Returns HTTP 429 — distinct from
     * AuthorizationException which continues to return HTTP 401.
     *
     * @param exception RateLimitExceededException
     * @return 429 Too Many Requests with error_code TOO_MANY_REQUESTS
     */
    @ExceptionHandler(RateLimitExceededException.class)
    @ResponseStatus(HttpStatus.TOO_MANY_REQUESTS)
    public ErrorDto rateLimitExceededHandler(final RateLimitExceededException exception) {
        final String defaultMsg = "Rate limit exceeded. Please retry after a short delay.";
        return logErrorAndReturnDTO(exception, defaultMsg, "TOO_MANY_REQUESTS");
    }

    /**
     * Handles jakarta.persistence.LockTimeoutException thrown when CreditReservationService
     * cannot acquire the SELECT FOR UPDATE lock within the timeout window (2000ms).
     * Clients should retry the request after a short delay.
     *
     * @param exception LockTimeoutException
     * @return 503 Service Unavailable with error_code LOCK_TIMEOUT
     */
    @ExceptionHandler(LockTimeoutException.class)
    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    public ErrorDto lockTimeoutHandler(final LockTimeoutException exception) {
        final String defaultMsg = "Server temporarily busy, please retry.";
        return logErrorAndReturnDTO(exception, defaultMsg, "LOCK_TIMEOUT");
    }

    /**
     * Handles org.springframework.dao.CannotAcquireLockException, which Spring's exception
     * translation layer may wrap around LockTimeoutException from Hibernate.
     *
     * @param exception CannotAcquireLockException
     * @return 503 Service Unavailable with error_code LOCK_TIMEOUT
     */
    @ExceptionHandler(CannotAcquireLockException.class)
    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    public ErrorDto cannotAcquireLockHandler(final CannotAcquireLockException exception) {
        final String defaultMsg = "Server temporarily busy, please retry.";
        return logErrorAndReturnDTO(exception, defaultMsg, "LOCK_TIMEOUT");
    }

    /**
     * Handles ProviderUnavailableException thrown by NexahClient when the 'nexah'
     * circuit breaker is OPEN or the upstream call fails after the breaker trips.
     *
     * @param exception ProviderUnavailableException
     * @return 503 Service Unavailable with error_code PROVIDER_UNAVAILABLE
     */
    @ExceptionHandler(ProviderUnavailableException.class)
    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    public ErrorDto providerUnavailableHandler(final ProviderUnavailableException exception) {
        final String defaultMsg = "SMS provider is temporarily unavailable. Please retry shortly.";
        return logErrorAndReturnDTO(exception, defaultMsg, "PROVIDER_UNAVAILABLE");
    }


    private List<FieldError> deduplicate(List<FieldError> fieldErrors) {
        Map<String, FieldError> errorMap = new HashMap<>();
        fieldErrors.forEach(fe -> errorMap.put(fe.getField(), fe));
        return List.copyOf(errorMap.values());
    }

    private ErrorDto handleSecErrorAndReturnDTO(AuthenticationException exception, String defaultMsg, String msgKey, String... args) {
        final ErrorDto errorDTO = logErrorAndReturnDTO(exception, defaultMsg, msgKey, args);
        publisher.publishEvent(new SecurityAlertEvent(exception, errorDTO.getHelpCode()));
        return errorDTO;
    }

    private ErrorDto handleSecErrorAndReturnDTO(AuthorizationException exception, String defaultMsg, String msgKey, String... args) {
        final ErrorDto errorDTO = logErrorAndReturnDTO(exception, defaultMsg, msgKey, args);
        publisher.publishEvent(new SecurityAlertEvent(exception, errorDTO.getHelpCode()));
        return errorDTO;
    }

    private ErrorDto handleSecErrorAndReturnDTO(SecException exception, String defaultMsg, String msgKey, String... args) {
        final ErrorDto errorDTO = logErrorAndReturnDTO(exception, defaultMsg, msgKey, args);
        publisher.publishEvent(new SecurityAlertEvent(exception, errorDTO.getHelpCode()));
        return errorDTO;
    }


    private ErrorDto logErrorAndReturnDTO(Throwable throwable, String defaultMsg, String msgKey, String... args) {
        final String helpCode = logError(throwable, defaultMsg);
        return toErrorDTO(msgKey, defaultMsg, helpCode, args);
    }

    private ErrorDto toErrorDTO(final String msgKey, final String defaultMessage, String helpCode, final Object... args) {
        final String chosenLang = RequestMetadataProvider.getClientInfo().getChosenLang();
        String message = defaultMessage;
        if(StringUtils.isNotBlank(msgKey)) {
            message = messageSource.getMessage(msgKey,
                                               args,
                                               defaultMessage,
                                               Locale.forLanguageTag(chosenLang));
        }
        return new ErrorDto(helpCode, new ErrorMsg(msgKey, message));

    }

    private String getParameterNameNullSafe(final String paramName) {
        return paramName != null ? paramName : "unknown";
    }

    private String logError(Throwable throwable, String msgTemplate)  {
        String errorCode;
        Map<String, Object> ctx = new HashMap<>();
        if(throwable instanceof ApplicationException applicationException) {
            errorCode = applicationException.getSupportId();
            //Get and log the context as well
            ctx = applicationException.getLogContext();
        } else {
            errorCode = SQIDS.encode(List.of(Integer.toUnsignedLong(UUID.randomUUID().hashCode())));
        }

        final String templateWithSupportId = msgTemplate + " SUPPORT_ID: %s";
        final String fullMsg = String.format(templateWithSupportId, errorCode);
        log.error(fullMsg, entries(ctx), throwable);
        return errorCode;
    }
}

package es.idynamicsax.ostris.api;
import es.idynamicsax.ostris.core.ProtocolException; import es.idynamicsax.ostris.ledger.ProtocolProofOutboxStore.ReplayNotAllowedException; import java.util.Map; import org.springframework.http.*; import org.springframework.web.bind.annotation.*;
@RestControllerAdvice public class ProtocolExceptionHandler {@ExceptionHandler(ProtocolException.class) ResponseEntity<Map<String,String>> handle(ProtocolException e){return ResponseEntity.unprocessableEntity().body(Map.of("code",e.code(),"message",e.getMessage()));}@ExceptionHandler({org.springframework.web.bind.MethodArgumentNotValidException.class,org.springframework.http.converter.HttpMessageNotReadableException.class,org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class,IllegalArgumentException.class}) ResponseEntity<Map<String,String>> badInput(Exception e){return ResponseEntity.badRequest().body(Map.of("code","INVALID_REQUEST","message","Request input is invalid"));}
    // NOT_FOUND also covers a replay id that exists under a different tenant - deliberately
    // uniform, same anti-enumeration reasoning as everywhere else in this codebase that collapses
    // "doesn't exist" and "not yours" into one response.
    @ExceptionHandler(ReplayNotAllowedException.class) ResponseEntity<Map<String,String>> replayNotAllowed(ReplayNotAllowedException e){
        HttpStatus httpStatus = switch (e.code) {
            case "NOT_FOUND" -> HttpStatus.NOT_FOUND;
            case "OPERATOR_IDENTITY_REQUIRED" -> HttpStatus.FORBIDDEN;
            default -> HttpStatus.CONFLICT;
        };
        return ResponseEntity.status(httpStatus).body(Map.of("code", e.code, "message", "Replay is not allowed for this delivery"));
    }}

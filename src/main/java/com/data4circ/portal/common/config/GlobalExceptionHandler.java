package com.data4circ.portal.common.config;

import com.data4circ.portal.common.exception.AccessDeniedException;
import com.data4circ.portal.common.exception.EntityNotFoundException;
import com.data4circ.portal.common.exception.ValidationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.ModelAndView;

import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@ControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(EntityNotFoundException.class)
    public Object handleEntityNotFoundException(EntityNotFoundException ex, HttpServletRequest request, WebRequest webRequest) {
        logClientError("Entity not found", ex, request);

        if (isRestRequest(request)) {
            Map<String, Object> errorResponse = createErrorResponse(
                HttpStatus.NOT_FOUND.value(),
                "Resource not found",
                ex.getMessage(),
                request.getRequestURI()
            );
            return new ResponseEntity<>(errorResponse, HttpStatus.NOT_FOUND);
        } else {
            ModelAndView mav = new ModelAndView("error/404");
            mav.addObject("message", ex.getMessage());
            mav.addObject("title", "Page Not Found");
            return mav;
        }
    }

    @ExceptionHandler(AccessDeniedException.class)
    public Object handleAccessDeniedException(AccessDeniedException ex, HttpServletRequest request, WebRequest webRequest) {
        logClientError("Access denied", ex, request);

        if (isRestRequest(request)) {
            Map<String, Object> errorResponse = createErrorResponse(
                HttpStatus.FORBIDDEN.value(),
                "Access denied",
                ex.getMessage(),
                request.getRequestURI()
            );
            return new ResponseEntity<>(errorResponse, HttpStatus.FORBIDDEN);
        } else {
            ModelAndView mav = new ModelAndView("error/403");
            mav.addObject("message", ex.getMessage());
            mav.addObject("title", "Access Denied");
            return mav;
        }
    }

    /**
     * Spring Security's own {@link org.springframework.security.access.AccessDeniedException} is
     * thrown by {@code @PreAuthorize} during controller invocation, so it bypasses the security
     * filter's access-denied handling and would otherwise fall through to the generic 500 handler.
     * Render a proper 403 for an authenticated-but-unauthorized user, and send an unauthenticated
     * user to the login page (e.g. clicking an email link while logged out).
     */
    @ExceptionHandler(org.springframework.security.access.AccessDeniedException.class)
    public Object handleSpringAccessDeniedException(org.springframework.security.access.AccessDeniedException ex,
                                                    HttpServletRequest request) {
        logClientError("Access denied (security)", ex, request);

        boolean authenticated = isAuthenticated();

        if (isRestRequest(request)) {
            HttpStatus status = authenticated ? HttpStatus.FORBIDDEN : HttpStatus.UNAUTHORIZED;
            Map<String, Object> errorResponse = createErrorResponse(
                status.value(),
                "Access denied",
                authenticated ? "You do not have permission to access this resource."
                              : "Authentication required.",
                request.getRequestURI()
            );
            return new ResponseEntity<>(errorResponse, status);
        }

        // Not logged in → send to the login page rather than showing an error.
        if (!authenticated) {
            return new ModelAndView("redirect:/login");
        }

        ModelAndView mav = new ModelAndView("error/403");
        mav.addObject("message", "You do not have permission to access this page. "
                + "If this belongs to your organisation, please sign in with the correct account.");
        mav.addObject("title", "Access Denied");
        return mav;
    }

    @ExceptionHandler(ValidationException.class)
    public Object handleValidationException(ValidationException ex, HttpServletRequest request, WebRequest webRequest) {
        logClientError("Validation error", ex, request);

        if (isRestRequest(request)) {
            Map<String, Object> errorResponse = createErrorResponse(
                HttpStatus.BAD_REQUEST.value(),
                "Validation failed",
                ex.getMessage(),
                request.getRequestURI()
            );

            if (ex.hasFieldErrors()) {
                errorResponse.put("fieldErrors", ex.getFieldErrors());
            }

            return new ResponseEntity<>(errorResponse, HttpStatus.BAD_REQUEST);
        } else {
            ModelAndView mav = new ModelAndView("error/400");
            mav.addObject("message", ex.getMessage());
            mav.addObject("title", "Validation Error");
            if (ex.hasFieldErrors()) {
                mav.addObject("fieldErrors", ex.getFieldErrors());
            }
            return mav;
        }
    }

    @ExceptionHandler(DisabledException.class)
    public Object handleDisabledException(DisabledException ex, HttpServletRequest request, WebRequest webRequest) {
        logClientError("Account disabled", ex, request);

        if (isRestRequest(request)) {
            Map<String, Object> errorResponse = createErrorResponse(
                HttpStatus.FORBIDDEN.value(),
                "Account disabled",
                ex.getMessage(),
                request.getRequestURI()
            );
            return new ResponseEntity<>(errorResponse, HttpStatus.FORBIDDEN);
        } else {
            ModelAndView mav = new ModelAndView("error/403");
            mav.addObject("message", ex.getMessage());
            mav.addObject("title", "Account Unavailable");
            return mav;
        }
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public Object handleIllegalArgumentException(IllegalArgumentException ex, HttpServletRequest request, WebRequest webRequest) {
        logClientError("Illegal argument", ex, request);

        if (isRestRequest(request)) {
            Map<String, Object> errorResponse = createErrorResponse(
                HttpStatus.BAD_REQUEST.value(),
                "Invalid request",
                ex.getMessage(),
                request.getRequestURI()
            );
            return new ResponseEntity<>(errorResponse, HttpStatus.BAD_REQUEST);
        } else {
            ModelAndView mav = new ModelAndView("error/400");
            mav.addObject("message", ex.getMessage());
            mav.addObject("title", "Invalid Request");
            return mav;
        }
    }

    @ExceptionHandler(Exception.class)
    public Object handleGenericException(Exception ex, HttpServletRequest request, WebRequest webRequest) {
        String requestURI = request.getRequestURI();

        // Ignore browser noise requests (favicon, Chrome DevTools, etc.)
        if (requestURI.equals("/favicon.ico") ||
            requestURI.startsWith("/.well-known/") ||
            requestURI.contains("chrome.devtools")) {
            // Return 404 silently without logging
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }

        logError("Unexpected error", ex, request);

        if (isRestRequest(request)) {
            Map<String, Object> errorResponse = createErrorResponse(
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                "Internal server error",
                "An unexpected error occurred. Please try again later.",
                request.getRequestURI()
            );
            return new ResponseEntity<>(errorResponse, HttpStatus.INTERNAL_SERVER_ERROR);
        } else {
            ModelAndView mav = new ModelAndView("error/500");
            mav.addObject("message", "An unexpected error occurred. Please try again later.");
            mav.addObject("title", "Server Error");
            return mav;
        }
    }

    private boolean isRestRequest(HttpServletRequest request) {
        String accept = request.getHeader("Accept");
        String contentType = request.getHeader("Content-Type");
        String requestURI = request.getRequestURI();

        return (accept != null && accept.contains("application/json")) ||
               (contentType != null && contentType.contains("application/json")) ||
               requestURI.startsWith("/api/");
    }

    private Map<String, Object> createErrorResponse(int status, String error, String message, String path) {
        Map<String, Object> errorResponse = new HashMap<>();
        errorResponse.put("timestamp", LocalDateTime.now());
        errorResponse.put("status", status);
        errorResponse.put("error", error);
        errorResponse.put("message", message);
        errorResponse.put("path", path);
        return errorResponse;
    }

    private void logError(String context, Exception ex, HttpServletRequest request) {
        String username = getCurrentUsername();
        String userAgent = request.getHeader("User-Agent");
        String requestURI = request.getRequestURI();
        String method = request.getMethod();

        logger.error("{} - {} {} - User: {} - UserAgent: {} - Error: {}",
                    context, method, requestURI, username, userAgent, ex.getMessage(), ex);
    }

    /**
     * Log an expected client error (4xx) — a missing resource, an authorization denial, invalid
     * input, etc. These are normal outcomes of client requests, not server faults, so they are
     * logged at WARN without a stack trace to avoid alarming ERROR noise (and failing
     * log-scanning CI checks). Genuine server errors (5xx) still use {@link #logError} with a
     * full stack trace.
     */
    private void logClientError(String context, Exception ex, HttpServletRequest request) {
        logger.warn("{} - {} {} - User: {} - UserAgent: {} - {}",
                    context, request.getMethod(), request.getRequestURI(),
                    getCurrentUsername(), request.getHeader("User-Agent"), ex.getMessage());
    }

    private String getCurrentUsername() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated() &&
            !authentication.getName().equals("anonymousUser")) {
            return authentication.getName();
        }
        return "anonymous";
    }

    private boolean isAuthenticated() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null && authentication.isAuthenticated() &&
            !"anonymousUser".equals(authentication.getName());
    }
}
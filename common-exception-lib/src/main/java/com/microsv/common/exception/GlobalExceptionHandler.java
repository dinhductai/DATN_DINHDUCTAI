package com.microsv.common.exception;

import com.microsv.common.enumeration.ErrorCode;
import com.microsv.common.response.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.RestClientException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.io.IOException;
import java.sql.SQLException;
import java.util.Objects;

/**
 * Global Exception Handler — dùng chung cho toàn bộ microservice.
 * Tự động bắt và xử lý các lỗi phổ biến: nghiệp vụ, validation, database, file, API ngoài, v.v.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 1️⃣ Xử lý lỗi custom nghiệp vụ — do dev chủ động ném ra (extends BaseException)
     */
    @ExceptionHandler(BaseException.class)
    public ResponseEntity<ApiResponse<Object>> handleBaseException(BaseException ex) {
        ErrorCode error = ex.getErrorCode();
        log.warn("[Business Exception] {} - {}", error.getCode(), ex.getMessage());
        return ResponseEntity
                .status(error.getStatus())
                .body(ApiResponse.error(error.getCode(), ex.getMessage()));
    }

    /**
     * 2️⃣ Lỗi validate dữ liệu đầu vào (@Valid)
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Object>> handleValidationException(MethodArgumentNotValidException ex) {
        String message = Objects.requireNonNull(ex.getBindingResult().getFieldError()).getDefaultMessage();
        log.warn("[Validation Error] {}", message);
        return ResponseEntity
                .status(ErrorCode.INVALID_INPUT.getStatus())
                .body(ApiResponse.error(ErrorCode.INVALID_INPUT.getCode(), message));
    }

    /**
     * 3️⃣ Lỗi thiếu tham số trong request
     */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiResponse<Object>> handleMissingParam(MissingServletRequestParameterException ex) {
        log.warn("[Missing Param] {}", ex.getMessage());
        return ResponseEntity
                .status(ErrorCode.MISSING_PARAMETER.getStatus())
                .body(ApiResponse.error(ErrorCode.MISSING_PARAMETER.getCode(), ex.getMessage()));
    }

    /**
     * 4️⃣ Lỗi file upload quá lớn
     */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiResponse<Object>> handleFileSize(MaxUploadSizeExceededException ex) {
        log.warn("[File Upload Error] {}", ex.getMessage());
        return ResponseEntity
                .status(ErrorCode.FILE_SIZE_EXCEEDED.getStatus())
                .body(ApiResponse.error(ErrorCode.FILE_SIZE_EXCEEDED.getCode(), "File quá lớn"));
    }

    /**
     * 5️⃣ Lỗi database (mất kết nối, query lỗi, timeout)
     */
    @ExceptionHandler({DataAccessException.class, SQLException.class})
    public ResponseEntity<ApiResponse<Object>> handleDatabaseException(Exception ex) {
        log.error("[Database Error] {}", ex.getMessage(), ex);
        return ResponseEntity
                .status(ErrorCode.DATABASE_QUERY_ERROR.getStatus())
                .body(ApiResponse.error(ErrorCode.DATABASE_QUERY_ERROR.getCode(),
                        "Lỗi cơ sở dữ liệu, vui lòng thử lại sau."));
    }

    /**
     * 6️⃣ Lỗi file / IO (đọc ghi thất bại, lưu trữ file lỗi)
     */
    @ExceptionHandler(IOException.class)
    public ResponseEntity<ApiResponse<Object>> handleIOException(IOException ex) {
        log.error("[IO Error] {}", ex.getMessage(), ex);
        return ResponseEntity
                .status(ErrorCode.FILE_STORAGE_ERROR.getStatus())
                .body(ApiResponse.error(ErrorCode.FILE_STORAGE_ERROR.getCode(),
                        "Lỗi xử lý file, vui lòng thử lại."));
    }

    /**
     * 7️⃣ Lỗi khi gọi API ngoài (FeignClient, RestTemplate, WebClient, ...)
     */
    @ExceptionHandler(RestClientException.class)
    public ResponseEntity<ApiResponse<Object>> handleRestClientException(RestClientException ex) {
        log.error("[External API Error] {}", ex.getMessage(), ex);
        return ResponseEntity
                .status(ErrorCode.API_CALL_FAILED.getStatus())
                .body(ApiResponse.error(ErrorCode.API_CALL_FAILED.getCode(),
                        "Không thể kết nối đến dịch vụ bên ngoài."));
    }

    /**
     * 8️⃣ Lỗi không xác định (NullPointer, IllegalState, v.v.)
     * - Handler cuối cùng, luôn để cuối cùng trong class
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Object>> handleGenericException(Exception ex) {
        log.error("[Unhandled Exception] {}", ex.getMessage(), ex);
        return ResponseEntity
                .status(ErrorCode.INTERNAL_SERVER_ERROR.getStatus())
                .body(ApiResponse.error(ErrorCode.INTERNAL_SERVER_ERROR.getCode(),
                        "Đã xảy ra lỗi không xác định. Vui lòng thử lại sau."));
    }
}

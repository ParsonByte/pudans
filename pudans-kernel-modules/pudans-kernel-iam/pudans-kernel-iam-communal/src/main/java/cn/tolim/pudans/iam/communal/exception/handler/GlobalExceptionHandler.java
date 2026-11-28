package cn.tolim.pudans.iam.communal.exception.handler;

import cn.tolim.pudans.iam.infra.exception.PudansException;
import jakarta.validation.ConstraintViolationException;
import lombok.AllArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.web.ErrorProperties;
import org.springframework.boot.webmvc.error.DefaultErrorAttributes;
import org.springframework.http.HttpStatus;
import org.springframework.validation.BindException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.util.WebUtils;


@AllArgsConstructor
@RestControllerAdvice
public class GlobalExceptionHandler {
    private final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private final ErrorProperties errorProperties;

    // Exception
    // Returns: ModelAndView
    @ExceptionHandler(value = Exception.class)
    public ModelAndView exception(WebRequest request, Exception e) {
        request.setAttribute(WebUtils.ERROR_STATUS_CODE_ATTRIBUTE,
                HttpStatus.INTERNAL_SERVER_ERROR.value(), WebRequest.SCOPE_REQUEST);
        setExceptionAttribute(request, e);
        logger.error("全局异常捕获", e);
        return new ModelAndView(errorProperties.getPath());
    }

    // PudansException
    // Returns: ModelAndView
    @ExceptionHandler(value = PudansException.class)
    public ModelAndView pudansException(WebRequest request, PudansException e) {
        request.setAttribute(WebUtils.ERROR_STATUS_CODE_ATTRIBUTE, e.getHttpStatus().value(),
                WebRequest.SCOPE_REQUEST);
        logger.error("全局异常捕获", e);
        return new ModelAndView(errorProperties.getPath());
    }

    // BindException
    // The default HandlerExceptionResolver returns status 400; set it to 500 here and forward to the error page.
    // Returns: ModelAndView
    @ExceptionHandler(value = BindException.class)
    public ModelAndView bindException(WebRequest request, BindException e) {
        request.setAttribute(WebUtils.ERROR_STATUS_CODE_ATTRIBUTE, HttpStatus.BAD_REQUEST.value(),
                WebRequest.SCOPE_REQUEST);
        logger.error("全局异常捕获", e);
        return new ModelAndView(errorProperties.getPath());
    }

    // ConstraintViolationException
    // The default HandlerExceptionResolver returns status 400; set it to 500 here and forward to the error page.
    // Returns: ModelAndView
    @ExceptionHandler(value = ConstraintViolationException.class)
    public ModelAndView constraintViolationException(WebRequest request,
                                                     ConstraintViolationException e) {
        request.setAttribute(WebUtils.ERROR_STATUS_CODE_ATTRIBUTE, HttpStatus.BAD_REQUEST.value(),
                WebRequest.SCOPE_REQUEST);
        logger.error("全局异常捕获", e);
        return new ModelAndView(errorProperties.getPath());
    }

    private void setExceptionAttribute(WebRequest request, Exception exception) {
        request.setAttribute(WebUtils.ERROR_EXCEPTION_ATTRIBUTE, exception,
                WebRequest.SCOPE_REQUEST);
        request.setAttribute(DefaultErrorAttributes.class.getName() + ".ERROR", exception,
                WebRequest.SCOPE_REQUEST);
        request.setAttribute(WebUtils.ERROR_EXCEPTION_TYPE_ATTRIBUTE, exception.getClass(),
                WebRequest.SCOPE_REQUEST);
    }
}

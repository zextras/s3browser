package com.zextras.s3browser.web;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

@ControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(Exception.class)
    public String handleException(Exception ex, HttpServletRequest request, Model model) {
        model.addAttribute("path", request.getRequestURI());
        model.addAttribute("message", ex.getMessage() == null ? "Bilinmeyen hata" : ex.getMessage());
        return "error";
    }
}


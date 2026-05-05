package com.zextras.s3browser.web;

import com.zextras.s3browser.application.usecase.S3BrowserUseCase;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class ConnectionController {

    private final S3BrowserUseCase s3BrowserUseCase;
    private final ConnectionSessionService connectionSessionService;

    public ConnectionController(S3BrowserUseCase s3BrowserUseCase, ConnectionSessionService connectionSessionService) {
        this.s3BrowserUseCase = s3BrowserUseCase;
        this.connectionSessionService = connectionSessionService;
    }

    @GetMapping({"/", "/connect"})
    public String connectPage(HttpSession session, Model model) {
        if (!model.containsAttribute("connectionForm")) {
            var saved = connectionSessionService.get(session);
            model.addAttribute("connectionForm", saved == null ? new ConnectionForm() : ConnectionForm.fromDomain(saved));
        }
        return "connect";
    }

    @PostMapping("/connect")
    public String connect(
        @Valid @ModelAttribute ConnectionForm connectionForm,
        BindingResult bindingResult,
        HttpSession session,
        RedirectAttributes redirectAttributes
    ) {
        if (bindingResult.hasErrors()) {
            return "connect";
        }

        var settings = connectionForm.toDomain();
        s3BrowserUseCase.verifyConnection(settings);
        connectionSessionService.save(session, settings);
        redirectAttributes.addFlashAttribute("success", "Baglanti basarili.");
        if (settings.defaultBucket() == null || settings.defaultBucket().isBlank()) {
            return "redirect:/buckets";
        }

        return "redirect:" + ServletUriComponentsBuilder.fromPath("/buckets/{bucket}")
            .buildAndExpand(settings.defaultBucket())
            .toUriString();
    }

    @PostMapping("/disconnect")
    public String disconnect(HttpSession session, RedirectAttributes redirectAttributes) {
        connectionSessionService.clear(session);
        redirectAttributes.addFlashAttribute("success", "Baglanti temizlendi.");
        return "redirect:/connect";
    }
}


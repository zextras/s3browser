package com.zextras.s3browser.web;

import com.zextras.s3browser.application.usecase.S3BrowserUseCase;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.util.StringUtils;

@Controller
public class ConnectionController {

    private final S3BrowserUseCase s3BrowserUseCase;
    private final ConnectionSessionService connectionSessionService;

    public ConnectionController(S3BrowserUseCase s3BrowserUseCase, ConnectionSessionService connectionSessionService) {
        this.s3BrowserUseCase = s3BrowserUseCase;
        this.connectionSessionService = connectionSessionService;
    }

    @GetMapping({"/", "/connect"})
    public String connectPage(
        @RequestParam(required = false) String connectionId,
        HttpSession session,
        Model model
    ) {
        var selectedConnectionId = StringUtils.hasText(connectionId)
            ? connectionId
            : connectionSessionService.getActiveConnectionId(session);

        if (!model.containsAttribute("connectionForm")) {
            var saved = connectionSessionService.getById(session, selectedConnectionId);
            model.addAttribute("connectionForm", saved == null ? new ConnectionForm() : ConnectionForm.fromDomain(saved));
        }

        addConnectionAttributes(model, session, selectedConnectionId);
        return "connect";
    }

    @PostMapping("/connect")
    public String connect(
        @Valid @ModelAttribute ConnectionForm connectionForm,
        BindingResult bindingResult,
        HttpSession session,
        Model model,
        RedirectAttributes redirectAttributes
    ) {
        if (bindingResult.hasErrors()) {
            addConnectionAttributes(model, session, connectionSessionService.getActiveConnectionId(session));
            return "connect";
        }

        var settings = connectionForm.toDomain();
        s3BrowserUseCase.verifyConnection(settings);
        String connectionId = connectionSessionService.save(session, settings);
        redirectAttributes.addFlashAttribute("success", "Baglanti eklendi ve aktif edildi.");
        if (settings.defaultBucket() == null || settings.defaultBucket().isBlank()) {
            return "redirect:" + ServletUriComponentsBuilder.fromPath("/buckets")
                .queryParam("connectionId", connectionId)
                .toUriString();
        }

        return "redirect:" + ServletUriComponentsBuilder.fromPath("/buckets/{bucket}")
            .queryParam("connectionId", connectionId)
            .buildAndExpand(settings.defaultBucket())
            .toUriString();
    }

    @PostMapping("/connections/select")
    public String selectConnection(
        @RequestParam String connectionId,
        HttpSession session,
        RedirectAttributes redirectAttributes
    ) {
        if (!connectionSessionService.select(session, connectionId)) {
            redirectAttributes.addFlashAttribute("error", "Baglanti bulunamadi.");
            return "redirect:/connect";
        }

        redirectAttributes.addFlashAttribute("success", "Aktif baglanti degistirildi.");
        return "redirect:" + ServletUriComponentsBuilder.fromPath("/buckets")
            .queryParam("connectionId", connectionId)
            .toUriString();
    }

    @PostMapping("/connections/{connectionId}/disconnect")
    public String disconnectOne(
        @PathVariable String connectionId,
        HttpSession session,
        RedirectAttributes redirectAttributes
    ) {
        if (!connectionSessionService.remove(session, connectionId)) {
            redirectAttributes.addFlashAttribute("error", "Baglanti bulunamadi.");
            return "redirect:/connect";
        }

        String activeConnectionId = connectionSessionService.getActiveConnectionId(session);
        redirectAttributes.addFlashAttribute("success", "Baglanti kaldirildi.");
        if (!StringUtils.hasText(activeConnectionId)) {
            return "redirect:/connect";
        }
        return "redirect:" + ServletUriComponentsBuilder.fromPath("/buckets")
            .queryParam("connectionId", activeConnectionId)
            .toUriString();
    }

    @PostMapping("/disconnect")
    public String disconnect(HttpSession session, RedirectAttributes redirectAttributes) {
        connectionSessionService.clear(session);
        redirectAttributes.addFlashAttribute("success", "Tum baglantilar temizlendi.");
        return "redirect:/connect";
    }

    private void addConnectionAttributes(Model model, HttpSession session, String selectedConnectionId) {
        var selected = connectionSessionService.getById(session, selectedConnectionId);
        model.addAttribute("connections", connectionSessionService.list(session));
        model.addAttribute("activeConnectionId", connectionSessionService.getActiveConnectionId(session));
        model.addAttribute("selectedConnectionId", selectedConnectionId);
        model.addAttribute("connectionEndpoint", selected != null && StringUtils.hasText(selected.endpointOverride()) ? selected.endpointOverride() : "AWS Default Endpoint");
        model.addAttribute("connectionRegion", selected != null ? selected.region() : "-");
        model.addAttribute("connectionAccessKey", selected != null ? maskAccessKey(selected.accessKeyId()) : "-");
    }

    private String maskAccessKey(String accessKeyId) {
        if (!StringUtils.hasText(accessKeyId)) {
            return "Default Credential Provider";
        }
        String trimmed = accessKeyId.trim();
        if (trimmed.length() <= 4) {
            return "****";
        }
        return trimmed.substring(0, 4) + "..." + trimmed.substring(trimmed.length() - 2);
    }
}


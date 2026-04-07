package com.westflow.ai.api;

import cn.dev33.satoken.annotation.SaCheckLogin;
import com.westflow.ai.model.AiCopilotAssetResponse;
import com.westflow.ai.service.AiCopilotAssetService;
import com.westflow.common.api.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * AI Copilot 附件接口。
 */
@RestController
@RequestMapping("/api/v1/ai/copilot/assets")
@SaCheckLogin
@RequiredArgsConstructor
public class AiCopilotAssetController {

    private final AiCopilotAssetService aiCopilotAssetService;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<AiCopilotAssetResponse> upload(
            @RequestPart("file") MultipartFile file,
            @RequestParam(value = "displayName", required = false) String displayName
    ) {
        return ApiResponse.success(aiCopilotAssetService.upload(file, displayName));
    }

    @GetMapping("/{fileId}/preview")
    public ResponseEntity<ByteArrayResource> preview(@PathVariable String fileId) {
        byte[] content = aiCopilotAssetService.downloadContent(fileId);
        String filename = aiCopilotAssetService.filename(fileId);
        String contentType = aiCopilotAssetService.contentType(fileId);
        MediaType mediaType = MediaType.APPLICATION_OCTET_STREAM;
        try {
            if (contentType != null && !contentType.isBlank()) {
                mediaType = MediaType.parseMediaType(contentType);
            }
        } catch (RuntimeException ignored) {
            mediaType = MediaType.APPLICATION_OCTET_STREAM;
        }
        return ResponseEntity.ok()
                .contentType(mediaType)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline().filename(filename).build().toString())
                .body(new ByteArrayResource(content));
    }
}

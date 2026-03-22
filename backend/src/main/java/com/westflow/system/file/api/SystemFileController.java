package com.westflow.system.file.api;

import cn.dev33.satoken.annotation.SaCheckLogin;
import com.westflow.common.api.ApiResponse;
import com.westflow.common.query.PageRequest;
import com.westflow.common.query.PageResponse;
import com.westflow.system.file.service.SystemFileService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 系统文件管理接口。
 */
@RestController
@RequestMapping("/api/v1/system/files")
@SaCheckLogin
@RequiredArgsConstructor
public class SystemFileController {

    private final SystemFileService systemFileService;

    @PostMapping("/page")
    public ApiResponse<PageResponse<SystemFileListItemResponse>> page(@Valid @RequestBody PageRequest request) {
        return ApiResponse.success(systemFileService.page(request));
    }

    @GetMapping("/{fileId}")
    public ApiResponse<SystemFileDetailResponse> detail(@PathVariable String fileId) {
        return ApiResponse.success(systemFileService.detail(fileId));
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<SystemFileMutationResponse> upload(
            @RequestPart("file") MultipartFile file,
            @RequestParam(value = "displayName", required = false) String displayName,
            @RequestParam(value = "remark", required = false) String remark
    ) {
        return ApiResponse.success(systemFileService.upload(file, displayName, remark));
    }

    @PutMapping("/{fileId}")
    public ApiResponse<SystemFileMutationResponse> update(
            @PathVariable String fileId,
            @Valid @RequestBody SaveSystemFileRequest request
    ) {
        return ApiResponse.success(systemFileService.update(fileId, request));
    }

    @DeleteMapping("/{fileId}")
    public ApiResponse<SystemFileMutationResponse> delete(@PathVariable String fileId) {
        return ApiResponse.success(systemFileService.delete(fileId));
    }

    @GetMapping("/{fileId}/download")
    public ResponseEntity<ByteArrayResource> download(@PathVariable String fileId) {
        byte[] content = systemFileService.downloadContent(fileId);
        String filename = systemFileService.downloadFilename(fileId);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(filename).build().toString())
                .body(new ByteArrayResource(content));
    }

    @GetMapping("/{fileId}/preview")
    public ResponseEntity<ByteArrayResource> preview(@PathVariable String fileId) {
        byte[] content = systemFileService.downloadContent(fileId);
        String filename = systemFileService.downloadFilename(fileId);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline().filename(filename).build().toString())
                .body(new ByteArrayResource(content));
    }
}

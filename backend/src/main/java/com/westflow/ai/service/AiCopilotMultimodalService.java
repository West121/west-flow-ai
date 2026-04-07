package com.westflow.ai.service;

import com.alibaba.cloud.ai.dashscope.api.DashScopeAudioTranscriptionApi;
import com.alibaba.cloud.ai.dashscope.audio.DashScopeAudioTranscriptionModel;
import com.alibaba.cloud.ai.dashscope.audio.DashScopeAudioTranscriptionOptions;
import com.alibaba.cloud.ai.dashscope.audio.transcription.AudioTranscriptionModel;
import com.westflow.ai.model.AiAttachmentRequest;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import javax.imageio.ImageIO;
import lombok.RequiredArgsConstructor;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.audio.transcription.AudioTranscriptionPrompt;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.DefaultResponseErrorHandler;
import org.springframework.web.client.RestClient;
import org.springframework.web.multipart.MultipartFile;

/**
 * AI Copilot 多模态辅助服务。
 */
@Service
@RequiredArgsConstructor
public class AiCopilotMultimodalService {

    private static final Logger log = LoggerFactory.getLogger(AiCopilotMultimodalService.class);
    private static final int PDF_TEXT_THRESHOLD = 40;
    private static final int PDF_MAX_OCR_PAGES = 5;
    private static final int MAX_IMAGE_DIMENSION = 1280;

    private final ChatClient aiCopilotChatClient;
    private final AiCopilotAssetService aiCopilotAssetService;
    private final RestClient.Builder restClientBuilder;

    private volatile AudioTranscriptionModel audioTranscriptionModel;

    @Value("${westflow.ai.copilot.vision-model:${DASHSCOPE_VISION_MODEL:qwen-vl-ocr-latest}}")
    private String visionModel;

    @Value("${westflow.ai.copilot.audio-transcription-model:${DASHSCOPE_ASR_MODEL:qwen3-asr-flash}}")
    private String audioTranscriptionModelName;

    @Value("${westflow.ai.copilot.audio-transcription-base-url:${DASHSCOPE_BASE_URL:https://dashscope.aliyuncs.com/compatible-mode}}")
    private String audioTranscriptionBaseUrl;

    @Value("${DASHSCOPE_API_KEY:}")
    private String dashscopeApiKey;

    /**
     * 提取图片中的可读文本与表单关键信息。
     */
    public String extractImageContext(List<AiAttachmentRequest> attachments) {
        long startedAt = System.nanoTime();
        List<AiAttachmentRequest> supportedAttachments = attachments == null
                ? List.of()
                : attachments.stream()
                        .filter(this::isOcrSupportedAttachment)
                        .toList();
        if (supportedAttachments.isEmpty()) {
            return "";
        }
        StringBuilder extracted = new StringBuilder();
        for (AiAttachmentRequest attachment : supportedAttachments) {
            long attachmentStartedAt = System.nanoTime();
            try {
                byte[] content = aiCopilotAssetService.downloadContent(attachment.fileId());
                String result = isPdfAttachment(attachment)
                        ? extractPdfContext(attachment, content)
                        : extractImageOcr(attachment, content, MediaType.parseMediaType(attachment.contentType()));
                if (result != null && !result.isBlank()) {
                    if (!extracted.isEmpty()) {
                        extracted.append("\n\n");
                    }
                    extracted.append("附件《")
                            .append(resolveDisplayName(attachment))
                            .append("》识别结果：\n")
                            .append(result.trim());
                }
                log.info(
                        "AI multimodal extraction completed fileId={} displayName={} contentType={} latencyMs={}",
                        attachment.fileId(),
                        resolveDisplayName(attachment),
                        attachment.contentType(),
                        Duration.ofNanos(System.nanoTime() - attachmentStartedAt).toMillis()
                );
            } catch (RuntimeException exception) {
                log.warn(
                        "AI multimodal extraction failed fileId={} contentType={} reason={}",
                        attachment.fileId(),
                        attachment.contentType(),
                        exception.getMessage()
                );
            }
        }
        log.info(
                "AI multimodal extraction finished attachmentCount={} latencyMs={}",
                supportedAttachments.size(),
                Duration.ofNanos(System.nanoTime() - startedAt).toMillis()
        );
        return extracted.toString();
    }

    /**
     * 转写音频内容。
     */
    public String transcribeAudio(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return "";
        }
        AudioTranscriptionModel model = resolveAudioTranscriptionModel();
        if (model == null) {
            log.warn("AI audio transcription skipped because dashscope api key is not configured");
            return "";
        }
        try {
            byte[] content = file.getBytes();
            ByteArrayResource resource = new ByteArrayResource(content) {
                @Override
                public String getFilename() {
                    return Optional.ofNullable(file.getOriginalFilename()).orElse("copilot-audio.wav");
                }
            };
            DashScopeAudioTranscriptionOptions.Builder optionsBuilder = DashScopeAudioTranscriptionOptions.builder()
                    .model(audioTranscriptionModelName)
                    .semanticPunctuationEnabled(true)
                    .inverseTextNormalizationEnabled(true)
                    .translationEnabled(false)
                    .transcriptionEnabled(true);
            resolveAudioFormat(file.getContentType(), file.getOriginalFilename())
                    .ifPresent(optionsBuilder::format);
            return Optional.ofNullable(model.call(new AudioTranscriptionPrompt(resource, optionsBuilder.build())))
                    .map(response -> response.getResult().getOutput())
                    .map(String::trim)
                    .orElse("");
        } catch (Exception exception) {
            log.warn(
                    "AI audio transcription failed filename={} contentType={} reason={}",
                    file.getOriginalFilename(),
                    file.getContentType(),
                    exception.getMessage()
            );
            return "";
        }
    }

    private boolean isImageAttachment(AiAttachmentRequest attachment) {
        if (attachment == null || attachment.contentType() == null) {
            return false;
        }
        return attachment.contentType().toLowerCase(Locale.ROOT).startsWith("image/");
    }

    private boolean isPdfAttachment(AiAttachmentRequest attachment) {
        if (attachment == null || attachment.contentType() == null) {
            return false;
        }
        String contentType = attachment.contentType().toLowerCase(Locale.ROOT);
        if (contentType.contains("pdf")) {
            return true;
        }
        String displayName = attachment.displayName() == null ? "" : attachment.displayName().toLowerCase(Locale.ROOT);
        return displayName.endsWith(".pdf");
    }

    private boolean isOcrSupportedAttachment(AiAttachmentRequest attachment) {
        return isImageAttachment(attachment) || isPdfAttachment(attachment);
    }

    private String extractImageOcr(AiAttachmentRequest attachment, byte[] content, MediaType mediaType) {
        MediaPayload payload = prepareImagePayload(content, mediaType);
        String result = aiCopilotChatClient.prompt()
                .options(OpenAiChatOptions.builder().model(visionModel).build())
                .user(user -> user
                        .text("""
                                请只提取图片里与审批发起相关的关键信息，并按“字段名：字段值”逐行输出。
                                只输出图片里明确可见的信息，不要解释，不要分标题，不要补充推断。
                                优先输出这些字段：
                                申请人、所属部门、请假类型、请假天数、开始时间、结束时间、请假原因、申请期间联系人、直属负责人。
                                不要输出单号、单据状态、审批备注、识别说明、页码或其他与表单发起无关的信息。
                                """)
                        .media(payload.mediaType(), new ByteArrayResource(payload.content())))
                .call()
                .content();
        return result == null ? "" : result.trim();
    }

    private String extractPdfContext(AiAttachmentRequest attachment, byte[] content) {
        try (PDDocument document = Loader.loadPDF(content)) {
            String extractedText = extractPdfText(document);
            if (extractedText.length() >= PDF_TEXT_THRESHOLD) {
                return """
                        PDF 文本直提：
                        %s
                        """.formatted(extractedText).trim();
            }
            String ocrText = extractPdfOcr(attachment, document);
            if (!ocrText.isBlank()) {
                return """
                        PDF 页面识别：
                        %s
                        """.formatted(ocrText).trim();
            }
            return extractedText;
        } catch (Exception exception) {
            log.warn(
                    "AI pdf extraction failed fileId={} displayName={} reason={}",
                    attachment.fileId(),
                    resolveDisplayName(attachment),
                    exception.getMessage()
            );
            return "";
        }
    }

    private String extractPdfText(PDDocument document) throws Exception {
        PDFTextStripper stripper = new PDFTextStripper();
        String text = Optional.ofNullable(stripper.getText(document)).orElse("").trim();
        if (text.length() <= 4000) {
            return text;
        }
        return text.substring(0, 4000).trim();
    }

    private String extractPdfOcr(AiAttachmentRequest attachment, PDDocument document) throws Exception {
        PDFRenderer renderer = new PDFRenderer(document);
        int pageCount = Math.min(document.getNumberOfPages(), PDF_MAX_OCR_PAGES);
        StringBuilder result = new StringBuilder();
        for (int pageIndex = 0; pageIndex < pageCount; pageIndex++) {
            BufferedImage image = renderer.renderImageWithDPI(pageIndex, 144, ImageType.RGB);
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            ImageIO.write(image, "png", outputStream);
            String pageText = extractImageOcr(
                    attachment,
                    outputStream.toByteArray(),
                    MediaType.IMAGE_PNG
            );
            if (!pageText.isBlank()) {
                if (!result.isEmpty()) {
                    result.append("\n\n");
                }
                result.append("第 ").append(pageIndex + 1).append(" 页：\n").append(pageText);
            }
        }
        return result.toString().trim();
    }

    private String resolveDisplayName(AiAttachmentRequest attachment) {
        if (attachment.displayName() != null && !attachment.displayName().isBlank()) {
            return attachment.displayName().trim();
        }
        return attachment.fileId();
    }

    private MediaPayload prepareImagePayload(byte[] content, MediaType mediaType) {
        if (content == null || content.length == 0) {
            return new MediaPayload(content, mediaType);
        }
        try {
            BufferedImage original = ImageIO.read(new java.io.ByteArrayInputStream(content));
            if (original == null) {
                return new MediaPayload(content, mediaType);
            }
            int width = original.getWidth();
            int height = original.getHeight();
            int maxDimension = Math.max(width, height);
            if (maxDimension <= MAX_IMAGE_DIMENSION) {
                return new MediaPayload(content, mediaType);
            }
            double scale = (double) MAX_IMAGE_DIMENSION / maxDimension;
            int targetWidth = Math.max(1, (int) Math.round(width * scale));
            int targetHeight = Math.max(1, (int) Math.round(height * scale));
            BufferedImage resized = new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_RGB);
            java.awt.Graphics2D graphics = resized.createGraphics();
            try {
                graphics.setRenderingHint(
                        java.awt.RenderingHints.KEY_INTERPOLATION,
                        java.awt.RenderingHints.VALUE_INTERPOLATION_BILINEAR
                );
                graphics.setRenderingHint(
                        java.awt.RenderingHints.KEY_RENDERING,
                        java.awt.RenderingHints.VALUE_RENDER_QUALITY
                );
                graphics.drawImage(original, 0, 0, targetWidth, targetHeight, null);
            } finally {
                graphics.dispose();
            }
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            ImageIO.write(resized, "jpg", outputStream);
            return new MediaPayload(outputStream.toByteArray(), MediaType.IMAGE_JPEG);
        } catch (Exception exception) {
            log.warn("AI multimodal image resize skipped reason={}", exception.getMessage());
            return new MediaPayload(content, mediaType);
        }
    }

    private record MediaPayload(byte[] content, MediaType mediaType) {
    }

    private AudioTranscriptionModel resolveAudioTranscriptionModel() {
        AudioTranscriptionModel cached = audioTranscriptionModel;
        if (cached != null) {
            return cached;
        }
        if (!StringUtils.hasText(dashscopeApiKey)) {
            return null;
        }
        synchronized (this) {
            if (audioTranscriptionModel == null) {
                DashScopeAudioTranscriptionApi api = DashScopeAudioTranscriptionApi.builder()
                        .apiKey(dashscopeApiKey)
                        .baseUrl(normalizeDashScopeBaseUrl(audioTranscriptionBaseUrl))
                        .model(audioTranscriptionModelName)
                        .restClientBuilder(restClientBuilder)
                        .responseErrorHandler(new DefaultResponseErrorHandler())
                        .build();
                DashScopeAudioTranscriptionOptions options = DashScopeAudioTranscriptionOptions.builder()
                        .model(audioTranscriptionModelName)
                        .semanticPunctuationEnabled(true)
                        .inverseTextNormalizationEnabled(true)
                        .translationEnabled(false)
                        .transcriptionEnabled(true)
                        .build();
                audioTranscriptionModel = new DashScopeAudioTranscriptionModel(api, options);
            }
            return audioTranscriptionModel;
        }
    }

    private Optional<DashScopeAudioTranscriptionApi.AudioFormat> resolveAudioFormat(
            String contentType,
            String filename
    ) {
        String candidate = (contentType == null ? "" : contentType).toLowerCase(Locale.ROOT);
        String lowerFilename = filename == null ? "" : filename.toLowerCase(Locale.ROOT);
        if (candidate.contains("wav") || lowerFilename.endsWith(".wav")) {
            return Optional.of(DashScopeAudioTranscriptionApi.AudioFormat.WAV);
        }
        if (candidate.contains("mpeg") || candidate.contains("mp3") || lowerFilename.endsWith(".mp3")) {
            return Optional.of(DashScopeAudioTranscriptionApi.AudioFormat.MP3);
        }
        if (candidate.contains("ogg") || candidate.contains("opus") || lowerFilename.endsWith(".opus") || lowerFilename.endsWith(".ogg")) {
            return Optional.of(DashScopeAudioTranscriptionApi.AudioFormat.OPUS);
        }
        if (candidate.contains("aac") || lowerFilename.endsWith(".aac") || lowerFilename.endsWith(".m4a")) {
            return Optional.of(DashScopeAudioTranscriptionApi.AudioFormat.AAC);
        }
        if (candidate.contains("amr") || lowerFilename.endsWith(".amr")) {
            return Optional.of(DashScopeAudioTranscriptionApi.AudioFormat.AMR);
        }
        return Optional.empty();
    }

    private String normalizeDashScopeBaseUrl(String baseUrl) {
        String normalized = StringUtils.hasText(baseUrl)
                ? baseUrl.trim()
                : "https://dashscope.aliyuncs.com";
        normalized = normalized.replaceAll("/compatible-mode(/v1)?/?$", "");
        normalized = normalized.replaceAll("/v1/?$", "");
        if (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }
}

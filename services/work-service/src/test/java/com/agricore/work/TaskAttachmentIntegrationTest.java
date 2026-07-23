package com.agricore.work;

import com.agricore.farmaccess.FarmAccessClient;
import com.agricore.farmaccess.FarmAccessException;
import com.agricore.farmaccess.FarmResourceAccess;
import com.agricore.work.infrastructure.storage.AttachmentStorageException;
import com.agricore.work.infrastructure.storage.TaskAttachmentStorage;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.net.URI;
import java.util.Arrays;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "agricore.object-storage.max-upload-size=1KB",
        "agricore.object-storage.max-attachments-per-task=2"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class TaskAttachmentIntegrationTest {

    private static final UUID FARM_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @MockitoBean
    private FarmAccessClient farmAccessClient;
    @MockitoBean
    private TaskAttachmentStorage attachmentStorage;

    @BeforeEach
    void configureDependencies() {
        when(farmAccessClient.requirePlot(any(UUID.class)))
                .thenAnswer(invocation -> new FarmResourceAccess(FARM_ID, invocation.getArgument(0)));
        when(attachmentStorage.createDownloadUrl(anyString(), anyString()))
                .thenReturn(URI.create("https://objects.example/private/photo.png?signature=redacted"));
    }

    @Test
    void uploadIsIdempotentAndAppearsInTaskListAndPrivateDownload() throws Exception {
        CreatedTask task = createTask();
        MockMultipartFile file = pngFile("field-evidence.tmp", 1);

        MvcResult firstUpload = mockMvc.perform(authenticated(multipart(
                                "/api/v1/work-tasks/{taskId}/attachments", task.id()
                        ).file(file)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.fileName").value("field-evidence.png"))
                .andExpect(jsonPath("$.contentType").value("image/png"))
                .andExpect(jsonPath("$.uploadedBy").value("worker-a"))
                .andExpect(jsonPath("$.sha256").isString())
                .andReturn();
        String attachmentId = objectMapper.readTree(firstUpload.getResponse().getContentAsString()).path("id").asText();

        mockMvc.perform(authenticated(multipart(
                                "/api/v1/work-tasks/{taskId}/attachments", task.id()
                        ).file(file)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(attachmentId));

        mockMvc.perform(authenticated(get("/api/v1/work-tasks/{taskId}", task.id())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.attachments.length()").value(1))
                .andExpect(jsonPath("$.attachments[0].id").value(attachmentId));
        mockMvc.perform(authenticated(get("/api/v1/work-tasks/{taskId}/attachments", task.id())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
        mockMvc.perform(authenticated(get(
                        "/api/v1/work-tasks/{taskId}/attachments/{attachmentId}/download",
                        task.id(),
                        attachmentId
                )))
                .andExpect(status().isFound())
                .andExpect(header().string(
                        "Location",
                        "https://objects.example/private/photo.png?signature=redacted"
                ));

        verify(attachmentStorage, times(1))
                .store(anyString(), any(), anyLong(), anyString(), anyString());
    }

    @Test
    void rejectsUnsupportedContentTerminalTasksAndAttachmentOverflowBeforeStorage() throws Exception {
        CreatedTask task = createTask();
        MockMultipartFile text = new MockMultipartFile("file", "payload.svg", "image/svg+xml", "<svg/>".getBytes());
        mockMvc.perform(authenticated(multipart(
                                "/api/v1/work-tasks/{taskId}/attachments", task.id()
                        ).file(text)))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.code").value("UNSUPPORTED_ATTACHMENT_CONTENT"));

        upload(task.id(), pngFile("first.png", 1));
        upload(task.id(), pngFile("second.png", 2));
        mockMvc.perform(authenticated(multipart(
                                "/api/v1/work-tasks/{taskId}/attachments", task.id()
                        ).file(pngFile("third.png", 3))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ATTACHMENT_LIMIT_REACHED"));
        verify(attachmentStorage, times(2))
                .store(anyString(), any(), anyLong(), anyString(), anyString());

        CreatedTask terminal = createTask();
        mockMvc.perform(authenticated(post("/api/v1/work-tasks/{taskId}/cancel", terminal.id())))
                .andExpect(status().isOk());
        mockMvc.perform(authenticated(multipart(
                                "/api/v1/work-tasks/{taskId}/attachments", terminal.id()
                        ).file(pngFile("late.png", 4))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("TASK_ATTACHMENTS_LOCKED"));
    }

    @Test
    void uploadIsSizeBoundFarmScopedAndFailsClosedWhenStorageIsUnavailable() throws Exception {
        CreatedTask task = createTask();
        byte[] oversized = Arrays.copyOf(pngBytes(1), 1025);
        mockMvc.perform(authenticated(multipart(
                                "/api/v1/work-tasks/{taskId}/attachments", task.id()
                        ).file(new MockMultipartFile("file", "large.png", "image/png", oversized))))
                .andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.code").value("ATTACHMENT_TOO_LARGE"));

        doThrow(new FarmAccessException("FARM_RESOURCE_NOT_FOUND", "Farm resource not found", 404))
                .when(farmAccessClient).requirePlot(task.plotId());
        mockMvc.perform(authenticated(multipart(
                                "/api/v1/work-tasks/{taskId}/attachments", task.id()
                        ).file(pngFile("private.png", 5))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("FARM_RESOURCE_NOT_FOUND"));
        verify(attachmentStorage, never())
                .store(anyString(), any(), anyLong(), anyString(), anyString());

        CreatedTask storageTask = createTask();
        doThrow(new AttachmentStorageException("unavailable"))
                .when(attachmentStorage).store(anyString(), any(), anyLong(), anyString(), anyString());
        mockMvc.perform(authenticated(multipart(
                                "/api/v1/work-tasks/{taskId}/attachments", storageTask.id()
                        ).file(pngFile("retry.png", 6))))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("ATTACHMENT_STORAGE_UNAVAILABLE"));
    }

    private void upload(String taskId, MockMultipartFile file) throws Exception {
        mockMvc.perform(authenticated(multipart("/api/v1/work-tasks/{taskId}/attachments", taskId).file(file)))
                .andExpect(status().isCreated());
    }

    private CreatedTask createTask() throws Exception {
        UUID plotId = UUID.randomUUID();
        MvcResult result = mockMvc.perform(authenticated(post("/api/v1/work-tasks"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "code":"ATTACH-%s",
                                  "cropCycleId":"%s",
                                  "plotId":"%s",
                                  "taskType":"INSPECTION",
                                  "title":"Capture field evidence",
                                  "priority":"MEDIUM"
                                }
                                """.formatted(UUID.randomUUID(), UUID.randomUUID(), plotId)))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        return new CreatedTask(body.path("id").asText(), plotId);
    }

    private static MockMultipartFile pngFile(String name, int marker) {
        return new MockMultipartFile("file", name, "image/png", pngBytes(marker));
    }

    private static byte[] pngBytes(int marker) {
        byte[] png = new byte[45];
        byte[] header = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 13, 0x49, 0x48, 0x44, 0x52};
        byte[] iend = {0, 0, 0, 0, 0x49, 0x45, 0x4E, 0x44, (byte) 0xAE, 0x42, 0x60, (byte) 0x82};
        System.arraycopy(header, 0, png, 0, header.length);
        png[20] = (byte) marker;
        System.arraycopy(iend, 0, png, png.length - iend.length, iend.length);
        return png;
    }

    private static <T extends MockHttpServletRequestBuilder> T authenticated(T request) {
        request.header("X-Dev-User", "worker-a");
        request.header("X-Dev-Roles", "FARM_MANAGER");
        return request;
    }

    private record CreatedTask(String id, UUID plotId) {
    }
}

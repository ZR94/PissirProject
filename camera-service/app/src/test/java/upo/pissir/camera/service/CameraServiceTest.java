package upo.pissir.camera.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class CameraServiceTest {

    @Test
    void ignoresUnsupportedDirectionWithoutThrowing() {
        CameraService service = new CameraService(null);
        String payload = """
                {
                  "type": "CAMERA_REQUEST",
                  "correlationId": "abc-123",
                  "channel": "manual"
                }
                """;

        assertDoesNotThrow(() -> service.onCameraRequest("highway/MI_Ovest/foo/camera/requests", payload));
    }
}

package com.school.service;

import com.school.repository.SchoolClassRepository;
import com.school.repository.StudentRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests du service d'import Excel : modèle, rejet des fichiers invalides.
 */
@ExtendWith(MockitoExtension.class)
class StudentImportServiceTest {

    @Mock
    private StudentRepository studentRepository;
    @Mock
    private SchoolClassRepository schoolClassRepository;
    @Mock
    private StudentService studentService;
    @Mock
    private HttpServletRequest httpRequest;

    private StudentImportService service;

    @BeforeEach
    void setUp() {
        service = new StudentImportService(studentRepository, schoolClassRepository, studentService);
    }

    @Test
    void templateIsGenerated() throws IOException {
        byte[] template = service.template();
        assertThat(template).isNotEmpty();
        assertThat(template).startsWith(new byte[]{0x50, 0x4B}); // signature ZIP/Office
    }

    @Test
    void invalidFileIsRejectedWithBusinessError() {
        MockMultipartFile bad = new MockMultipartFile("file", "notes.txt",
                "text/plain", "pas un fichier excel".getBytes(StandardCharsets.UTF_8));
        assertThatThrownBy(() -> service.importFromExcel(bad, httpRequest))
                .isInstanceOf(com.school.exception.BusinessException.class)
                .hasMessageContaining("Fichier illisible");
    }
}
package com.school.service;

import com.school.dto.response.GlobalSearchResponse;
import com.school.entity.Convocation;
import com.school.entity.FeeType;
import com.school.entity.Invoice;
import com.school.entity.Level;
import com.school.entity.SchoolClass;
import com.school.entity.Student;
import com.school.entity.Teacher;
import com.school.enums.InvoiceStatus;
import com.school.enums.StudentStatus;
import com.school.enums.TeacherStatus;
import com.school.repository.ConvocationRepository;
import com.school.repository.InvoiceRepository;
import com.school.repository.SchoolClassRepository;
import com.school.repository.StudentRepository;
import com.school.repository.TeacherRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * Recherche globale : élèves, enseignants, classes, factures, convocations.
 *
 * <p>Deux risques ici, tous deux invisibles à l'écran :</p>
 * <ul>
 *   <li><b>une requête vide doit court-circuiter</b> — sinon chaque frappe dans
 *       la barre de recherche déclenche cinq requêtes SQL ;</li>
 *   <li><b>les relations sont nullables</b> — un élève sans classe, une facture
 *       sans type de frais : sans garde, la recherche lève une NPE et <em>tout</em>
 *       le panneau reste vide, y compris les modules qui avaient des résultats.</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class SearchServiceTest {

    @Mock private StudentRepository studentRepository;
    @Mock private TeacherRepository teacherRepository;
    @Mock private SchoolClassRepository classRepository;
    @Mock private InvoiceRepository invoiceRepository;
    @Mock private ConvocationRepository convocationRepository;

    private SearchService service;

    @BeforeEach
    void setUp() {
        service = new SearchService(studentRepository, teacherRepository, classRepository,
                invoiceRepository, convocationRepository);
    }

    /**
     * Page est une interface : un mock non stubé renvoie {@code null}, pas une
     * page vide ⇒ {@code getContent()} lèverait une NPE. Les cinq dépôts doivent
     * donc être stubés dans chaque test qui appelle {@code search()}.
     */
    private void stubTout(String keyword, List<Student> students, List<Teacher> teachers,
                          List<SchoolClass> classes, List<Invoice> invoices,
                          List<Convocation> convocations) {
        when(studentRepository.search(keyword, null, null, null, PageRequest.of(0, 8)))
                .thenReturn(new PageImpl<>(students));
        when(teacherRepository.search(keyword, null, PageRequest.of(0, 8)))
                .thenReturn(new PageImpl<>(teachers));
        when(classRepository.search(keyword, null, null, PageRequest.of(0, 8)))
                .thenReturn(new PageImpl<>(classes));
        when(invoiceRepository.findTop8ByKeyword(keyword, PageRequest.of(0, 8)))
                .thenReturn(invoices);
        when(convocationRepository
                .findTop8BySubjectContainingIgnoreCaseOrReferenceContainingIgnoreCase(keyword, keyword))
                .thenReturn(convocations);
    }

    private void stubVide(String keyword) {
        stubTout(keyword, List.of(), List.of(), List.of(), List.of(), List.of());
    }

    // =====================================================================
    // Court-circuit
    // =====================================================================

    @Test
    @DisplayName("une requête nulle ne touche aucun dépôt")
    void requeteNulle() {
        GlobalSearchResponse response = service.search(null);

        assertThat(response.getStudents()).isEmpty();
        verifyNoInteractions(studentRepository, teacherRepository, classRepository,
                invoiceRepository, convocationRepository);
    }

    @Test
    @DisplayName("une requête blanche ne touche aucun dépôt")
    void requeteBlanche() {
        // La barre de recherche envoie '' puis '   ' à chaque frappe : sans ce
        // garde, cinq requêtes SQL partent pour rien à chaque caractère effacé.
        GlobalSearchResponse response = service.search("   ");

        assertThat(response.getTeachers()).isEmpty();
        verifyNoInteractions(studentRepository, teacherRepository, classRepository,
                invoiceRepository, convocationRepository);
    }

    // =====================================================================
    // Mot-clé et limites
    // =====================================================================

    @Test
    @DisplayName("le mot-clé est élagué avant d'atteindre les dépôts")
    void motCleElague() {
        stubVide("harouna");

        service.search("  harouna  ");

        verify(studentRepository).search("harouna", null, null, null, PageRequest.of(0, 8));
        verify(convocationRepository)
                .findTop8BySubjectContainingIgnoreCaseOrReferenceContainingIgnoreCase("harouna", "harouna");
    }

    @Test
    @DisplayName("chaque module renvoie au plus 8 résultats")
    void limiteA8() {
        // Sans cette borne, une recherche sur une lettre ramènerait tout
        // l'établissement et figerait le navigateur.
        stubVide("a");

        service.search("a");

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(studentRepository).search(eq("a"), isNull(), isNull(), isNull(), captor.capture());
        verify(teacherRepository).search(eq("a"), isNull(), captor.capture());
        verify(classRepository).search(eq("a"), isNull(), isNull(), captor.capture());
        verify(invoiceRepository).findTop8ByKeyword(eq("a"), captor.capture());

        assertThat(captor.getAllValues()).isNotEmpty().allSatisfy(pageable -> {
            assertThat(pageable.getPageNumber()).isZero();
            assertThat(pageable.getPageSize()).isEqualTo(8);
        });
    }

    @Test
    @DisplayName("les cinq modules sont interrogés")
    void cinqModules() {
        stubVide("baobab");

        GlobalSearchResponse response = service.search("baobab");

        verify(studentRepository).search(eq("baobab"), isNull(), isNull(), isNull(), any());
        verify(teacherRepository).search(eq("baobab"), isNull(), any());
        verify(classRepository).search(eq("baobab"), isNull(), isNull(), any());
        verify(invoiceRepository).findTop8ByKeyword(eq("baobab"), any());
        verify(convocationRepository)
                .findTop8BySubjectContainingIgnoreCaseOrReferenceContainingIgnoreCase("baobab", "baobab");
        assertThat(List.<List<?>>of(response.getStudents(), response.getTeachers(),
                response.getClasses(), response.getInvoices(), response.getConvocations()))
                .allSatisfy(List::isEmpty);
    }

    // =====================================================================
    // Projection des résultats
    // =====================================================================

    @Test
    @DisplayName("un élève expose son nom complet, son matricule, sa classe et son statut")
    void projetteEleve() {
        Student eleve = Student.builder()
                .id(7L).firstName("Aïssata").lastName("Traoré").matricule("EL-2026-007")
                .status(StudentStatus.ACTIVE)
                .schoolClass(SchoolClass.builder().id(3L).name("6e A").code("6A").build())
                .build();
        stubTout("aissata", List.of(eleve), List.of(), List.of(), List.of(), List.of());

        GlobalSearchResponse.StudentSearchHit hit = service.search("aissata").getStudents().get(0);

        assertThat(hit.getId()).isEqualTo(7L);
        assertThat(hit.getFullName()).isEqualTo("Aïssata Traoré");
        assertThat(hit.getMatricule()).isEqualTo("EL-2026-007");
        assertThat(hit.getClassName()).isEqualTo("6e A");
        assertThat(hit.getStatus()).isEqualTo("ACTIVE");
    }

    @Test
    @DisplayName("un élève sans classe n'interrompt pas la recherche")
    void eleveSansClasse() {
        // `s.getSchoolClass().getName()` sans garde lèverait une NPE et viderait
        // AUSSI les résultats des enseignants, classes, factures et convocations.
        Student eleve = Student.builder()
                .id(8L).firstName("Moussa").lastName("Keïta").matricule("EL-2026-008")
                .status(StudentStatus.ACTIVE)
                .schoolClass(null)
                .build();
        stubTout("moussa", List.of(eleve), List.of(), List.of(), List.of(), List.of());

        GlobalSearchResponse.StudentSearchHit hit = service.search("moussa").getStudents().get(0);

        assertThat(hit.getClassName()).isNull();
        assertThat(hit.getFullName()).isEqualTo("Moussa Keïta");
    }

    @Test
    @DisplayName("un élève sans statut n'interrompt pas la recherche")
    void eleveSansStatut() {
        Student eleve = Student.builder()
                .id(9L).firstName("Fatou").lastName("Diarra").matricule("EL-2026-009")
                .status(null)
                .build();
        stubTout("fatou", List.of(eleve), List.of(), List.of(), List.of(), List.of());

        assertThat(service.search("fatou").getStudents().get(0).getStatus()).isNull();
    }

    @Test
    @DisplayName("un enseignant expose son matricule employé et son statut")
    void projetteEnseignant() {
        Teacher prof = Teacher.builder()
                .id(1L).firstName("Ibrahim").lastName("Cissé").employeeNo("ENS-001")
                .status(TeacherStatus.ON_LEAVE)
                .build();
        stubTout("ibrahim", List.of(), List.of(prof), List.of(), List.of(), List.of());

        GlobalSearchResponse.TeacherSearchHit hit = service.search("ibrahim").getTeachers().get(0);

        assertThat(hit.getFullName()).isEqualTo("Ibrahim Cissé");
        assertThat(hit.getEmployeeNo()).isEqualTo("ENS-001");
        assertThat(hit.getStatus()).isEqualTo("ON_LEAVE");
    }

    @Test
    @DisplayName("une classe expose son code et son niveau, même sans niveau")
    void projetteClasse() {
        SchoolClass avecNiveau = SchoolClass.builder()
                .id(3L).name("6e A").code("6A").level(Level.builder().id(1L).name("6ème").build())
                .build();
        SchoolClass sansNiveau = SchoolClass.builder().id(4L).name("7e B").code("7B").level(null).build();
        stubTout("6", List.of(), List.of(), List.of(avecNiveau, sansNiveau), List.of(), List.of());

        List<GlobalSearchResponse.ClassSearchHit> hits = service.search("6").getClasses();

        assertThat(hits).extracting(GlobalSearchResponse.ClassSearchHit::getLevelName)
                .containsExactly("6ème", null);
        assertThat(hits).extracting(GlobalSearchResponse.ClassSearchHit::getCode)
                .containsExactly("6A", "7B");
    }

    @Test
    @DisplayName("une facture sans élève ni type de frais n'interrompt pas la recherche")
    void projetteFacture() {
        Invoice complete = Invoice.builder()
                .id(11L).invoiceNo("FAC-2026-0011")
                .student(Student.builder().id(7L).firstName("Aïssata").lastName("Traoré").build())
                .feeType(FeeType.builder().id(2L).name("Scolarité T1").build())
                .status(InvoiceStatus.OVERDUE)
                .build();
        Invoice orpheline = Invoice.builder().id(12L).invoiceNo("FAC-2026-0012")
                .student(null).feeType(null).status(InvoiceStatus.UNPAID).build();
        stubTout("fac", List.of(), List.of(), List.of(), List.of(complete, orpheline), List.of());

        List<GlobalSearchResponse.InvoiceSearchHit> hits = service.search("fac").getInvoices();

        assertThat(hits.get(0).getStudentName()).isEqualTo("Aïssata Traoré");
        assertThat(hits.get(0).getFeeTypeName()).isEqualTo("Scolarité T1");
        assertThat(hits.get(0).getStatus()).isEqualTo("OVERDUE");
        assertThat(hits.get(1).getStudentName()).isNull();
        assertThat(hits.get(1).getFeeTypeName()).isNull();
    }

    @Test
    @DisplayName("une convocation expose son objet comme nom et sa référence comme code")
    void projetteConvocation() {
        Convocation convocation = Convocation.builder()
                .id(5L).subject("Conseil de discipline").reference("CONV-2026-0005")
                .build();
        stubTout("conseil", List.of(), List.of(), List.of(), List.of(), List.of(convocation));

        GlobalSearchResponse.ConvocationSearchHit hit =
                service.search("conseil").getConvocations().get(0);

        assertThat(hit.getName()).isEqualTo("Conseil de discipline");
        assertThat(hit.getCode()).isEqualTo("CONV-2026-0005");
        assertThat(hit.getSubInfo()).isEqualTo("Convocation");
    }

    @Test
    @DisplayName("aucun résultat renvoie cinq listes vides, jamais null")
    void aucunResultat() {
        stubVide("zzz");

        GlobalSearchResponse response = service.search("zzz");

        assertThat(response.getStudents()).isNotNull().isEmpty();
        assertThat(response.getTeachers()).isNotNull().isEmpty();
        assertThat(response.getClasses()).isNotNull().isEmpty();
        assertThat(response.getInvoices()).isNotNull().isEmpty();
        assertThat(response.getConvocations()).isNotNull().isEmpty();
    }
}

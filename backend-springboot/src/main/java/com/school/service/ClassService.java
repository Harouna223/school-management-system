package com.school.service;

import com.school.dto.request.ClassRequest;
import com.school.dto.request.LevelRequest;
import com.school.dto.request.RoomRequest;
import com.school.dto.request.SectionRequest;
import com.school.dto.response.ClassResponse;
import com.school.dto.response.PageResponse;
import com.school.entity.Level;
import com.school.entity.Room;
import com.school.entity.SchoolClass;
import com.school.entity.Section;
import com.school.exception.BusinessException;
import com.school.exception.ResourceNotFoundException;
import com.school.mapper.ClassMapper;
import com.school.repository.LevelRepository;
import com.school.repository.RoomRepository;
import com.school.repository.SchoolClassRepository;
import com.school.repository.SectionRepository;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Module classes : niveaux, sections, salles, capacités.
 */
@Service
@RequiredArgsConstructor
public class ClassService {

    private final SchoolClassRepository classRepository;
    private final LevelRepository levelRepository;
    private final SectionRepository sectionRepository;
    private final RoomRepository roomRepository;
    private final ClassMapper classMapper;
    private final AuditService auditService;

    @Transactional(readOnly = true)
    public PageResponse<ClassResponse> search(String search, Long levelId, Long sectionId,
                                              int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("name").ascending());
        Page<SchoolClass> result = classRepository.search(search, levelId, sectionId, pageable);
        return PageResponse.from(result, ClassResponse::from);
    }

    @Transactional(readOnly = true)
    public List<ClassResponse> findAll() {
        return classRepository.findAll(Sort.by("name")).stream()
                .map(ClassResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public ClassResponse getById(Long id) {
        return ClassResponse.from(findById(id));
    }

    @Transactional
    public ClassResponse create(ClassRequest request, HttpServletRequest httpRequest) {
        if (classRepository.existsByCode(request.getCode())) {
            throw new BusinessException("Une classe porte déjà ce code : " + request.getCode());
        }
        SchoolClass schoolClass = classMapper.toEntity(request);
        schoolClass.setLevel(getLevel(request.getLevelId()));
        schoolClass.setSection(request.getSectionId() != null ? getSection(request.getSectionId()) : null);
        schoolClass.setRoom(request.getRoomId() != null ? getRoom(request.getRoomId()) : null);
        schoolClass.setCapacity(request.getCapacity() != null ? request.getCapacity() : 30);
        SchoolClass saved = classRepository.save(schoolClass);
        auditService.log("CREATE", "Class", saved.getId(), "Création classe " + saved.getName(), httpRequest);
        return ClassResponse.from(saved);
    }

    @Transactional
    public ClassResponse update(Long id, ClassRequest request, HttpServletRequest httpRequest) {
        SchoolClass schoolClass = findById(id);
        classMapper.updateEntity(request, schoolClass);
        schoolClass.setLevel(getLevel(request.getLevelId()));
        schoolClass.setSection(request.getSectionId() != null ? getSection(request.getSectionId()) : null);
        schoolClass.setRoom(request.getRoomId() != null ? getRoom(request.getRoomId()) : null);
        if (request.getCapacity() != null) {
            schoolClass.setCapacity(request.getCapacity());
        }
        auditService.log("UPDATE", "Class", id, "Modification classe " + schoolClass.getName(), httpRequest);
        return ClassResponse.from(classRepository.save(schoolClass));
    }

    @Transactional
    public void delete(Long id, HttpServletRequest httpRequest) {
        SchoolClass schoolClass = findById(id);
        if (!schoolClass.getStudents().isEmpty()) {
            throw new BusinessException("Impossible de supprimer : la classe contient des élèves");
        }
        auditService.log("DELETE", "Class", id, "Suppression classe " + schoolClass.getName(), httpRequest);
        classRepository.delete(schoolClass);
    }

    public SchoolClass findById(Long id) {
        return classRepository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Classe", id));
    }

    public List<Level> listLevels() {
        return levelRepository.findAll(Sort.by("name"));
    }

    public List<Section> listSections() {
        return sectionRepository.findAll(Sort.by("name"));
    }

    public List<Room> listRooms() {
        return roomRepository.findAll(Sort.by("name"));
    }

    public Level getLevel(Long id) {
        return levelRepository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Niveau", id));
    }

    public Section getSection(Long id) {
        return sectionRepository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Section", id));
    }

    public Room getRoom(Long id) {
        return roomRepository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Salle", id));
    }

    // ---------- CRUD Niveaux ----------

    @Transactional
    public Level createLevel(LevelRequest request, HttpServletRequest httpRequest) {
        if (levelRepository.findByCode(request.code()).isPresent()) {
            throw new BusinessException("Un niveau porte déjà ce code : " + request.code());
        }
        Level level = Level.builder().name(request.name()).code(request.code())
                .educationCycle(request.educationCycle()).build();
        Level saved = levelRepository.save(level);
        auditService.log("CREATE", "Level", saved.getId(), "Création niveau " + saved.getName(), httpRequest);
        return saved;
    }

    @Transactional
    public Level updateLevel(Long id, LevelRequest request, HttpServletRequest httpRequest) {
        Level level = getLevel(id);
        if (!level.getCode().equals(request.code()) && levelRepository.findByCode(request.code()).isPresent()) {
            throw new BusinessException("Un niveau porte déjà ce code : " + request.code());
        }
        level.setName(request.name());
        level.setCode(request.code());
        level.setEducationCycle(request.educationCycle());
        auditService.log("UPDATE", "Level", id, "Modification niveau " + level.getName(), httpRequest);
        return levelRepository.save(level);
    }

    @Transactional
    public void deleteLevel(Long id, HttpServletRequest httpRequest) {
        Level level = getLevel(id);
        if (classRepository.existsByLevelId(id)) {
            throw new BusinessException("Impossible de supprimer : des classes utilisent ce niveau");
        }
        auditService.log("DELETE", "Level", id, "Suppression niveau " + level.getName(), httpRequest);
        levelRepository.delete(level);
    }

    // ---------- CRUD Sections ----------

    @Transactional
    public Section createSection(SectionRequest request, HttpServletRequest httpRequest) {
        if (sectionRepository.findByName(request.name()).isPresent()) {
            throw new BusinessException("Une section porte déjà ce nom : " + request.name());
        }
        Section section = Section.builder().name(request.name()).description(request.description()).build();
        Section saved = sectionRepository.save(section);
        auditService.log("CREATE", "Section", saved.getId(), "Création section " + saved.getName(), httpRequest);
        return saved;
    }

    @Transactional
    public Section updateSection(Long id, SectionRequest request, HttpServletRequest httpRequest) {
        Section section = getSection(id);
        if (!section.getName().equals(request.name()) && sectionRepository.findByName(request.name()).isPresent()) {
            throw new BusinessException("Une section porte déjà ce nom : " + request.name());
        }
        section.setName(request.name());
        section.setDescription(request.description());
        auditService.log("UPDATE", "Section", id, "Modification section " + section.getName(), httpRequest);
        return sectionRepository.save(section);
    }

    @Transactional
    public void deleteSection(Long id, HttpServletRequest httpRequest) {
        Section section = getSection(id);
        if (classRepository.existsBySectionId(id)) {
            throw new BusinessException("Impossible de supprimer : des classes utilisent cette section");
        }
        auditService.log("DELETE", "Section", id, "Suppression section " + section.getName(), httpRequest);
        sectionRepository.delete(section);
    }

    // ---------- CRUD Salles ----------

    @Transactional
    public Room createRoom(RoomRequest request, HttpServletRequest httpRequest) {
        if (roomRepository.findByName(request.name()).isPresent()) {
            throw new BusinessException("Une salle porte déjà ce nom : " + request.name());
        }
        Room room = Room.builder().name(request.name())
                .capacity(request.capacity() != null ? request.capacity() : 30)
                .location(request.location()).build();
        Room saved = roomRepository.save(room);
        auditService.log("CREATE", "Room", saved.getId(), "Création salle " + saved.getName(), httpRequest);
        return saved;
    }

    @Transactional
    public Room updateRoom(Long id, RoomRequest request, HttpServletRequest httpRequest) {
        Room room = getRoom(id);
        if (!room.getName().equals(request.name()) && roomRepository.findByName(request.name()).isPresent()) {
            throw new BusinessException("Une salle porte déjà ce nom : " + request.name());
        }
        room.setName(request.name());
        if (request.capacity() != null) {
            room.setCapacity(request.capacity());
        }
        room.setLocation(request.location());
        auditService.log("UPDATE", "Room", id, "Modification salle " + room.getName(), httpRequest);
        return roomRepository.save(room);
    }

    @Transactional
    public void deleteRoom(Long id, HttpServletRequest httpRequest) {
        Room room = getRoom(id);
        if (classRepository.existsByRoomId(id)) {
            throw new BusinessException("Impossible de supprimer : des classes utilisent cette salle");
        }
        auditService.log("DELETE", "Room", id, "Suppression salle " + room.getName(), httpRequest);
        roomRepository.delete(room);
    }
}
package com.school.mapper;

import com.school.dto.request.StudentRequest;
import com.school.entity.Student;
import org.mapstruct.*;

/**
 * Mapper MapStruct entre Student et StudentRequest.
 * Le matricule, le statut, la classe et le parent sont gérés en service.
 */
@Mapper(componentModel = "spring")
public interface StudentMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "matricule", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "photo", ignore = true)
    @Mapping(target = "schoolClass", ignore = true)
    @Mapping(target = "parent", ignore = true)
    @Mapping(target = "user", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "enrollmentDate", ignore = true)
    Student toEntity(StudentRequest request);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "matricule", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "photo", ignore = true)
    @Mapping(target = "schoolClass", ignore = true)
    @Mapping(target = "parent", ignore = true)
    @Mapping(target = "user", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "enrollmentDate", ignore = true)
    void updateEntity(StudentRequest request, @MappingTarget Student entity);
}
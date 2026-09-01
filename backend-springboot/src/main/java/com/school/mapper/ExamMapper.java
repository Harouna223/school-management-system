package com.school.mapper;

import com.school.dto.request.ExamRequest;
import com.school.entity.Exam;
import org.mapstruct.*;

@Mapper(componentModel = "spring")
public interface ExamMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "schoolClass", ignore = true)
    @Mapping(target = "subject", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "coefficient", ignore = true)
    Exam toEntity(ExamRequest request);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "schoolClass", ignore = true)
    @Mapping(target = "subject", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "coefficient", ignore = true)
    void updateEntity(ExamRequest request, @MappingTarget Exam entity);
}
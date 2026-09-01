package com.school.mapper;

import com.school.dto.request.TeacherRequest;
import com.school.entity.Teacher;
import org.mapstruct.*;

@Mapper(componentModel = "spring")
public interface TeacherMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "employeeNo", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "photo", ignore = true)
    @Mapping(target = "user", ignore = true)
    @Mapping(target = "salary", ignore = true)
    Teacher toEntity(TeacherRequest request);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "employeeNo", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "photo", ignore = true)
    @Mapping(target = "user", ignore = true)
    @Mapping(target = "salary", ignore = true)
    void updateEntity(TeacherRequest request, @MappingTarget Teacher entity);
}
package com.school.mapper;

import com.school.dto.request.SubjectRequest;
import com.school.entity.Subject;
import org.mapstruct.*;

@Mapper(componentModel = "spring")
public interface SubjectMapper {

    @Mapping(target = "id", ignore = true)
    Subject toEntity(SubjectRequest request);

    @Mapping(target = "id", ignore = true)
    void updateEntity(SubjectRequest request, @MappingTarget Subject entity);
}
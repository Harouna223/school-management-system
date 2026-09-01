package com.school.mapper;

import com.school.dto.request.ClassRequest;
import com.school.entity.SchoolClass;
import org.mapstruct.*;

@Mapper(componentModel = "spring")
public interface ClassMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "level", ignore = true)
    @Mapping(target = "section", ignore = true)
    @Mapping(target = "room", ignore = true)
    @Mapping(target = "students", ignore = true)
    @Mapping(target = "capacity", ignore = true)
    SchoolClass toEntity(ClassRequest request);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "level", ignore = true)
    @Mapping(target = "section", ignore = true)
    @Mapping(target = "room", ignore = true)
    @Mapping(target = "students", ignore = true)
    @Mapping(target = "capacity", ignore = true)
    void updateEntity(ClassRequest request, @MappingTarget SchoolClass entity);
}
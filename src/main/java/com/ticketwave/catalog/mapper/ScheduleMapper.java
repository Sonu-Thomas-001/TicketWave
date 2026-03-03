package com.ticketwave.catalog.mapper;

import com.ticketwave.catalog.api.ScheduleSearchResult;
import com.ticketwave.catalog.domain.Schedule;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.springframework.stereotype.Component;

@Mapper(config = com.ticketwave.common.config.MapStructConfig.class)
public interface ScheduleMapper {

    @Mapping(source = "route.originCity", target = "originCity")
    @Mapping(source = "route.destinationCity", target = "destinationCity")
    @Mapping(source = "route.transportMode", target = "transportMode")
    @Mapping(target = "scheduleId", source = "id")
    @Mapping(target = "routeId", source = "route.id")
    @Mapping(target = "dynamicPrice", ignore = true)
    @Mapping(target = "priceModifier", ignore = true)
    @Mapping(target = "demandFactor", ignore = true)
    @Mapping(target = "availabilityPercentage", ignore = true)
    @Mapping(target = "durationMinutes", ignore = true)
    ScheduleSearchResult scheduleToSearchResult(Schedule schedule);
}

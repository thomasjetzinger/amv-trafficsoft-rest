package org.amv.trafficsoft.rest.contract.datapackage.model;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import lombok.Builder;
import lombok.Value;

@Value
@Builder(builderClassName = "Builder")
@JsonDeserialize(builder = XfcdParamRestDto.Builder.class)
public class XfcdParamRestDto {
    @JsonPOJOBuilder(withPrefix = "")
    public static class Builder {

    }

    private String code;
    private String name;
    private String description;
}

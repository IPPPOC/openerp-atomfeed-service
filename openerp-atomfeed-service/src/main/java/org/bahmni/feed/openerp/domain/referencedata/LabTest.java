package org.bahmni.feed.openerp.domain.referencedata;

import org.apache.log4j.Logger;
import org.bahmni.feed.openerp.ObjectMapperRepository;
import org.bahmni.openerp.web.request.builder.Parameter;
import org.codehaus.jackson.annotate.JsonIgnoreProperties;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class LabTest implements  ERPParameterizable{

    private static final Logger logger = Logger.getLogger(LabTest.class);

    private String category;
    private String name;
    private String shortName;
    private String id;
    private double salePrice;
    private boolean active;

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getShortName() {
        return shortName;
    }

    public void setShortName(String shortName) {
        this.shortName = shortName;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public double getSalePrice() {
        return salePrice;
    }

    public void setSalePrice(double salePrice) {
        this.salePrice = salePrice;
    }

    public boolean getIsActive() {
        return active;
    }

    public void setIsActive(boolean isActive) {
        this.active = isActive;
    }


    public void setClass(String klass){
        category = klass.substring(klass.lastIndexOf('.')+1);
    }

    public boolean shouldERPConsumeEvent() {
        return true;
    }

    @Override
    public List<Parameter> getParameters(String eventId, String feedURIForLastReadEntry, String feedURI) {
        List<Parameter> parameters = new ArrayList<>();

        parameters.add(new Parameter("category", "create.lab.test", "string"));
        parameters.add(new Parameter("feed_uri", feedURI, "string"));
        parameters.add(new Parameter("last_read_entry_id", eventId, "string"));
        parameters.add(new Parameter("feed_uri_for_last_read_entry", feedURIForLastReadEntry, "string"));
        try {
            parameters.add(new Parameter("lab_test", getLabTestAsJson(), "string"));
        } catch (IOException e) {
            logger.error("Cannot serialize object to json ",e);
        }

        return parameters;
    }

    private String getLabTestAsJson() throws IOException {
        return ObjectMapperRepository.objectMapper.writeValueAsString(this);
    }
}
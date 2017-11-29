package org.bahmni.feed.openerp.worker;

import org.apache.log4j.Logger;
import org.bahmni.feed.openerp.ObjectMapperRepository;
import org.bahmni.feed.openerp.OpenMRSPatientMapper;
import org.bahmni.feed.openerp.client.OpenMRSWebClient;
import org.bahmni.feed.openerp.domain.OpenMRSPatient;
import org.bahmni.feed.openerp.domain.OpenMRSPatientIdentifier;
import org.bahmni.feed.openerp.domain.OpenMRSPersonAddress;
import org.bahmni.openerp.web.client.OpenERPClient;
import org.bahmni.openerp.web.request.builder.Parameter;
import org.ict4h.atomfeed.client.domain.Event;
import org.ict4h.atomfeed.client.service.EventWorker;

import java.io.IOException;
import java.net.URI;
import java.util.*;

import static java.util.Arrays.asList;

public class OpenERPCustomerServiceEventWorker implements EventWorker {
    OpenERPClient openERPClient;
    private String feedUrl;
    private OpenMRSWebClient webClient;
    private String urlPrefix;

    private static Logger logger = Logger.getLogger(OpenERPCustomerServiceEventWorker.class);

    public OpenERPCustomerServiceEventWorker(String feedUrl, OpenERPClient openERPClient, OpenMRSWebClient webClient, String urlPrefix) {
        this.feedUrl = feedUrl;
        this.openERPClient = openERPClient;
        this.webClient = webClient;
        this.urlPrefix = urlPrefix;
    }

    @Override
    public void process(Event event) {
        try {
            Object[] customer = (Object[]) openERPClient.search("res.partner", getSearchParams(event));
            if (customer.length > 0) {
                openERPClient.execute("res.partner", "write", getWriteParams(event, customer));
            } else {
                openERPClient.execute("res.partner", "create", getParameters(event));
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private List<List<List<?>>> getSearchParams(Event event) throws IOException {
        List<HashMap> parameters = getParameters(event);

        return asList(asList(
                asList("ref", "=", parameters.get(0).get("ref")),
                asList("customer", "=", true)));
    }


    private List<HashMap> getWriteParams(Event event, Object[] customer) throws IOException {
        List writeParams = new ArrayList<>();
        List<HashMap> params = getParameters(event);

        writeParams.add(customer[0]);
        writeParams.add(params.get(0));
        return writeParams;
    }

    @Override
    public void cleanUp(Event event) {
    }


    private List<HashMap> getParameters(Event event) throws IOException {
        String content = event.getContent();
        String patientJSON = webClient.get(URI.create(urlPrefix + content));

        OpenMRSPatientMapper openMRSPatientMapper = new OpenMRSPatientMapper(ObjectMapperRepository.objectMapper);
        OpenMRSPatient openMRSPatient = openMRSPatientMapper.map(patientJSON);

        return mapParameters(openMRSPatient);
    }

    private List<HashMap> mapParameters(OpenMRSPatient openMRSPatient) {
        List<HashMap> mapList = new ArrayList<>();
        HashMap<String, String> parameters = new HashMap<>();
        parameters.put("name", openMRSPatient.getName());
        parameters.put("ref", getPrimaryIdentifier(openMRSPatient).getIdentifier());
        parameters.put("city", identifyVillage(openMRSPatient));
        mapList.add(parameters);
        return mapList;
    }

    private OpenMRSPatientIdentifier getPrimaryIdentifier(OpenMRSPatient patient) {
        for (OpenMRSPatientIdentifier identifier : patient.getIdentifiers()) {
            if (identifier.isPreferred()){
                return identifier;
            }
        }
        throw new RuntimeException("Preferred or Primary identifier is not available for the patient: "+patient.getName());
    }

    private String identifyVillage(OpenMRSPatient openMRSPatient) {
        OpenMRSPersonAddress preferredAddress = openMRSPatient.getPerson().getPreferredAddress();
        String village = (preferredAddress != null) ?  preferredAddress.getCityVillage() : "";
        return (village != null) ?  village : "";
    }


    private Parameter createParameter(String name, String value, String type) {
        return new Parameter(name, value, type);
    }

}

package org.bahmni.feed.openerp.worker;

import org.apache.log4j.Logger;
import org.bahmni.feed.openerp.ObjectMapperRepository;
import org.bahmni.feed.openerp.client.OpenMRSWebClient;
import org.bahmni.feed.openerp.domain.encounter.MapERPOrders;
import org.bahmni.feed.openerp.domain.encounter.OpenERPOrder;
import org.bahmni.feed.openerp.domain.encounter.OpenERPOrders;
import org.bahmni.feed.openerp.domain.encounter.OpenMRSEncounter;
import org.bahmni.feed.openerp.domain.visit.OpenMRSVisit;
import org.bahmni.openerp.web.client.OpenERPClient;
import org.bahmni.openerp.web.request.builder.Parameter;
import org.ict4h.atomfeed.client.domain.Event;
import org.ict4h.atomfeed.client.service.EventWorker;

import java.io.IOException;
import java.net.URI;
import java.util.*;

import static java.util.Arrays.asList;

public class OpenERPSaleOrderEventWorker implements EventWorker {
    OpenERPClient openERPClient;
    private String feedUrl;
    private OpenMRSWebClient webClient;
    private String urlPrefix;
    private OpenERPOrders openERPOrders;
    private HashMap<String, Integer> conceptToProductMapping = new HashMap<>();


    private static Logger logger = Logger.getLogger(OpenERPSaleOrderEventWorker.class);

    public OpenERPSaleOrderEventWorker(String feedUrl, OpenERPClient openERPClient, OpenMRSWebClient webClient, String urlPrefix) {
        this.feedUrl = feedUrl;
        this.openERPClient = openERPClient;
        this.webClient = webClient;
        this.urlPrefix = urlPrefix;
        mapConceptsToProducts();
    }

    private void mapConceptsToProducts() {
        conceptToProductMapping.put("1583c382-f0c6-4589-8be5-6d12b6063471", 51);  // TH Harness Spare
        conceptToProductMapping.put("caac5f28-f978-43b2-9a8c-2f3c5994988d", 51);  // TH Harness
        conceptToProductMapping.put("27efc3b7-d623-4dc3-883c-f0d00930a8c8", 50);  // TR Suspension Spare
        conceptToProductMapping.put("5c28a6a4-f6fa-4581-8fe6-e6961922e927", 50);  // TR Suspension
        conceptToProductMapping.put("6a9d979d-5b76-4683-b08e-50442bc69d55", 49);  // Transradial Right Upper Limb - Man
        conceptToProductMapping.put("5dca042b-6c50-4ec2-ab71-1b029b123a7c", 104); // Transradial Right Upper Limb - Woman/Child
        conceptToProductMapping.put("5d52c626-8cf0-490b-9c17-e0c95821ea0a", 105); // Transradial Left Upper Limb - Man
        conceptToProductMapping.put("239363a2-3874-4308-b57b-0d510233e0d4", 106); // Transradial Left Upper Limb - Woman/Child
    }

    @Override
    public void process(Event event) {
        try {

            List<Parameter> parameters = mapRequest(event);
            Object[] customer = (Object[]) openERPClient.search("res.partner", getSearchParams(parameters));

            if (customer.length > 0 && openERPOrders.getOpenERPOrders().size() > 0){
                Object[] saleOrder = (Object[]) openERPClient.search("sale.order", getSaleOrderSearchParams(parameters));
                if(saleOrder.length == 0) {
                    Object saleOrderID = createSaleOrder(customer, parameters);
                    createSaleOrderLines(saleOrderID, openERPOrders.getOpenERPOrders());
                } else {
                    updateSaleOrder(saleOrder, parameters);
                    List<OpenERPOrder> newOpenERPOrders = updateExistingSaleOrderLines((Integer) saleOrder[0]);
                    createSaleOrderLines(saleOrder[0], newOpenERPOrders);
                }
            }

        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }



    private List<OpenERPOrder> updateExistingSaleOrderLines(Integer saleOrderID) {
        Object[] existingSaleOrderLines = getSaleOrderLines(saleOrderID);
        List<OpenERPOrder> newOpenERPOrders = new ArrayList<>();
        if(existingSaleOrderLines != null && existingSaleOrderLines.length > 0) {
            List<Integer> productIdsOfOldOrders =  getProductIds(existingSaleOrderLines);
            for(OpenERPOrder openERPOrder : openERPOrders.getOpenERPOrders()) {
                Integer productId = conceptToProductMapping.get(openERPOrder.getProductId());
                if(!productIdsOfOldOrders.contains(productId)) {
                    newOpenERPOrders.add(openERPOrder);
                }
            }
            return newOpenERPOrders;
        } else {
            return openERPOrders.getOpenERPOrders();
        }
    }

    private List<Integer> getProductIds(Object[] existingSaleOrderLines) {
        ArrayList<Object> saleOrderLineList = new ArrayList<>(Arrays.asList(existingSaleOrderLines));
        List<Integer> product_ids = new ArrayList<>();
        for(Object orderLine : saleOrderLineList) {
            Object[] product_id = (Object[]) ((HashMap) orderLine).get("product_id");
            product_ids.add((Integer) product_id[0]);
        }
        return product_ids;
    }

    private Object[] getSaleOrderLines(Integer saleOrderID) {
        Vector saleOrderIds = new Vector();
        saleOrderIds.add(saleOrderID);

        Object[] read = (Object[]) openERPClient.read("sale.order", saleOrderIds, null);

        Object[] orderLines = (Object[]) ((HashMap) read[0]).get("order_line");

        if (orderLines.length > 0) {
            Vector saleOrderLineIds = new Vector();
            Collections.addAll(saleOrderLineIds, orderLines);
            return  (Object[]) openERPClient.read("sale.order.line", saleOrderLineIds, null);
        }

        return null;
    }

        private void createSaleOrderLines(Object saleOrderID, List<OpenERPOrder> openERPOrders) {
        for (OpenERPOrder openERPOrder : openERPOrders) {
            List<HashMap> saleOrderLines = new ArrayList<>();
            Integer product_id = conceptToProductMapping.get(openERPOrder.getProductId());
            if(product_id != null) {
                HashMap saleOrderLine = new HashMap();
                saleOrderLine.put("order_id", saleOrderID);
                saleOrderLine.put("product_id", product_id);
                saleOrderLine.put("product_uom_qty", 1);

                saleOrderLines.add(saleOrderLine);
                openERPClient.execute("sale.order.line", "create", saleOrderLines);
            }
        }
    }

    private Object createSaleOrder(Object[] customer, List<Parameter> parameters) {
        List<HashMap> saleOrder = new ArrayList<>();
        HashMap saleOrderMap= new HashMap();
        saleOrderMap.put("partner_id", customer[0]);
        saleOrderMap.put("client_order_ref", parameters.get(2).getValue());
        saleOrderMap.put("note", getNotes());
        saleOrder.add(saleOrderMap);

        return openERPClient.execute("sale.order", "create", saleOrder);
    }

    private void updateSaleOrder(Object[] saleOrderId, List<Parameter> parameters) {
        List saleOrder = new ArrayList<>();
        HashMap saleOrderMap= new HashMap();
        saleOrderMap.put("client_order_ref", parameters.get(2).getValue());
        saleOrderMap.put("note", getNotes());

        saleOrder.add(saleOrderId[0]);
        saleOrder.add(saleOrderMap);

        openERPClient.execute("sale.order", "write", saleOrder);
    }

    private String getNotes() {
        String notes = "";
        for(OpenERPOrder order: openERPOrders.getOpenERPOrders()){
            if (order.getNotes() != null && order.getNotes().length() != 0){
                String note = order.getProductName() + ": " + order.getNotes() + ".";
                note = String.format("%-138s", note);
                notes += note;
            }
        }
        return notes;
    }

    @Override
    public void cleanUp(Event event) {
    }

    private List<List<List<?>>> getSearchParams(List<Parameter> parameters) throws IOException {

        return asList(asList(
                asList("ref", "=", parameters.get(1).getValue()),
                asList("customer", "=", true)));
    }

    private List<List<List<?>>>     getSaleOrderSearchParams(List<Parameter> parameters) throws IOException {

        return asList(asList(
                asList("client_order_ref", "=", parameters.get(2).getValue())));
    }


    private List<Parameter> mapRequest(Event event) throws IOException {

        String encounterEventContent = webClient.get(URI.create(urlPrefix + event.getContent()));
        OpenMRSEncounter openMRSEncounter = ObjectMapperRepository.objectMapper.readValue(encounterEventContent, OpenMRSEncounter.class);

        String visitURL = "/openmrs/ws/rest/v1/visit/" + openMRSEncounter.getVisitUuid() + "?v=full";
        String visitContent = webClient.get(URI.create(urlPrefix + visitURL));

        OpenMRSVisit openMRSVisit = ObjectMapperRepository.objectMapper.readValue(visitContent, OpenMRSVisit.class);
        MapERPOrders mapERPOrders = new MapERPOrders(openMRSEncounter, openMRSVisit);
        openERPOrders = mapERPOrders.mapOpenERPOrders();
        return mapERPOrders.getParameters(event.getId(), event.getFeedUri(), feedUrl);
    }

    private Parameter createParameter(String name, String value, String type) {
        return new Parameter(name, value, type);
    }
}

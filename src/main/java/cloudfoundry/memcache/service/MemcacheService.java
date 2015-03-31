package cloudfoundry.memcache.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;

import cf.spring.servicebroker.Bind;
import cf.spring.servicebroker.BindRequest;
import cf.spring.servicebroker.BindResponse;
import cf.spring.servicebroker.Catalog;
import cf.spring.servicebroker.Catalog.CatalogService;
import cf.spring.servicebroker.Catalog.Plan;
import cf.spring.servicebroker.Deprovision;
import cf.spring.servicebroker.DeprovisionRequest;
import cf.spring.servicebroker.DynamicCatalog;
import cf.spring.servicebroker.Provision;
import cf.spring.servicebroker.ProvisionRequest;
import cf.spring.servicebroker.ProvisionResponse;
import cf.spring.servicebroker.ServiceBroker;
import cf.spring.servicebroker.Unbind;
import cf.spring.servicebroker.UnbindRequest;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * @author Mike Heath <elcapo@gmail.com>
 */
@ServiceBroker
public class MemcacheService {
	
	private static final String PLAN_CONFIG_NAME_KEY = "name";
	private static final String PLAN_CONFIG_DESCRIPTION_KEY = "description";
	private static final String PLAN_CONFIG_FREE_KEY = "free";
	
	@Value("#{environment['plans']}")
	private Map<String, Map<String, Object>> planConfigs;

	private static final Logger LOGGER = LoggerFactory.getLogger(MemcacheService.class);

	@DynamicCatalog
	public Catalog getCatalog() {
		List<Plan> plans = new ArrayList<>();
		for(Map.Entry<String, Map<String, Object>> planConfig : planConfigs.entrySet()) {
			Plan plan = new Plan();
			plans.add(plan);
			plan.setId(planConfig.getKey());
			for(Map.Entry<String, Object> planValues : planConfig.getValue().entrySet()) {
				if(PLAN_CONFIG_NAME_KEY.equals(planValues.getKey())) {
					plan.setName((String)planValues.getValue());
				} else if(PLAN_CONFIG_DESCRIPTION_KEY.equals(planValues.getKey())) {
					plan.setDescription((String)planValues.getValue());
				} else if(PLAN_CONFIG_FREE_KEY.equals(planValues.getKey())) {
					plan.setFree((Boolean)planValues.getValue());
				} 
			}
		}

		CatalogService service = new CatalogService(
				"memcache",
				"memcache",
				"Provides access to a memcache cluster.",
				true,
				null,
				null,
				null,
				plans,
				null);
		
		return new Catalog(Collections.singletonList(service));
	}
	
	@Provision
	public ProvisionResponse provision(ProvisionRequest request) {
		return new ProvisionResponse();
	}

	@Deprovision
	public void deprovision(DeprovisionRequest request) {
	}

	@Bind
	public BindResponse bind(BindRequest bindRequest) {
		ObjectNode credentials = JsonNodeFactory.instance.objectNode();
		return new BindResponse(credentials);
	}

	@Unbind
	public void unbind(UnbindRequest unbindRequest) {
	}
}

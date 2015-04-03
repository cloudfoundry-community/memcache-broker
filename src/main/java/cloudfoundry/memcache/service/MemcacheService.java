package cloudfoundry.memcache.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.codec.digest.DigestUtils;
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

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * @author Mike Heath <elcapo@gmail.com>
 */
@ServiceBroker
public class MemcacheService {
	
	private static final String MEMCACHE_SERVERS_KEY = "servers";
	private static final String MEMCACHE_PASSWORD_KEY = "password";
	private static final String MEMCACHE_USERNAME_KEY = "username";
	private static final String MEMCACHE_VIP_KEY = "vip";
	private static final String MEMCACHE_SECRET_KEY_KEY = "secret_key";
	private static final String PLAN_ID_PREFIX = "memcache_";
	private static final String PLAN_CONFIG_NAME_KEY = "name";
	private static final String PLAN_CONFIG_DESCRIPTION_KEY = "description";
	private static final String PLAN_CONFIG_FREE_KEY = "free";
	
	@Value("#{environment['plans']}")
	private Map<String, Map<String, Object>> planConfigs;

	@Value("#{environment['memcache']}")
	private Map<String, String> memcacheConfig;

	@Value("#{environment['memcache']['servers']}")
	private List<String> serversConfig;

	private static final Logger LOGGER = LoggerFactory.getLogger(MemcacheService.class);

	@DynamicCatalog
	public Catalog getCatalog() {
		List<Plan> plans = new ArrayList<>();
		for(Map.Entry<String, Map<String, Object>> planConfig : planConfigs.entrySet()) {
			Plan plan = new Plan();
			plans.add(plan);
			plan.setId(PLAN_ID_PREFIX+planConfig.getKey());
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
		if(memcacheConfig.containsKey(MEMCACHE_VIP_KEY)) {
			credentials.put(MEMCACHE_VIP_KEY, memcacheConfig.get(MEMCACHE_VIP_KEY));
		}
		credentials.put(MEMCACHE_USERNAME_KEY, generateUsername(bindRequest.getPlanId(), bindRequest.getServiceInstanceGuid(), bindRequest.getApplicationGuid()));
		credentials.put(MEMCACHE_PASSWORD_KEY, generatePassword(bindRequest.getPlanId(), bindRequest.getServiceInstanceGuid(), bindRequest.getApplicationGuid()));
		ArrayNode servers = credentials.arrayNode();
		for(String server : serversConfig) {
			servers.add(server);
		}
		credentials.put(MEMCACHE_SERVERS_KEY, servers);
		return new BindResponse(credentials);
	}

	@Unbind
	public void unbind(UnbindRequest unbindRequest) {
	}
	
	private String generateUsername(String planId, UUID serviceInstanceGuid, UUID appGuid) {
		return generateCacheName(planId, serviceInstanceGuid)+'|'+(appGuid.toString());
	}

	private String generateCacheName(String planId, UUID serviceInstanceGuid) {
		return planId.replace(PLAN_ID_PREFIX, "")+'|'+(serviceInstanceGuid.toString());
	}

	private String generatePassword(String planId, UUID serviceInstanceGuid, UUID appGuid) {
		return Base64.encodeBase64String(DigestUtils.sha384((generateUsername(planId, serviceInstanceGuid, appGuid) + memcacheConfig.get(MEMCACHE_SECRET_KEY_KEY))));
	}

}

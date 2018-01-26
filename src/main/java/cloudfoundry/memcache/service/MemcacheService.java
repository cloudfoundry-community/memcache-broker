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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

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
	private static final Logger LOGGER = LoggerFactory.getLogger(MemcacheService.class);

	private static final String MEMCACHE_SERVERS_KEY = "servers";
	private static final String MEMCACHE_PASSWORD_KEY = "password";
	private static final String MEMCACHE_USERNAME_KEY = "username";
	private static final String MEMCACHE_VIP_KEY = "vip";
	private static final String MEMCACHE_SECRET_KEY_KEY = "secret_key";
	
	@Autowired
	Main.Config config;

	@DynamicCatalog
	public Catalog getCatalog() {
		List<Plan> plans = new ArrayList<>();
		for(Map.Entry<String, Main.Config.Plan> planConfig : config.getPlans().entrySet()) {
			Plan plan = new Plan();
			plans.add(plan);
			plan.setId(planConfig.getKey());
			plan.setName(planConfig.getValue().getName());
			plan.setDescription(planConfig.getValue().getDescription());
			plan.setFree(planConfig.getValue().getFree());
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
		String cacheName = generateCacheName(request.getPlanId(), request.getInstanceGuid());
		RestTemplate template = new RestTemplate();
		ResponseEntity<String> result = template.exchange(config.getMemcache().getSrvUrl()+"/cache/{cacheName}", HttpMethod.DELETE, new HttpEntity<String>(createBasicAuthHeaders()), String.class, (Map<String, ?>)Collections.singletonMap("cacheName", cacheName));
		if(result.getStatusCode() != HttpStatus.OK) {
			throw new RuntimeException("memcache server failed to handle delete request.");
		}
	}

	private HttpHeaders createBasicAuthHeaders() {
		String plainCreds = config.getMemcache().getUsername()+":"+config.getMemcache().getPassword();
		byte[] plainCredsBytes = plainCreds.getBytes();
		byte[] base64CredsBytes = Base64.encodeBase64(plainCredsBytes);
		String base64Creds = new String(base64CredsBytes);

		HttpHeaders headers = new HttpHeaders();
		headers.add("Authorization", "Basic " + base64Creds);
		return headers;
	}

	@Bind
	public BindResponse bind(BindRequest bindRequest) {
		ObjectNode credentials = JsonNodeFactory.instance.objectNode();
		if(config.getMemcache().getVip() != null) {
			credentials.put(MEMCACHE_VIP_KEY, config.getMemcache().getVip());
		}
		credentials.put(MEMCACHE_USERNAME_KEY, generateUsername(bindRequest.getPlanId(), bindRequest.getServiceInstanceGuid(), bindRequest.getBindingGuid()));
		credentials.put(MEMCACHE_PASSWORD_KEY, generatePassword(bindRequest.getPlanId(), bindRequest.getServiceInstanceGuid(), bindRequest.getBindingGuid()));
		ArrayNode servers = credentials.arrayNode();
		for(String server : config.getMemcache().getServers()) {
			servers.add(server);
		}
		credentials.set(MEMCACHE_SERVERS_KEY, servers);
		return new BindResponse(credentials);
	}

	@Unbind
	public void unbind(UnbindRequest unbindRequest) {
	}
	
	private String generateUsername(String planId, UUID serviceInstanceGuid, UUID bindGuid) {
		return generateCacheName(planId, serviceInstanceGuid)+'|'+(bindGuid.toString());
	}

	private String generateCacheName(String planId, UUID serviceInstanceGuid) {
		return planId+'|'+(serviceInstanceGuid.toString());
	}

	private String generatePassword(String planId, UUID serviceInstanceGuid, UUID bindGuid) {
		return Base64.encodeBase64String(DigestUtils.sha384((generateUsername(planId, serviceInstanceGuid, bindGuid) + config.getMemcache().getSecretKey())));
	}

}

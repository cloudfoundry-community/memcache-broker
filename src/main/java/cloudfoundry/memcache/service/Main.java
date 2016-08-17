package cloudfoundry.memcache.service;

import java.util.List;
import java.util.Map;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;

import org.hibernate.validator.constraints.NotEmpty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import cf.spring.servicebroker.EnableServiceBroker;

/**
 * @author Mike Heath <elcapo@gmail.com>
 */
@SpringBootApplication
@EnableServiceBroker(password = "#{environment['brokerPassword']}")
public class Main {
	private static final Logger LOGGER = LoggerFactory.getLogger(Main.class);

	public static void main(String[] args) {
		SpringApplication.run(Main.class, args);
		LOGGER.info("Memcache service broker started");
	}

	@Component
	@ConfigurationProperties
	public static class Config {
		@Valid Memcache memcache;
		@Valid Map<String, Plan> plans;

		public Memcache getMemcache() {
			return memcache;
		}
		public void setMemcache(Memcache memcache) {
			this.memcache = memcache;
		}
		public Map<String, Plan> getPlans() {
			return plans;
		}
		public void setPlans(Map<String, Plan> plans) {
			this.plans = plans;
		}

		public static class Memcache {
			@NotEmpty String srvUrl;
			@NotEmpty String username;
			@NotEmpty String password;
			String vip;
			@NotEmpty String secretKey;
			@NotEmpty List<String> servers;

			public String getSrvUrl() {
				return srvUrl;
			}
			public void setSrvUrl(String srvUrl) {
				this.srvUrl = srvUrl;
			}
			public String getUsername() {
				return username;
			}
			public void setUsername(String username) {
				this.username = username;
			}
			public String getPassword() {
				return password;
			}
			public void setPassword(String password) {
				this.password = password;
			}
			public String getVip() {
				return vip;
			}
			public void setVip(String vip) {
				this.vip = vip;
			}
			public String getSecretKey() {
				return secretKey;
			}
			public void setSecretKey(String secretKey) {
				this.secretKey = secretKey;
			}
			public List<String> getServers() {
				return servers;
			}
			public void setServers(List<String> servers) {
				this.servers = servers;
			}
		}

		public static class Plan {
			@NotEmpty String name;
			@NotEmpty String description;
			@NotNull Boolean free;

			public String getName() {
				return name;
			}
			public void setName(String name) {
				this.name = name;
			}
			public String getDescription() {
				return description;
			}
			public void setDescription(String description) {
				this.description = description;
			}
			public Boolean getFree() {
				return free;
			}
			public void setFree(Boolean free) {
				this.free = free;
			}
		}
	}
}

package cloudfoundry.memcache.service;

import java.io.File;
import java.io.IOException;
import java.util.List;

import loggregator.Emitter;
import loggregator.EmitterBuilder;
import nats.client.Nats;
import nats.client.spring.NatsBuilder;

import org.apache.http.client.HttpClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration;
import org.springframework.boot.context.embedded.EmbeddedServletContainerFactory;
import org.springframework.boot.context.embedded.tomcat.TomcatEmbeddedServletContainerFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import cf.nats.CfNats;
import cf.nats.DefaultCfNats;
import cf.nats.RouterRegisterHandler;
import cf.spring.CfComponent;
import cf.spring.HttpClientFactoryBean;
import cf.spring.NettyEventLoopGroupFactoryBean;
import cf.spring.PidFileFactory;
import cf.spring.config.YamlPropertyContextInitializer;
import cf.spring.servicebroker.EnableServiceBroker;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;

/**
 * @author Mike Heath <elcapo@gmail.com>
 */
@Configuration
@EnableAutoConfiguration(exclude= {DataSourceAutoConfiguration.class, DataSourceTransactionManagerAutoConfiguration.class})
@EnableServiceBroker(password = "#{environment['brokerPassword']}")
@ComponentScan("org.lds.cloudfoundry.service.autoscale")
@CfComponent(type = "AutoScaleServiceBroker", host = "#{environment['host.local']}", port = "#{environment['host.port']}")
public class Main {

	public static final String TOKEN_PROVIDER_KEY = "TOKEN_PROVIDER";
	public static final String EMITTER_KEY = "EMITTER";
	public static final String CLOUD_CONTROLLER_KEY = "CLOUD_CONTROLLER";

	private static final Logger LOGGER = LoggerFactory.getLogger(Main.class);
	
	@Bean
	TaskExecutor executor() {
		ThreadPoolTaskExecutor taskExecutor = new ThreadPoolTaskExecutor();
		taskExecutor.setCorePoolSize(10);
		return taskExecutor;
	}

	@Bean
	Nats nats(
			ApplicationEventPublisher publisher,
			@Value("#{config.nats.machines}") List<String> natsMachines) {
		final NatsBuilder builder = new NatsBuilder(publisher);
		builder.eventLoopGroup(workerGroup().getObject());
		natsMachines.forEach(builder::addHost);
		return builder.connect();
	}

	@Bean
	CfNats cfNats(Nats nats) {
		return new DefaultCfNats(nats);
	}

	@Bean
	@Qualifier("worker")
	NettyEventLoopGroupFactoryBean workerGroup() {
		return new NettyEventLoopGroupFactoryBean();
	}

	@Bean
	public EmbeddedServletContainerFactory servletContainer(
			@Value("${host.port}") int port,
			@Value("#{config['tomcat']?.base_directory}") String baseDirectory
	) {
		System.setProperty("java.security.egd", "file:/dev/./urandom");
		final TomcatEmbeddedServletContainerFactory servletContainerFactory = new TomcatEmbeddedServletContainerFactory(port);
		if (baseDirectory != null) {
			servletContainerFactory.setBaseDirectory(new File(baseDirectory));
		}
		return servletContainerFactory;
	}

	@Bean
	public PidFileFactory pidFile(Environment environment) throws IOException {
		return new PidFileFactory(environment.getProperty("pidfile"));
	}

	@Bean
	RouterRegisterHandler routerRegisterHandler(CfNats cfNats, Environment environment) {
		return new RouterRegisterHandler(
				cfNats,
				environment.getProperty("host.local", "127.0.0.1"),
				Integer.valueOf(environment.getProperty("host.port", "8080")),
				environment.getProperty("host.public", "service-broker")
		);
	}

	@Bean
	FactoryBean<HttpClient> httpClient() {
		return new HttpClientFactoryBean();
	}

	@Bean
	Emitter emitter(
			@Value("${loggregator_endpoint.host}") String host,
			@Value("${loggregator_endpoint.port:3456}") int port,
			@Value("${loggregator_endpoint.shared_secret}") String secret) {
		return new EmitterBuilder(host, port, secret).sourceName("SCALE").build();
	}

	public static void main(String[] args) {
		final SpringApplication springApplication = new SpringApplication(Main.class);
		springApplication.addInitializers(new YamlPropertyContextInitializer(
				"config",
				"config",
				"service-broker.yml"));
		final ApplicationContext applicationContext = springApplication.run(args);

		final LoggerContext loggerContext = (LoggerContext) LoggerFactory.getILoggerFactory();
		final Level level = Level.toLevel(applicationContext.getEnvironment().getProperty("logging.level"), Level.INFO);
		loggerContext.getLogger("ROOT").setLevel(level);

		LOGGER.info("Auto Scale service broker started");
	}

}

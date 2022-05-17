package org.entando.pbc.spid;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.boot.web.servlet.server.ConfigurableServletWebServerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

@ComponentScan(basePackages = "org.entando.pbc.spid.config")
@Configuration
@SpringBootApplication
public class SpidApplication {

	@Bean
	public WebServerFactoryCustomizer<ConfigurableServletWebServerFactory>
	webServerFactoryCustomizer() {
		// http://localhost:8080/spid/api/actuator/health
		return factory -> factory.setContextPath("/spid");
	}

	public static void main(String[] args) {
		SpringApplication.run(SpidApplication.class, args);
		// start!
		Installer.install();
	}

}

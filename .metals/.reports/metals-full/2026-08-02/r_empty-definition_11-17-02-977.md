error id: file://<WORKSPACE>/src/main/java/com/microservices/gateway/ApiGatewayApplication.java:_empty_/EnableEurekaServer#
file://<WORKSPACE>/src/main/java/com/microservices/gateway/ApiGatewayApplication.java
empty definition using pc, found symbol in pc: _empty_/EnableEurekaServer#
empty definition using semanticdb
empty definition using fallback
non-local guesses:

offset: 504
uri: file://<WORKSPACE>/src/main/java/com/microservices/gateway/ApiGatewayApplication.java
text:
```scala
package com.microservices.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.cloud.netflix.eureka.server.EnableDiscoveryClient;

@SpringBootApplication
@EnableDiscoveryClient
@EnableEurekaServ@@er
@RestController
public class ApiGatewayApplication {
    public static void main(String[] args) {
        SpringApplication.run(ApiGatewayApplication.class, args);
    }

    @GetMapping("/health")
    public String health() {
        return "OK";
    }
}

```


#### Short summary: 

empty definition using pc, found symbol in pc: _empty_/EnableEurekaServer#
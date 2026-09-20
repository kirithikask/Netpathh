package com.netpath;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration;

/**
 * NETPATH keeps Redis for the path-health hot state only, so the Redis repository support is
 * switched off: it adds a key/value adapter that this application never uses.
 */
@SpringBootApplication(exclude = RedisRepositoriesAutoConfiguration.class)
public class NetpathApplication {

    public static void main(String[] args) {
        SpringApplication.run(NetpathApplication.class, args);
    }
}
